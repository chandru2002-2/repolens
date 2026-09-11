package io.repolens.parse;

import io.repolens.core.model.Import;
import io.repolens.core.model.Module;
import io.repolens.core.model.Relationship;
import io.repolens.core.model.RelationshipType;
import io.repolens.core.model.Repository;
import io.repolens.core.model.RepositoryModel;
import io.repolens.core.model.SourceFile;
import io.repolens.core.model.SourceLocation;
import io.repolens.core.model.Symbol;
import io.repolens.core.model.SymbolKind;
import io.repolens.core.model.WorkingTreeInventory;
import io.repolens.core.ports.SourceAnalyzer;
import io.repolens.parse.engine.SeartTreeSitterEngine;
import io.repolens.parse.engine.SyntaxCapture;
import io.repolens.parse.engine.SyntaxQueryEngine;
import io.repolens.parse.profile.LanguageProfile;
import io.repolens.parse.profile.LanguageProfiles;
import io.repolens.parse.structural.StructuralFactExtractor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Builds a RepositoryModel using language profiles + Tree-sitter when available.
 */
public final class ProfiledSourceAnalyzer implements SourceAnalyzer {

    private static final Logger LOGGER = Logger.getLogger(ProfiledSourceAnalyzer.class.getName());

    private final SyntaxQueryEngine engine;
    private final List<LanguageProfile> profiles;

    public ProfiledSourceAnalyzer() {
        this(new SeartTreeSitterEngine(), LanguageProfiles.defaults());
    }

    public ProfiledSourceAnalyzer(SyntaxQueryEngine engine, List<LanguageProfile> profiles) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.profiles = List.copyOf(Objects.requireNonNull(profiles, "profiles"));
    }

    public String engineId() {
        return engine.isAvailable() ? engine.id() : "structural-fallback";
    }

    public boolean usingTreeSitter() {
        return engine.isAvailable();
    }

    @Override
    public RepositoryModel analyze(
            Repository repository,
            Path workingTree,
            WorkingTreeInventory inventory
    ) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(workingTree, "workingTree");
        Objects.requireNonNull(inventory, "inventory");

        RepositoryModel.Builder builder = RepositoryModel.builder(repository);
        Set<String> moduleIds = new HashSet<>();
        List<Symbol> symbols = new ArrayList<>();
        List<Module> modules = new ArrayList<>();
        Map<String, String> typeIdsBySimpleName = new HashMap<>();
        List<PendingTypeEdge> pendingEdges = new ArrayList<>();
        int symbolSeq = 0;
        int importSeq = 0;
        int relationshipSeq = 0;

        for (WorkingTreeInventory.InventoriedFile file : inventory.files()) {
            LanguageProfile profile = findProfile(file.relativePath());
            String language = profile == null ? languageFromExtension(file.relativePath()) : profile.id();
            Path absolute = workingTree.resolve(file.relativePath());
            builder.addFile(new SourceFile(file.relativePath(), language, hashFile(absolute), file.sizeBytes()));

            if (profile == null) {
                continue;
            }

            String source = readSource(absolute);
            List<SyntaxCapture> captures = extract(profile, source);

            Optional<String> moduleName = profile.moduleFromCaptures(captures, file.relativePath());
            Optional<String> moduleId = Optional.empty();
            if (moduleName.isPresent()) {
                String id = "module:" + moduleName.get();
                if (moduleIds.add(id)) {
                    Module module = Module.of(id, moduleName.get(), profile.id());
                    builder.addModule(module);
                    modules.add(module);
                }
                moduleId = Optional.of(id);
            }

            Optional<String> enclosingTypeId = Optional.empty();
            for (SyntaxCapture capture : captures) {
                switch (capture.name()) {
                    case "module" -> {
                        // handled above
                    }
                    case "import" -> {
                        String importId = "import:" + (++importSeq);
                        builder.addImport(new Import(
                                importId,
                                file.relativePath(),
                                capture.text().replaceAll("\\s+", " ").trim(),
                                Optional.empty(),
                                location(file.relativePath(), capture)
                        ));
                        if (moduleId.isPresent()) {
                            builder.addRelationship(Relationship.of(
                                    "rel:" + (++relationshipSeq),
                                    RelationshipType.IMPORTS,
                                    moduleId.get(),
                                    importId
                            ));
                        }
                    }
                    case "class", "interface", "enum", "type", "function", "method", "field" -> {
                        SymbolKind kind = toKind(capture.name());
                        String symbolId = "sym:" + (++symbolSeq);
                        Optional<String> parent = kind == SymbolKind.METHOD || kind == SymbolKind.FIELD
                                ? enclosingTypeId
                                : Optional.empty();
                        Symbol symbol = new Symbol(
                                symbolId,
                                capture.text().trim(),
                                kind,
                                parent,
                                moduleId,
                                location(file.relativePath(), capture),
                                Optional.empty()
                        );
                        builder.addSymbol(symbol);
                        symbols.add(symbol);
                        if (kind == SymbolKind.CLASS || kind == SymbolKind.INTERFACE
                                || kind == SymbolKind.ENUM || kind == SymbolKind.TYPE) {
                            enclosingTypeId = Optional.of(symbolId);
                            typeIdsBySimpleName.putIfAbsent(symbol.name(), symbolId);
                        }
                        if (moduleId.isPresent() && (kind == SymbolKind.CLASS || kind == SymbolKind.INTERFACE
                                || kind == SymbolKind.ENUM || kind == SymbolKind.TYPE
                                || kind == SymbolKind.FUNCTION)) {
                            builder.addRelationship(Relationship.of(
                                    "rel:" + (++relationshipSeq),
                                    RelationshipType.CONTAINS,
                                    moduleId.get(),
                                    symbolId
                            ));
                        }
                        if (parent.isPresent() && (kind == SymbolKind.METHOD || kind == SymbolKind.FIELD)) {
                            builder.addRelationship(Relationship.of(
                                    "rel:" + (++relationshipSeq),
                                    RelationshipType.CONTAINS,
                                    parent.get(),
                                    symbolId
                            ));
                        }
                    }
                    case "extends" -> {
                        if (enclosingTypeId.isPresent()) {
                            pendingEdges.add(new PendingTypeEdge(
                                    enclosingTypeId.get(),
                                    RelationshipType.EXTENDS,
                                    simpleName(capture.text())
                            ));
                        }
                    }
                    case "implements" -> {
                        if (enclosingTypeId.isPresent()) {
                            pendingEdges.add(new PendingTypeEdge(
                                    enclosingTypeId.get(),
                                    RelationshipType.IMPLEMENTS,
                                    simpleName(capture.text())
                            ));
                        }
                    }
                    default -> {
                    }
                }
            }
        }

        for (PendingTypeEdge edge : pendingEdges) {
            String targetId = typeIdsBySimpleName.get(edge.targetSimpleName());
            if (targetId == null || targetId.equals(edge.fromSymbolId())) {
                continue;
            }
            builder.addRelationship(Relationship.of(
                    "rel:" + (++relationshipSeq),
                    edge.type(),
                    edge.fromSymbolId(),
                    targetId
            ));
        }

        StructuralFactExtractor.extract(builder, workingTree, inventory, symbols);
        DocumentationIndexer.index(builder, workingTree, inventory, symbols, modules);
        return builder.build();
    }

    private LanguageProfile findProfile(String relativePath) {
        for (LanguageProfile profile : profiles) {
            if (profile.supports(relativePath)) {
                return profile;
            }
        }
        return null;
    }

    private List<SyntaxCapture> extract(LanguageProfile profile, String source) {
        if (engine.isAvailable()) {
            try {
                return engine.query(profile.treeSitterLanguage(), source, profile.query());
            } catch (RuntimeException ex) {
                LOGGER.log(Level.WARNING,
                        "Tree-sitter extract failed for profile {0}; using fallback: {1}",
                        new Object[]{profile.id(), ex.toString()});
            }
        }
        return profile.fallbackExtract(source);
    }

    private static SymbolKind toKind(String captureName) {
        return switch (captureName) {
            case "class" -> SymbolKind.CLASS;
            case "interface" -> SymbolKind.INTERFACE;
            case "enum" -> SymbolKind.ENUM;
            case "type" -> SymbolKind.TYPE;
            case "function" -> SymbolKind.FUNCTION;
            case "method" -> SymbolKind.METHOD;
            case "field" -> SymbolKind.FIELD;
            default -> SymbolKind.OTHER;
        };
    }

    private static String simpleName(String typeName) {
        String value = typeName == null ? "" : typeName.trim();
        int dot = value.lastIndexOf('.');
        return dot >= 0 ? value.substring(dot + 1) : value;
    }

    private static SourceLocation location(String path, SyntaxCapture capture) {
        return new SourceLocation(
                path,
                capture.startLine(),
                capture.startColumn(),
                capture.endLine(),
                capture.endColumn()
        );
    }

    private static String readSource(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read source file: " + path, ex);
        }
    }

    private static String hashFile(Path path) {
        try {
            byte[] bytes = Files.readAllBytes(path);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return HexFormat.of().formatHex(digest);
        } catch (IOException | NoSuchAlgorithmException ex) {
            return "unknown";
        }
    }

    private static String languageFromExtension(String relativePath) {
        String lower = relativePath.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        if (dot < 0) {
            return "unknown";
        }
        return lower.substring(dot + 1);
    }

    private record PendingTypeEdge(String fromSymbolId, RelationshipType type, String targetSimpleName) {
    }
}

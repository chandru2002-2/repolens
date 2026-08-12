package io.repolens.analyzers;

import io.repolens.core.model.AnalysisResult;
import io.repolens.core.model.Import;
import io.repolens.core.model.Metric;
import io.repolens.core.model.Relationship;
import io.repolens.core.model.RelationshipType;
import io.repolens.core.model.RepositoryModel;
import io.repolens.core.model.Symbol;
import io.repolens.core.model.SymbolKind;
import io.repolens.core.ports.Analyzer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Derives type-level DEPENDS_ON edges from resolved imports (no invented links).
 */
public final class TypeDependencyAnalyzer implements Analyzer {

    @Override
    public String id() {
        return "type-dependencies";
    }

    @Override
    public AnalysisResult analyze(RepositoryModel model) {
        Map<String, String> typeIndex = buildTypeIndex(model);
        Map<String, String> primaryTypeByFile = primaryTypeByFile(model);
        Set<String> seen = new HashSet<>();
        List<Relationship> relationships = new ArrayList<>();
        int seq = 0;

        for (Import importDecl : model.imports()) {
            String ownerType = primaryTypeByFile.get(importDecl.sourceFilePath());
            if (ownerType == null) {
                continue;
            }
            String targetType = resolveType(typeIndex, importDecl.rawImport());
            if (targetType == null || targetType.equals(ownerType)) {
                continue;
            }
            String key = ownerType + "->" + targetType;
            if (!seen.add(key)) {
                continue;
            }
            relationships.add(new Relationship(
                    "type-dep-" + (++seq),
                    RelationshipType.DEPENDS_ON,
                    ownerType,
                    targetType,
                    0.65,
                    Optional.of("import-type-resolution")
            ));
        }

        List<Metric> metrics = List.of(
                new Metric(
                        "type_dependency_edge_count",
                        relationships.size(),
                        Optional.empty(),
                        Optional.of(model.repository().id())
                )
        );

        String summary = "Type dependencies: " + relationships.size() + " type DEPENDS_ON edges";
        return new AnalysisResult(id(), summary, metrics, relationships, List.of());
    }

    private static Map<String, String> buildTypeIndex(RepositoryModel model) {
        Map<String, String> index = new HashMap<>();
        for (Symbol symbol : model.symbols()) {
            if (!isType(symbol.kind())) {
                continue;
            }
            index.putIfAbsent(symbol.name().toLowerCase(Locale.ROOT), symbol.id());
            symbol.moduleId().flatMap(model::findModule).ifPresent(module -> {
                String fqn = module.name() + "." + symbol.name();
                index.putIfAbsent(fqn.toLowerCase(Locale.ROOT), symbol.id());
            });
        }
        return index;
    }

    private static Map<String, String> primaryTypeByFile(RepositoryModel model) {
        Map<String, String> byFile = new HashMap<>();
        for (Symbol symbol : model.symbols()) {
            if (!isType(symbol.kind())) {
                continue;
            }
            byFile.putIfAbsent(symbol.location().filePath(), symbol.id());
        }
        return byFile;
    }

    private static String resolveType(Map<String, String> typeIndex, String rawImport) {
        String spec = normalizeImport(rawImport);
        if (spec.isBlank() || spec.endsWith("*")) {
            return null;
        }
        String direct = typeIndex.get(spec.toLowerCase(Locale.ROOT));
        if (direct != null) {
            return direct;
        }
        int dot = spec.lastIndexOf('.');
        if (dot > 0) {
            return typeIndex.get(spec.substring(dot + 1).toLowerCase(Locale.ROOT));
        }
        return null;
    }

    private static String normalizeImport(String rawImport) {
        String value = rawImport == null ? "" : rawImport.trim();
        value = value.replaceFirst("^import\\s+(?:static\\s+)?", "");
        value = value.replace(";", "").trim();
        return value;
    }

    private static boolean isType(SymbolKind kind) {
        return kind == SymbolKind.CLASS
                || kind == SymbolKind.INTERFACE
                || kind == SymbolKind.ENUM
                || kind == SymbolKind.TYPE;
    }
}

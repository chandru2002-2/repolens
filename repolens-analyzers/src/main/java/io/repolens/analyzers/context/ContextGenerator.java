package io.repolens.analyzers.context;

import io.repolens.core.model.AnalysisResult;
import io.repolens.core.model.DocumentationDocument;
import io.repolens.core.model.DocumentationSection;
import io.repolens.core.model.Module;
import io.repolens.core.model.Relationship;
import io.repolens.core.model.RelationshipType;
import io.repolens.core.model.RepositoryModel;
import io.repolens.core.model.RepositoryOrigin;
import io.repolens.core.model.SourceFile;
import io.repolens.core.model.StructuralFact;
import io.repolens.core.model.Symbol;
import io.repolens.core.model.SymbolKind;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Packages existing RepositoryModel intelligence into an AI-ready context.
 * Does not parse source, invent relationships, or run analyzers.
 */
public final class ContextGenerator {

    private static final Pattern SECURITY_NAME = Pattern.compile(
            "(?i).*(security|auth|oauth|jwt|permission|role|csrf|cors|passwd|password|credential).*");

    private final TokenEstimator estimator;
    private final MarkdownContextFormatter markdownFormatter = new MarkdownContextFormatter();
    private final JsonContextFormatter jsonFormatter = new JsonContextFormatter();

    public ContextGenerator() {
        this(TokenEstimator.ApproxCharsPerToken.INSTANCE);
    }

    public ContextGenerator(TokenEstimator estimator) {
        this.estimator = Objects.requireNonNull(estimator, "estimator");
    }

    public ContextResult generate(
            RepositoryModel model,
            List<AnalysisResult> results,
            Path workingTree,
            ContextRequest request
    ) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(results, "results");
        Objects.requireNonNull(workingTree, "workingTree");
        Objects.requireNonNull(request, "request");

        validateScope(request);

        ContextStrategy strategy = request.strategy();
        Set<String> seedSymbolIds = resolveSeedSymbols(model, request);
        Set<String> relatedSymbolIds = expandRelated(model, seedSymbolIds, request.purpose());
        Set<String> filePaths = resolveFiles(model, request, seedSymbolIds, relatedSymbolIds, strategy);

        ContextDraft.Builder draft = ContextDraft.builder(
                request.title(), request.purpose(), request.scope(), strategy, request.budget().tokens());

        addRepositoryOverview(draft, model);
        draft.include("repository overview");
        draft.include("strategy: " + strategy.displayName());

        if (strategy.includesModules()) {
            addModules(draft, model, request, strategy);
        } else {
            draft.exclude("modules omitted by " + strategy.displayName() + " strategy");
        }
        if (strategy.includesComponents()) {
            addComponents(draft, model, seedSymbolIds, relatedSymbolIds, request.purpose(), strategy);
        }
        if (strategy.includesRelationships()) {
            addRelationships(draft, model, seedSymbolIds, relatedSymbolIds, request.purpose(), strategy);
        } else {
            draft.exclude("relationships omitted by " + strategy.displayName() + " strategy");
        }
        if (strategy.includesEndpoints()) {
            addEndpoints(draft, model, request.purpose(), strategy);
        }
        if (strategy.includesDataModels()) {
            addDataModels(draft, model, request.purpose(), strategy);
        }
        if (strategy.includesConfiguration()) {
            addConfiguration(draft, model, request.purpose());
        }
        if (strategy.includesDocumentation()) {
            addDocumentation(draft, model, seedSymbolIds, relatedSymbolIds);
        } else {
            draft.exclude("documentation omitted by " + strategy.displayName() + " strategy");
        }
        addAnalysisNotes(draft, results);
        if (strategy.maxSourceFiles() > 0) {
            addSourceSnippets(draft, model, workingTree, filePaths, request, strategy);
        } else {
            draft.exclude("source files omitted by " + strategy.displayName() + " strategy");
        }

        ContextDraft built = draft.build();
        ContextFormatter formatter = request.format() == ContextFormat.JSON ? jsonFormatter : markdownFormatter;
        FitResult fitted = fitToBudget(built, formatter, request.budget().tokens());

        List<String> excluded = new ArrayList<>(built.excluded());
        excluded.addAll(fitted.extraExcluded());

        int estimated = estimator.estimate(fitted.content());
        return new ContextResult(
                UUID.randomUUID().toString(),
                request.title(),
                request.purpose(),
                request.scope(),
                request.format(),
                request.budget().tokens(),
                estimated,
                fitted.content(),
                built.included(),
                excluded,
                true,
                strategy
        );
    }

    private void validateScope(ContextRequest request) {
        ContextPurpose purpose = request.purpose();
        ContextScope scope = request.scope();
        if (purpose == ContextPurpose.SELECTED_FILES && scope.filePaths().isEmpty()) {
            throw new IllegalArgumentException("SELECTED_FILES purpose requires scope.filePaths");
        }
        if (purpose == ContextPurpose.SELECTED_SYMBOLS
                && scope.symbolIds().isEmpty()
                && scope.graphNodeIds().isEmpty()) {
            throw new IllegalArgumentException("SELECTED_SYMBOLS purpose requires symbolIds or graphNodeIds");
        }
    }

    private Set<String> resolveSeedSymbols(RepositoryModel model, ContextRequest request) {
        Set<String> seeds = new LinkedHashSet<>();
        ContextScope scope = request.scope();
        seeds.addAll(scope.symbolIds());
        for (String nodeId : scope.graphNodeIds()) {
            if (nodeId.startsWith("node:")) {
                String entity = nodeId.substring("node:".length());
                if (model.findSymbol(entity).isPresent()) {
                    seeds.add(entity);
                }
            } else if (model.findSymbol(nodeId).isPresent()) {
                seeds.add(nodeId);
            }
        }
        for (String path : scope.filePaths()) {
            model.symbols().stream()
                    .filter(s -> s.location().filePath().equals(path))
                    .filter(s -> isType(s.kind()))
                    .map(Symbol::id)
                    .forEach(seeds::add);
        }

        if (seeds.isEmpty()) {
            switch (request.purpose()) {
                case API_BACKEND -> model.structuralFacts("sequence").stream()
                        .filter(f -> Set.of("controller", "service", "repository").contains(f.kind()))
                        .map(f -> f.sourceEntityId().orElse(null))
                        .filter(Objects::nonNull)
                        .forEach(seeds::add);
                case DATABASE_JPA -> model.structuralFacts("er").stream()
                        .filter(f -> "entity".equals(f.kind()))
                        .map(f -> f.sourceEntityId().orElse(null))
                        .filter(Objects::nonNull)
                        .forEach(seeds::add);
                case SECURITY -> model.symbols().stream()
                        .filter(s -> isType(s.kind()) && SECURITY_NAME.matcher(s.name()).matches())
                        .map(Symbol::id)
                        .forEach(seeds::add);
                case ARCHITECTURE, FULL_REPOSITORY, CUSTOM -> model.symbols().stream()
                        .filter(s -> isType(s.kind()))
                        .sorted(Comparator.comparing(Symbol::name))
                        .limit(40)
                        .map(Symbol::id)
                        .forEach(seeds::add);
                case SELECTED_FILES, SELECTED_SYMBOLS -> {
                    // seeds already empty — generate smaller overview-only context
                }
            }
        }
        return seeds;
    }

    private Set<String> expandRelated(RepositoryModel model, Set<String> seeds, ContextPurpose purpose) {
        Set<String> related = new LinkedHashSet<>(seeds);
        if (seeds.isEmpty()) {
            return related;
        }
        for (Relationship rel : model.relationships()) {
            if (rel.type() == RelationshipType.CALLS
                    || rel.type() == RelationshipType.DEPENDS_ON
                    || rel.type() == RelationshipType.EXTENDS
                    || rel.type() == RelationshipType.IMPLEMENTS) {
                if (seeds.contains(rel.fromId()) && model.findSymbol(rel.toId()).isPresent()) {
                    related.add(rel.toId());
                }
                if (seeds.contains(rel.toId()) && model.findSymbol(rel.fromId()).isPresent()) {
                    related.add(rel.fromId());
                }
            }
        }
        if (purpose == ContextPurpose.API_BACKEND || purpose == ContextPurpose.FULL_REPOSITORY) {
            for (StructuralFact fact : model.structuralFacts("sequence")) {
                if ("call".equals(fact.kind())
                        && fact.sourceEntityId().isPresent()
                        && fact.targetEntityId().isPresent()) {
                    if (seeds.contains(fact.sourceEntityId().get()) || seeds.contains(fact.targetEntityId().get())) {
                        related.add(fact.sourceEntityId().get());
                        related.add(fact.targetEntityId().get());
                    }
                }
            }
        }
        return related;
    }

    private Set<String> resolveFiles(
            RepositoryModel model,
            ContextRequest request,
            Set<String> seeds,
            Set<String> related,
            ContextStrategy strategy
    ) {
        Set<String> files = new LinkedHashSet<>();
        files.addAll(request.scope().filePaths());
        if (strategy.maxSourceFiles() <= 0) {
            return files;
        }
        for (String id : related) {
            model.findSymbol(id).ifPresent(s -> files.add(s.location().filePath()));
        }
        if (files.isEmpty() && (request.purpose() == ContextPurpose.FULL_REPOSITORY
                || request.purpose() == ContextPurpose.ARCHITECTURE
                || strategy == ContextStrategy.SOURCE_AND_SYMBOLS
                || strategy == ContextStrategy.ALTERNATE_PRIORITIZATION)) {
            List<String> all = model.files().stream()
                    .sorted(Comparator.comparing(SourceFile::path))
                    .map(SourceFile::path)
                    .toList();
            if (strategy == ContextStrategy.ALTERNATE_PRIORITIZATION) {
                all = all.reversed();
            }
            all.stream().limit(strategy.maxSourceFiles()).forEach(files::add);
        }
        List<String> ordered = new ArrayList<>(files);
        if (strategy == ContextStrategy.ALTERNATE_PRIORITIZATION) {
            Collections.reverse(ordered);
        }
        int cap = Math.max(strategy.maxSourceFiles(), request.scope().filePaths().size());
        if (cap <= 0) {
            return Set.of();
        }
        return new LinkedHashSet<>(ordered.stream().limit(cap).toList());
    }

    private void addRepositoryOverview(ContextDraft.Builder draft, RepositoryModel model) {
        draft.repositoryField("name", model.repository().name());
        draft.repositoryField("origin", model.repository().origin().name());
        if (model.repository().origin() == RepositoryOrigin.REMOTE) {
            model.repository().remoteUrl().ifPresent(url -> draft.repositoryField("remoteUrl", url));
        } else {
            model.repository().localPath().ifPresent(path -> draft.repositoryField("localPath", path));
        }
        draft.repositoryField("files", String.valueOf(model.fileCount()));
        draft.repositoryField("modules", String.valueOf(model.modules().size()));
        draft.repositoryField("symbols", String.valueOf(model.symbolCount()));
        draft.repositoryField("relationships", String.valueOf(model.relationships().size()));
        String languages = model.files().stream()
                .map(SourceFile::language)
                .filter(l -> l != null && !l.isBlank())
                .collect(Collectors.groupingBy(l -> l, Collectors.counting()))
                .entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(8)
                .map(e -> e.getKey() + "(" + e.getValue() + ")")
                .collect(Collectors.joining(", "));
        draft.repositoryField("languages", languages);
    }

    private void addModules(
            ContextDraft.Builder draft,
            RepositoryModel model,
            ContextRequest request,
            ContextStrategy strategy
    ) {
        if (request.purpose() == ContextPurpose.SELECTED_FILES
                || request.purpose() == ContextPurpose.SELECTED_SYMBOLS) {
            return;
        }
        int limit = strategy == ContextStrategy.COMPACT_ARCHITECTURE ? 8 : 30;
        List<Module> modules = model.modules().stream()
                .sorted(Comparator.comparing(Module::name))
                .limit(limit)
                .toList();
        if (modules.isEmpty()) {
            return;
        }
        for (Module module : modules) {
            draft.addModule(module.name() + " (" + module.id() + ")");
        }
        draft.include(modules.size() + " modules");
    }

    private void addComponents(
            ContextDraft.Builder draft,
            RepositoryModel model,
            Set<String> seeds,
            Set<String> related,
            ContextPurpose purpose,
            ContextStrategy strategy
    ) {
        int limit = switch (strategy) {
            case COMPACT_ARCHITECTURE -> 8;
            case SOURCE_AND_SYMBOLS -> 30;
            case ALTERNATE_PRIORITIZATION -> 20;
            case ARCHITECTURE_OVERVIEW -> purpose == ContextPurpose.FULL_REPOSITORY ? 40 : 25;
        };
        Comparator<Symbol> order = Comparator.comparing((Symbol s) -> seeds.contains(s.id()) ? 0 : 1)
                .thenComparing(Symbol::name);
        if (strategy == ContextStrategy.ALTERNATE_PRIORITIZATION) {
            order = Comparator.comparing(Symbol::name).reversed();
        }
        List<Symbol> symbols = related.stream()
                .map(model::findSymbol)
                .flatMap(Optional::stream)
                .filter(s -> isType(s.kind()))
                .sorted(order)
                .limit(limit)
                .toList();
        for (Symbol symbol : symbols) {
            draft.addComponent(symbol.kind().name() + " " + symbol.name()
                    + " @ " + symbol.location().filePath());
        }
        if (!symbols.isEmpty()) {
            draft.include(symbols.size() + " relevant classes/types");
        }
    }

    private void addRelationships(
            ContextDraft.Builder draft,
            RepositoryModel model,
            Set<String> seeds,
            Set<String> related,
            ContextPurpose purpose,
            ContextStrategy strategy
    ) {
        int limit = switch (strategy) {
            case COMPACT_ARCHITECTURE -> 15;
            case SOURCE_AND_SYMBOLS -> 8;
            case ALTERNATE_PRIORITIZATION -> 25;
            case ARCHITECTURE_OVERVIEW -> 40;
        };
        Comparator<Relationship> order = Comparator.comparing((Relationship r) -> Math.min(
                        seeds.contains(r.fromId()) ? 0 : 1,
                        seeds.contains(r.toId()) ? 0 : 1))
                .thenComparing(r -> r.type().name());
        if (strategy == ContextStrategy.ALTERNATE_PRIORITIZATION) {
            order = Comparator.comparing((Relationship r) -> r.type().name())
                    .thenComparing(Relationship::fromId)
                    .reversed();
        }
        List<Relationship> rels = model.relationships().stream()
                .filter(r -> r.type() != RelationshipType.CONTAINS)
                .filter(r -> related.contains(r.fromId()) || related.contains(r.toId())
                        || purpose == ContextPurpose.ARCHITECTURE
                        || strategy == ContextStrategy.COMPACT_ARCHITECTURE)
                .sorted(order)
                .limit(limit)
                .toList();
        for (Relationship rel : rels) {
            String from = model.findSymbol(rel.fromId()).map(Symbol::name).orElse(rel.fromId());
            String to = model.findSymbol(rel.toId()).map(Symbol::name).orElse(rel.toId());
            String conf = String.format(Locale.ROOT, "%.2f", rel.confidence());
            draft.addRelationship(from + " -[" + rel.type().name() + " conf=" + conf + "]-> " + to);
        }
        if (!rels.isEmpty()) {
            draft.include(rels.size() + " relationships");
        }
    }

    private void addEndpoints(
            ContextDraft.Builder draft,
            RepositoryModel model,
            ContextPurpose purpose,
            ContextStrategy strategy
    ) {
        if (purpose != ContextPurpose.API_BACKEND
                && purpose != ContextPurpose.FULL_REPOSITORY
                && purpose != ContextPurpose.CUSTOM
                && purpose != ContextPurpose.ARCHITECTURE
                && strategy != ContextStrategy.COMPACT_ARCHITECTURE) {
            return;
        }
        int limit = strategy == ContextStrategy.COMPACT_ARCHITECTURE ? 20 : 30;
        List<StructuralFact> facts = model.structuralFacts("usecase").stream()
                .filter(f -> "use_case".equals(f.kind()))
                .limit(limit)
                .toList();
        for (StructuralFact fact : facts) {
            draft.addEndpoint(fact.label() + (fact.detail().map(d -> " (" + d + ")").orElse("")));
        }
        if (!facts.isEmpty()) {
            draft.include(facts.size() + " endpoints/use-cases");
        }
    }

    private void addDataModels(
            ContextDraft.Builder draft,
            RepositoryModel model,
            ContextPurpose purpose,
            ContextStrategy strategy
    ) {
        if (purpose != ContextPurpose.DATABASE_JPA
                && purpose != ContextPurpose.FULL_REPOSITORY
                && purpose != ContextPurpose.CUSTOM
                && strategy != ContextStrategy.ALTERNATE_PRIORITIZATION) {
            return;
        }
        List<StructuralFact> entities = model.structuralFacts("er").stream()
                .filter(f -> "entity".equals(f.kind()) || f.kind().contains("to_") || "association".equals(f.kind()))
                .limit(strategy == ContextStrategy.ALTERNATE_PRIORITIZATION ? 20 : 40)
                .toList();
        for (StructuralFact fact : entities) {
            draft.addDataModel(fact.kind() + ": " + fact.label()
                    + fact.detail().map(d -> " [" + d + "]").orElse(""));
        }
        if (!entities.isEmpty()) {
            draft.include(entities.size() + " data-model facts");
        }
    }

    private void addConfiguration(ContextDraft.Builder draft, RepositoryModel model, ContextPurpose purpose) {
        if (purpose != ContextPurpose.FULL_REPOSITORY
                && purpose != ContextPurpose.ARCHITECTURE
                && purpose != ContextPurpose.CUSTOM
                && purpose != ContextPurpose.SECURITY) {
            return;
        }
        List<StructuralFact> deploy = model.structuralFacts("deployment").stream().limit(20).toList();
        for (StructuralFact fact : deploy) {
            draft.addConfiguration(fact.kind() + ": " + fact.label());
        }
        if (!deploy.isEmpty()) {
            draft.include(deploy.size() + " configuration/deployment facts");
        }
    }

    private void addDocumentation(
            ContextDraft.Builder draft,
            RepositoryModel model,
            Set<String> seeds,
            Set<String> related
    ) {
        Set<String> entityIds = new HashSet<>(related);
        entityIds.addAll(seeds);
        List<String> excerpts = new ArrayList<>();
        for (DocumentationDocument doc : model.documentation()) {
            for (DocumentationSection section : doc.sections()) {
                boolean relevant = entityIds.isEmpty()
                        || model.documentationReferences().stream()
                        .anyMatch(ref -> ref.sectionId().equals(section.id())
                                && entityIds.contains(ref.entityId()));
                if (!relevant && !entityIds.isEmpty()) {
                    continue;
                }
                String text = section.text();
                if (text.length() > 600) {
                    text = text.substring(0, 600) + "…";
                }
                excerpts.add(doc.path() + " · " + section.heading() + "\n" + text);
                if (excerpts.size() >= 6) {
                    break;
                }
            }
            if (excerpts.size() >= 6) {
                break;
            }
        }
        excerpts.forEach(draft::addDocumentation);
        if (!excerpts.isEmpty()) {
            draft.include(excerpts.size() + " documentation excerpts");
        }
    }

    private void addAnalysisNotes(ContextDraft.Builder draft, List<AnalysisResult> results) {
        for (AnalysisResult result : results.stream().limit(8).toList()) {
            draft.addAnalysisNote(result.analyzerId() + ": " + result.summary());
        }
        if (!results.isEmpty()) {
            draft.include("analyzer summaries");
        }
    }

    private void addSourceSnippets(
            ContextDraft.Builder draft,
            RepositoryModel model,
            Path workingTree,
            Set<String> filePaths,
            ContextRequest request,
            ContextStrategy strategy
    ) {
        int added = 0;
        int skippedSensitive = 0;
        int skippedMissing = 0;
        int maxFiles = Math.max(strategy.maxSourceFiles(), request.scope().filePaths().size());
        int maxChars = Math.max(strategy.maxSnippetChars(), 400);
        for (String path : filePaths) {
            if (added >= maxFiles) {
                draft.exclude((filePaths.size() - added) + " additional source files (cap)");
                break;
            }
            if (SecretRedactor.looksSensitivePath(path)) {
                skippedSensitive++;
                continue;
            }
            Optional<SourceFile> meta = model.findFile(path);
            if (meta.isEmpty()) {
                skippedMissing++;
                continue;
            }
            Path absolute = workingTree.resolve(path);
            if (!Files.isRegularFile(absolute)) {
                skippedMissing++;
                continue;
            }
            try {
                String raw = Files.readString(absolute, StandardCharsets.UTF_8);
                if (raw.length() > maxChars) {
                    raw = raw.substring(0, maxChars) + "\n… [truncated for context budget]";
                }
                String safe = SecretRedactor.redact(raw);
                draft.addSourceSnippet("### " + path + " (" + meta.get().language() + ")\n```\n" + safe + "\n```\n");
                added++;
            } catch (IOException ex) {
                skippedMissing++;
            }
        }
        if (added > 0) {
            draft.include(added + " source files");
        }
        if (skippedSensitive > 0) {
            draft.exclude(skippedSensitive + " sensitive paths");
        }
        if (skippedMissing > 0) {
            draft.exclude(skippedMissing + " unreadable/missing files");
        }
        if (request.purpose() == ContextPurpose.SECURITY && added == 0) {
            draft.addAnalysisNote("No security-named symbols or readable security files were found in the model.");
        }
    }

    private FitResult fitToBudget(
            ContextDraft built,
            ContextFormatter formatter,
            int budget
    ) {
        String content = formatter.format(built);
        if (estimator.estimate(content) <= budget) {
            return new FitResult(content, List.of());
        }
        ContextDraft.Builder slim = ContextDraft.builder(
                built.title(), built.purpose(), built.scope(), built.strategy(), built.tokenBudget());
        built.repository().forEach(slim::repositoryField);
        built.components().forEach(slim::addComponent);
        built.relationships().forEach(slim::addRelationship);
        built.endpoints().forEach(slim::addEndpoint);
        built.dataModels().forEach(slim::addDataModel);
        built.analysisNotes().forEach(slim::addAnalysisNote);
        built.included().stream().filter(i -> !i.contains("source") && !i.contains("documentation"))
                .forEach(slim::include);
        built.excluded().forEach(slim::exclude);
        content = formatter.format(slim.build());
        if (estimator.estimate(content) <= budget) {
            return new FitResult(content, List.of(
                    "source snippets omitted to fit token budget",
                    "documentation omitted to fit token budget"));
        }
        ContextDraft.Builder minimal = ContextDraft.builder(
                built.title(), built.purpose(), built.scope(), built.strategy(), built.tokenBudget());
        built.repository().forEach(minimal::repositoryField);
        built.components().stream().limit(12).forEach(minimal::addComponent);
        built.relationships().stream().limit(12).forEach(minimal::addRelationship);
        minimal.include("repository overview");
        minimal.include("truncated components/relationships");
        content = formatter.format(minimal.build());
        if (estimator.estimate(content) <= budget) {
            return new FitResult(content, List.of("lower-priority sections omitted to fit token budget"));
        }
        int approxChars = Math.max(budget * 4, 500);
        if (content.length() > approxChars) {
            content = content.substring(0, approxChars) + "\n\n… [truncated to fit estimated token budget]\n";
        }
        return new FitResult(content, List.of("content hard-truncated to fit estimated token budget"));
    }

    private record FitResult(String content, List<String> extraExcluded) {
        private FitResult {
            extraExcluded = List.copyOf(extraExcluded);
        }
    }

    private static boolean isType(SymbolKind kind) {
        return kind == SymbolKind.CLASS || kind == SymbolKind.INTERFACE
                || kind == SymbolKind.ENUM || kind == SymbolKind.TYPE;
    }
}

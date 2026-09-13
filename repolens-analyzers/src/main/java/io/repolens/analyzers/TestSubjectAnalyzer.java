package io.repolens.analyzers;

import io.repolens.core.model.AnalysisResult;
import io.repolens.core.model.Evidence;
import io.repolens.core.model.Import;
import io.repolens.core.model.InferenceMethod;
import io.repolens.core.model.Metric;
import io.repolens.core.model.Relationship;
import io.repolens.core.model.RelationshipType;
import io.repolens.core.model.RepositoryModel;
import io.repolens.core.model.Symbol;
import io.repolens.core.model.SymbolKind;
import io.repolens.core.model.Test;
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
 * Derives TESTS relationships from Test facts plus existing CALLS/IMPORTS.
 * Does not parse source. Does not mutate Test facts.
 */
public final class TestSubjectAnalyzer implements Analyzer {

    static final double IMPORT_CONFIDENCE = 0.55;

    @Override
    public String id() {
        return "test-subjects";
    }

    @Override
    public AnalysisResult analyze(RepositoryModel model) {
        Set<String> testSymbols = new HashSet<>();
        Map<String, List<Test>> testsBySymbol = new HashMap<>();
        for (Test test : model.tests()) {
            testSymbols.add(test.symbolId());
            testsBySymbol.computeIfAbsent(test.symbolId(), ignored -> new ArrayList<>()).add(test);
        }

        List<Relationship> relationships = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int seq = 0;

        for (Relationship call : model.relationships()) {
            if (call.type() != RelationshipType.CALLS) {
                continue;
            }
            if (isTestRelated(model, testSymbols, call.toId())) {
                continue;
            }
            List<Test> tests = testsBySymbol.getOrDefault(call.fromId(), List.of());
            for (Test test : tests) {
                seq = emitTests(relationships, seen, seq, test, call.toId(), call.confidence(),
                        call, InferenceMethod.TYPE_RESOLUTION, "CALLS");
            }
        }

        Map<String, String> typeIndex = buildTypeIndex(model);
        for (Test test : model.tests()) {
            Optional<Symbol> symbol = model.findSymbol(test.symbolId());
            if (symbol.isEmpty() || !isType(symbol.get().kind())) {
                continue;
            }
            String file = symbol.get().location().filePath();
            for (Import importDecl : model.imports()) {
                if (!importDecl.sourceFilePath().equals(file)) {
                    continue;
                }
                String targetType = resolveType(typeIndex, importDecl.rawImport());
                if (targetType == null || isTestRelated(model, testSymbols, targetType)) {
                    continue;
                }
                emitTests(
                        relationships,
                        seen,
                        relationships.size(),
                        test,
                        targetType,
                        IMPORT_CONFIDENCE,
                        null,
                        InferenceMethod.TYPE_RESOLUTION,
                        "IMPORTS"
                );
            }
        }

        List<Metric> metrics = List.of(
                new Metric(
                        "tests_relationship_count",
                        relationships.size(),
                        Optional.empty(),
                        Optional.of(model.repository().id())
                )
        );
        String summary = "Test subjects: " + relationships.size() + " TESTS relationships";
        return new AnalysisResult(id(), summary, metrics, relationships, List.of());
    }

    private static int emitTests(
            List<Relationship> relationships,
            Set<String> seen,
            int seq,
            Test test,
            String subjectId,
            double confidence,
            Relationship call,
            InferenceMethod method,
            String via
    ) {
        String key = test.symbolId() + "->" + subjectId;
        if (!seen.add(key)) {
            return seq;
        }
        Optional<String> referenced = call == null ? Optional.empty() : Optional.of(call.id());
        Evidence evidence = new Evidence(
                methodForCall(call, method),
                test.evidence().location().or(() -> Optional.of(test.location())),
                referenced,
                Optional.of("tests via " + via)
        );
        String provenance = call != null
                ? "tests;" + call.provenance().orElse("calls")
                : "tests;imports";
        relationships.add(new Relationship(
                "tests-rel-" + (++seq),
                RelationshipType.TESTS,
                test.symbolId(),
                subjectId,
                confidence,
                Optional.of(provenance),
                Optional.of(evidence)
        ));
        return seq;
    }

    private static InferenceMethod methodForCall(Relationship call, InferenceMethod fallback) {
        if (call == null) {
            return fallback;
        }
        String provenance = call.provenance().orElse("");
        if (provenance.contains("via=name-heuristic")) {
            return InferenceMethod.NAME_HEURISTIC;
        }
        if (call.confidence() >= 0.9) {
            return InferenceMethod.TYPE_RESOLUTION;
        }
        return fallback;
    }

    private static boolean isTestRelated(RepositoryModel model, Set<String> testSymbols, String symbolId) {
        if (testSymbols.contains(symbolId)) {
            return true;
        }
        Optional<Symbol> current = model.findSymbol(symbolId);
        while (current.isPresent()) {
            Optional<String> parent = current.get().parentSymbolId();
            if (parent.isEmpty()) {
                break;
            }
            if (testSymbols.contains(parent.get())) {
                return true;
            }
            current = model.findSymbol(parent.get());
        }
        return false;
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

    private static String resolveType(Map<String, String> typeIndex, String rawImport) {
        String spec = rawImport == null ? "" : rawImport.trim();
        spec = spec.replaceFirst("^import\\s+(?:static\\s+)?", "").replace(";", "").trim();
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

    private static boolean isType(SymbolKind kind) {
        return kind == SymbolKind.CLASS
                || kind == SymbolKind.INTERFACE
                || kind == SymbolKind.ENUM
                || kind == SymbolKind.TYPE;
    }
}

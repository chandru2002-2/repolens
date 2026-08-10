package io.repolens.analyzers;

import io.repolens.core.model.AnalysisResult;
import io.repolens.core.model.Metric;
import io.repolens.core.model.Module;
import io.repolens.core.model.RepositoryModel;
import io.repolens.core.model.Symbol;
import io.repolens.core.model.SymbolKind;
import io.repolens.core.ports.Analyzer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Summarizes repository structure from RepositoryModel only.
 */
public final class StructureAnalyzer implements Analyzer {

    @Override
    public String id() {
        return "structure";
    }

    @Override
    public AnalysisResult analyze(RepositoryModel model) {
        Map<SymbolKind, Long> byKind = new EnumMap<>(SymbolKind.class);
        for (SymbolKind kind : SymbolKind.values()) {
            byKind.put(kind, 0L);
        }
        for (Symbol symbol : model.symbols()) {
            byKind.merge(symbol.kind(), 1L, Long::sum);
        }

        List<Metric> metrics = new ArrayList<>();
        String repoId = model.repository().id();
        metrics.add(metric("module_count", model.modules().size(), repoId));
        metrics.add(metric("symbol_count", model.symbolCount(), repoId));
        metrics.add(metric("file_count", model.fileCount(), repoId));
        for (Map.Entry<SymbolKind, Long> entry : byKind.entrySet()) {
            if (entry.getValue() > 0) {
                metrics.add(new Metric(
                        "symbol_count_" + entry.getKey().name().toLowerCase(Locale.ROOT),
                        entry.getValue(),
                        Optional.empty(),
                        Optional.of(repoId)
                ));
            }
        }

        List<AnalysisResult.Finding> findings = new ArrayList<>();
        int findingSeq = 0;
        for (Module module : model.modules()) {
            long symbolsInModule = model.symbols().stream()
                    .filter(symbol -> symbol.moduleId().isPresent() && symbol.moduleId().get().equals(module.id()))
                    .count();
            if (symbolsInModule == 0) {
                findings.add(new AnalysisResult.Finding(
                        "structure-finding-" + (++findingSeq),
                        "info",
                        "Module has no extracted symbols: " + module.name(),
                        Optional.of(module.id())
                ));
            }
        }

        List<Module> largest = model.modules().stream()
                .sorted(Comparator.comparingLong((Module module) -> model.symbols().stream()
                                .filter(s -> s.moduleId().isPresent() && s.moduleId().get().equals(module.id()))
                                .count())
                        .reversed())
                .limit(3)
                .toList();

        String summary = "Structure: " + model.modules().size() + " modules, "
                + model.symbolCount() + " symbols across " + model.fileCount() + " files"
                + (largest.isEmpty() ? "" : "; top modules: "
                + String.join(", ", largest.stream().map(Module::name).toList()));

        return new AnalysisResult(id(), summary, metrics, List.of(), findings);
    }

    private static Metric metric(String name, double value, String scopeId) {
        return new Metric(name, value, Optional.empty(), Optional.of(scopeId));
    }
}

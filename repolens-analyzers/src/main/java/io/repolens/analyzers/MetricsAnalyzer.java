package io.repolens.analyzers;

import io.repolens.core.model.AnalysisResult;
import io.repolens.core.model.Metric;
import io.repolens.core.model.RepositoryModel;
import io.repolens.core.model.SourceFile;
import io.repolens.core.model.Symbol;
import io.repolens.core.ports.Analyzer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Computes basic quantitative metrics from RepositoryModel.
 */
public final class MetricsAnalyzer implements Analyzer {

    @Override
    public String id() {
        return "metrics";
    }

    @Override
    public AnalysisResult analyze(RepositoryModel model) {
        String repoId = model.repository().id();
        long totalBytes = model.files().stream().mapToLong(SourceFile::sizeBytes).sum();

        Map<String, Integer> symbolsPerFile = new HashMap<>();
        for (Symbol symbol : model.symbols()) {
            symbolsPerFile.merge(symbol.location().filePath(), 1, Integer::sum);
        }

        double avgSymbolsPerFile = model.fileCount() == 0
                ? 0.0
                : (double) model.symbolCount() / model.fileCount();
        double avgFileBytes = model.fileCount() == 0
                ? 0.0
                : (double) totalBytes / model.fileCount();
        int maxSymbolsInFile = symbolsPerFile.values().stream().mapToInt(Integer::intValue).max().orElse(0);

        List<Metric> metrics = new ArrayList<>();
        metrics.add(new Metric("total_source_bytes", totalBytes, Optional.of("bytes"), Optional.of(repoId)));
        metrics.add(new Metric("avg_file_bytes", avgFileBytes, Optional.of("bytes"), Optional.of(repoId)));
        metrics.add(new Metric("avg_symbols_per_file", avgSymbolsPerFile, Optional.empty(), Optional.of(repoId)));
        metrics.add(new Metric("max_symbols_in_file", maxSymbolsInFile, Optional.empty(), Optional.of(repoId)));
        metrics.add(new Metric("relationship_count", model.relationships().size(), Optional.empty(), Optional.of(repoId)));
        metrics.add(new Metric("language_file_count", distinctLanguages(model), Optional.empty(), Optional.of(repoId)));

        List<AnalysisResult.Finding> findings = new ArrayList<>();
        if (maxSymbolsInFile >= 40) {
            String hotFile = symbolsPerFile.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("unknown");
            findings.add(new AnalysisResult.Finding(
                    "metrics-finding-1",
                    "info",
                    "Dense file with " + maxSymbolsInFile + " symbols: " + hotFile,
                    Optional.empty()
            ));
        }

        String summary = String.format(
                "Metrics: %d bytes across %d files (avg %.1f symbols/file)",
                totalBytes,
                model.fileCount(),
                avgSymbolsPerFile
        );
        return new AnalysisResult(id(), summary, metrics, List.of(), findings);
    }

    private static long distinctLanguages(RepositoryModel model) {
        return model.files().stream().map(SourceFile::language).distinct().count();
    }
}

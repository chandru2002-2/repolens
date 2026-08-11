package io.repolens.core.pipeline;

import io.repolens.core.model.AnalysisResult;
import io.repolens.core.model.Metric;
import io.repolens.core.model.RepositoryModel;
import io.repolens.core.model.WorkingTreeInventory;
import io.repolens.core.ports.AnalysisRunner;
import io.repolens.core.ports.Analyzer;
import io.repolens.core.ports.RepositoryIngestor;
import io.repolens.core.ports.SourceAnalyzer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Default pipeline wiring. Adapters (CLI/Web) depend on this orchestration, not on parsers.
 */
public final class DefaultAnalysisRunner implements AnalysisRunner {

    private static final int MAX_SKIPPED_PATHS_IN_SUMMARY = 5;

    private final RepositoryIngestor ingestor;
    private final SourceAnalyzer sourceAnalyzer;
    private final List<Analyzer> analyzers;

    public DefaultAnalysisRunner(
            RepositoryIngestor ingestor,
            SourceAnalyzer sourceAnalyzer,
            List<Analyzer> analyzers
    ) {
        this.ingestor = Objects.requireNonNull(ingestor, "ingestor");
        this.sourceAnalyzer = Objects.requireNonNull(sourceAnalyzer, "sourceAnalyzer");
        this.analyzers = List.copyOf(Objects.requireNonNull(analyzers, "analyzers"));
    }

    @Override
    public AnalysisRunResult run(AnalysisRequest request) {
        Objects.requireNonNull(request, "request");
        var ingestion = ingestor.ingest(new RepositoryIngestor.IngestionRequest(request.source(), request.remote()));
        RepositoryModel model = sourceAnalyzer.analyze(
                ingestion.repository(),
                ingestion.workingTree(),
                ingestion.inventory()
        );
        List<AnalysisResult> results = new ArrayList<>(analyzers.size() + 1);
        ingestNotes(ingestion.inventory()).ifPresent(results::add);
        for (Analyzer analyzer : analyzers) {
            results.add(analyzer.analyze(model));
        }
        return new AnalysisRunResult(model, results);
    }

    static Optional<AnalysisResult> ingestNotes(WorkingTreeInventory inventory) {
        List<WorkingTreeInventory.SkippedFile> oversized = inventory.skippedOversizedFiles();
        if (oversized.isEmpty()) {
            return Optional.empty();
        }

        long limitBytes = oversized.getFirst().limitBytes();
        String limitLabel = formatByteLimit(limitBytes);
        String summary = oversized.size() == 1
                ? "Skipped 1 file exceeding the " + limitLabel + " file-size limit."
                : "Skipped " + oversized.size() + " files exceeding the " + limitLabel + " file-size limit.";

        List<String> samplePaths = oversized.stream()
                .map(WorkingTreeInventory.SkippedFile::relativePath)
                .limit(MAX_SKIPPED_PATHS_IN_SUMMARY)
                .toList();
        if (oversized.size() > MAX_SKIPPED_PATHS_IN_SUMMARY) {
            summary = summary + " Examples: " + String.join(", ", samplePaths) + ", …";
        } else if (!samplePaths.isEmpty()) {
            summary = summary + " Paths: " + String.join(", ", samplePaths);
        }

        List<Metric> metrics = List.of(
                new Metric(
                        "skipped_oversized_file_count",
                        oversized.size(),
                        Optional.empty(),
                        Optional.empty()
                ),
                new Metric(
                        "max_file_bytes",
                        limitBytes,
                        Optional.of("bytes"),
                        Optional.empty()
                )
        );

        List<AnalysisResult.Finding> findings = new ArrayList<>();
        findings.add(new AnalysisResult.Finding(
                "ingest-oversized-files",
                "warning",
                summary,
                Optional.empty()
        ));
        int index = 0;
        for (WorkingTreeInventory.SkippedFile skipped : oversized) {
            findings.add(new AnalysisResult.Finding(
                    "ingest-oversized-" + (++index),
                    "info",
                    "Skipped oversized file (" + skipped.sizeBytes() + " bytes): " + skipped.relativePath(),
                    Optional.of(skipped.relativePath())
            ));
        }

        return Optional.of(new AnalysisResult(
                "ingest",
                summary,
                metrics,
                List.of(),
                findings
        ));
    }

    static String formatByteLimit(long bytes) {
        if (bytes <= 0) {
            return "configured";
        }
        if (bytes % (1024L * 1024L) == 0) {
            return (bytes / (1024L * 1024L)) + " MB";
        }
        if (bytes % 1024L == 0) {
            return (bytes / 1024L) + " KB";
        }
        return String.format(Locale.ROOT, "%,d bytes", bytes);
    }
}

package io.repolens.core.pipeline;

import io.repolens.core.model.AnalysisResult;
import io.repolens.core.model.RepositoryModel;
import io.repolens.core.ports.AnalysisRunner;
import io.repolens.core.ports.Analyzer;
import io.repolens.core.ports.RepositoryIngestor;
import io.repolens.core.ports.SourceAnalyzer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Default pipeline wiring. Adapters (CLI/Web) depend on this orchestration, not on parsers.
 */
public final class DefaultAnalysisRunner implements AnalysisRunner {

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
        List<AnalysisResult> results = new ArrayList<>(analyzers.size());
        for (Analyzer analyzer : analyzers) {
            results.add(analyzer.analyze(model));
        }
        return new AnalysisRunResult(model, results);
    }
}

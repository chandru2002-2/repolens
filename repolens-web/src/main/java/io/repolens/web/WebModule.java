package io.repolens.web;

import io.repolens.analyzers.AnalyzersModule;
import io.repolens.core.pipeline.DefaultAnalysisRunner;
import io.repolens.core.ports.AnalysisRunner;
import io.repolens.ingest.IngestModule;
import io.repolens.parse.ParseModule;

/**
 * Composition root for the Web adapter.
 */
public final class WebModule {
    private WebModule() {
    }

    public static AnalysisRunner analysisRunner() {
        return new DefaultAnalysisRunner(
                IngestModule.localIngestor(),
                ParseModule.sourceAnalyzer(),
                AnalyzersModule.defaultAnalyzers()
        );
    }

    public static AnalysisJobService jobService() {
        return new AnalysisJobService(analysisRunner(), new InMemoryJobStore());
    }

    public static String status() {
        return "web-javalin";
    }
}

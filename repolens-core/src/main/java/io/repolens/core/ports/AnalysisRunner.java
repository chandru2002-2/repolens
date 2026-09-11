package io.repolens.core.ports;

import io.repolens.core.model.AnalysisResult;
import io.repolens.core.model.RepositoryModel;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Orchestrates ingest → parse → analyze for a single request.
 */
public interface AnalysisRunner {

    AnalysisRunResult run(AnalysisRequest request);

    record AnalysisRequest(
            String source,
            boolean remote
    ) {
        public AnalysisRequest {
            if (source == null || source.isBlank()) {
                throw new IllegalArgumentException("source must not be blank");
            }
        }
    }

    /**
     * Completed analysis session: model + results + working tree used during ingest/parse.
     */
    record AnalysisRunResult(
            RepositoryModel model,
            List<AnalysisResult> results,
            Path workingTree
    ) {
        public AnalysisRunResult {
            Objects.requireNonNull(model, "model");
            Objects.requireNonNull(results, "results");
            Objects.requireNonNull(workingTree, "workingTree");
            results = List.copyOf(results);
        }
    }
}

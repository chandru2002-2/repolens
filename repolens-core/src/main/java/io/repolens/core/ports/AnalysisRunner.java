package io.repolens.core.ports;

import io.repolens.core.model.AnalysisResult;
import io.repolens.core.model.RepositoryModel;

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

    record AnalysisRunResult(
            RepositoryModel model,
            List<AnalysisResult> results
    ) {
        public AnalysisRunResult {
            Objects.requireNonNull(model, "model");
            Objects.requireNonNull(results, "results");
            results = List.copyOf(results);
        }
    }
}

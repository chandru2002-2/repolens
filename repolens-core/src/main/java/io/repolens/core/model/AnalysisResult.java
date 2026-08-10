package io.repolens.core.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Result produced by a single analyzer operating on a RepositoryModel.
 */
public record AnalysisResult(
        String analyzerId,
        String summary,
        List<Metric> metrics,
        List<Relationship> relationships,
        List<Finding> findings
) {
    public AnalysisResult {
        Objects.requireNonNull(analyzerId, "analyzerId");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(metrics, "metrics");
        Objects.requireNonNull(relationships, "relationships");
        Objects.requireNonNull(findings, "findings");
        if (analyzerId.isBlank()) {
            throw new IllegalArgumentException("analyzerId must not be blank");
        }
        metrics = List.copyOf(metrics);
        relationships = List.copyOf(relationships);
        findings = List.copyOf(findings);
    }

    public record Finding(
            String id,
            String severity,
            String message,
            Optional<String> subjectId
    ) {
        public Finding {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(severity, "severity");
            Objects.requireNonNull(message, "message");
            Objects.requireNonNull(subjectId, "subjectId");
            if (id.isBlank() || severity.isBlank() || message.isBlank()) {
                throw new IllegalArgumentException("id, severity, and message must not be blank");
            }
        }
    }
}

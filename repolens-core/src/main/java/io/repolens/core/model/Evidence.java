package io.repolens.core.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Structured provenance for a fact, relationship, or derived hop (ADR-012).
 * Confidence remains on the relationship or hop, not here.
 */
public record Evidence(
        InferenceMethod inferenceMethod,
        Optional<SourceLocation> location,
        Optional<String> referencedId,
        Optional<String> summary
) {
    public Evidence {
        Objects.requireNonNull(inferenceMethod, "inferenceMethod");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(referencedId, "referencedId");
        Objects.requireNonNull(summary, "summary");
        referencedId = referencedId.filter(id -> !id.isBlank());
        summary = summary.filter(text -> !text.isBlank());
    }

    public static Evidence of(InferenceMethod inferenceMethod) {
        return new Evidence(inferenceMethod, Optional.empty(), Optional.empty(), Optional.empty());
    }
}

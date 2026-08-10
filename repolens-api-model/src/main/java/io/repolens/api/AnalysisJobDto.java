package io.repolens.api;

import java.util.Objects;

/**
 * Ephemeral analysis job status for the Web API (ADR-006).
 */
public record AnalysisJobDto(
        String id,
        String status,
        String source,
        boolean remote,
        String createdAt,
        String updatedAt,
        String error,
        AnalysisResponseDto result
) {
    public AnalysisJobDto {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}

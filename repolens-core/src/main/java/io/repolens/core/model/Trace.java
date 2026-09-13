package io.repolens.core.model;

import java.util.List;
import java.util.Objects;

/**
 * Statically inferred path through repository facts and relationships (ADR-012).
 * Not runtime execution. Not stored on {@link RepositoryModel}.
 */
public record Trace(
        String id,
        String endpointId,
        List<TraceHop> hops,
        double confidence,
        boolean unresolved,
        String inferenceKind
) {
    public static final String STATIC = "static";

    public Trace {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(endpointId, "endpointId");
        Objects.requireNonNull(hops, "hops");
        Objects.requireNonNull(inferenceKind, "inferenceKind");
        if (id.isBlank() || endpointId.isBlank() || inferenceKind.isBlank()) {
            throw new IllegalArgumentException("id, endpointId, and inferenceKind must not be blank");
        }
        if (hops.isEmpty()) {
            throw new IllegalArgumentException("hops must not be empty");
        }
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        }
        hops = List.copyOf(hops);
    }
}

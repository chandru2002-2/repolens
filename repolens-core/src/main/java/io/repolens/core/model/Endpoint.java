package io.repolens.core.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Declared HTTP (or equivalent) operation captured as a repository fact (ADR-012).
 * Handler-to-collaborator hops are traces, not endpoint fields.
 */
public record Endpoint(
        String id,
        String httpMethod,
        String path,
        Optional<String> ownerTypeId,
        Optional<String> handlerMethodId,
        SourceLocation location,
        Evidence evidence
) {
    /** HTTP method when the declaration does not distinguish one. */
    public static final String UNKNOWN_METHOD = "UNKNOWN";

    public Endpoint {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(httpMethod, "httpMethod");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(ownerTypeId, "ownerTypeId");
        Objects.requireNonNull(handlerMethodId, "handlerMethodId");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(evidence, "evidence");
        if (id.isBlank() || httpMethod.isBlank() || path.isBlank()) {
            throw new IllegalArgumentException("id, httpMethod, and path must not be blank");
        }
        ownerTypeId = ownerTypeId.filter(value -> !value.isBlank());
        handlerMethodId = handlerMethodId.filter(value -> !value.isBlank());
    }
}

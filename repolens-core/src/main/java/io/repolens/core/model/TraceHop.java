package io.repolens.core.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One step in a static, evidence-backed trace. Not a {@link RepositoryModel} fact.
 */
public record TraceHop(
        String entityId,
        String role,
        Optional<String> relationshipId,
        Evidence evidence,
        double confidence,
        boolean resolved
) {
    public static final String ROLE_ENDPOINT = "endpoint";
    public static final String ROLE_HANDLER = "handler";
    public static final String ROLE_SYMBOL = "symbol";
    public static final String ROLE_UNRESOLVED = "unresolved";

    public static final String UNRESOLVED_ID = "unresolved";

    public TraceHop {
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(relationshipId, "relationshipId");
        Objects.requireNonNull(evidence, "evidence");
        if (entityId.isBlank() || role.isBlank()) {
            throw new IllegalArgumentException("entityId and role must not be blank");
        }
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        }
    }
}

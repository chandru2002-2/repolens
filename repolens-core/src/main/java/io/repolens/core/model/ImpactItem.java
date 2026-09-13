package io.repolens.core.model;

import java.util.Objects;
import java.util.Optional;

/**
 * One evidence-backed impact of a selected entity. Not a {@link RepositoryModel} fact.
 */
public record ImpactItem(
        String entityId,
        String category,
        boolean inferred,
        double confidence,
        Evidence evidence,
        Optional<String> relationshipId,
        Optional<String> traceId
) {
    public static final String CATEGORY_CALLER = "caller";
    public static final String CATEGORY_DEPENDENT = "dependent";
    public static final String CATEGORY_TEST = "test";
    public static final String CATEGORY_ENDPOINT = "endpoint";
    public static final String CATEGORY_INFERRED = "inferred";

    public ImpactItem {
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(relationshipId, "relationshipId");
        Objects.requireNonNull(traceId, "traceId");
        if (entityId.isBlank() || category.isBlank()) {
            throw new IllegalArgumentException("entityId and category must not be blank");
        }
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        }
        relationshipId = relationshipId.filter(id -> !id.isBlank());
        traceId = traceId.filter(id -> !id.isBlank());
    }
}

package io.repolens.core.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Directed relationship between two model entity ids.
 */
public record Relationship(
        String id,
        RelationshipType type,
        String fromId,
        String toId,
        double confidence,
        Optional<String> provenance,
        Optional<Evidence> evidence
) {
    public Relationship {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(fromId, "fromId");
        Objects.requireNonNull(toId, "toId");
        Objects.requireNonNull(provenance, "provenance");
        if (evidence == null) {
            evidence = Optional.empty();
        }
        if (id.isBlank() || fromId.isBlank() || toId.isBlank()) {
            throw new IllegalArgumentException("id, fromId, and toId must not be blank");
        }
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        }
    }

    /** v1.6-compatible constructor; structured evidence is empty. */
    public Relationship(
            String id,
            RelationshipType type,
            String fromId,
            String toId,
            double confidence,
            Optional<String> provenance
    ) {
        this(id, type, fromId, toId, confidence, provenance, Optional.empty());
    }

    public static Relationship of(String id, RelationshipType type, String fromId, String toId) {
        return new Relationship(id, type, fromId, toId, 1.0, Optional.empty(), Optional.empty());
    }
}

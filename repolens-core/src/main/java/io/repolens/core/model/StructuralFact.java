package io.repolens.core.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Deterministic structural observation used to project specialized diagrams.
 * Populated during parsing/config scanning — analyzers never parse source.
 */
public record StructuralFact(
        String id,
        String category,
        String kind,
        String label,
        Optional<String> sourceEntityId,
        Optional<String> targetEntityId,
        Optional<String> detail,
        Optional<String> filePath,
        Optional<Integer> line
) {
    public StructuralFact {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(sourceEntityId, "sourceEntityId");
        Objects.requireNonNull(targetEntityId, "targetEntityId");
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(filePath, "filePath");
        Objects.requireNonNull(line, "line");
        if (id.isBlank() || category.isBlank() || kind.isBlank() || label.isBlank()) {
            throw new IllegalArgumentException("id, category, kind, and label must not be blank");
        }
    }

    public static StructuralFact of(
            String id,
            String category,
            String kind,
            String label
    ) {
        return new StructuralFact(
                id,
                category,
                kind,
                label,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
    }
}

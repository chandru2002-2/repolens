package io.repolens.core.model;

import java.util.Objects;

/**
 * Deterministic association between a documentation section and a model entity id
 * (symbol, module, or file path).
 */
public record DocumentationReference(
        String id,
        String documentId,
        String sectionId,
        String entityId,
        String matchedText
) {
    public DocumentationReference {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(sectionId, "sectionId");
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(matchedText, "matchedText");
        if (id.isBlank() || documentId.isBlank() || sectionId.isBlank() || entityId.isBlank()) {
            throw new IllegalArgumentException("documentation reference ids must not be blank");
        }
        if (matchedText.isBlank()) {
            throw new IllegalArgumentException("matchedText must not be blank");
        }
    }
}

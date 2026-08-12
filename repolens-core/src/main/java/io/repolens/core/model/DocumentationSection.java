package io.repolens.core.model;

import java.util.Objects;

/**
 * Heading-bounded excerpt within a documentation document.
 */
public record DocumentationSection(
        String id,
        String heading,
        String text,
        int startLine
) {
    public DocumentationSection {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(heading, "heading");
        Objects.requireNonNull(text, "text");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (startLine < 1) {
            throw new IllegalArgumentException("startLine must be >= 1");
        }
    }
}

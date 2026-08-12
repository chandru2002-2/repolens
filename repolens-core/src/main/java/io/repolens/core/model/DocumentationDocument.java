package io.repolens.core.model;

import java.util.List;
import java.util.Objects;

/**
 * Markdown documentation file indexed from the working tree (README, docs/, …).
 * Supplemental context only — never the source of truth for structure.
 */
public record DocumentationDocument(
        String id,
        String path,
        String title,
        List<DocumentationSection> sections
) {
    public DocumentationDocument {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(sections, "sections");
        if (id.isBlank() || path.isBlank()) {
            throw new IllegalArgumentException("id and path must not be blank");
        }
        sections = List.copyOf(sections);
    }
}

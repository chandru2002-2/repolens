package io.repolens.core.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Logical module or package grouping derived during parsing.
 */
public record Module(
        String id,
        String name,
        String language,
        Optional<String> parentModuleId
) {
    public Module {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(parentModuleId, "parentModuleId");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }

    public static Module of(String id, String name, String language) {
        return new Module(id, name, language, Optional.empty());
    }
}

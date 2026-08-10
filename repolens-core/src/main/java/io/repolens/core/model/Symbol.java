package io.repolens.core.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Named program element (class, function, method, etc.).
 */
public record Symbol(
        String id,
        String name,
        SymbolKind kind,
        Optional<String> parentSymbolId,
        Optional<String> moduleId,
        SourceLocation location,
        Optional<String> visibility
) {
    public Symbol {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(parentSymbolId, "parentSymbolId");
        Objects.requireNonNull(moduleId, "moduleId");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(visibility, "visibility");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }
}

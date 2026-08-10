package io.repolens.core.model;

import java.util.Objects;
import java.util.Optional;

/**
 * External package-manager dependency (Maven, npm, etc.).
 */
public record ExternalDependency(
        String id,
        String name,
        Optional<String> version,
        Optional<String> ecosystem,
        Optional<String> manifestPath
) {
    public ExternalDependency {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(ecosystem, "ecosystem");
        Objects.requireNonNull(manifestPath, "manifestPath");
        if (id.isBlank() || name.isBlank()) {
            throw new IllegalArgumentException("id and name must not be blank");
        }
    }
}

package io.repolens.core.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Named numeric measurement attached to a scope (repo, module, symbol, etc.).
 */
public record Metric(
        String name,
        double value,
        Optional<String> unit,
        Optional<String> scopeId
) {
    public Metric {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(scopeId, "scopeId");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }
}

package io.repolens.core.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Discovered test type or method (ADR-012). Subject linking is derived intelligence,
 * not stored on this fact.
 */
public record Test(
        String id,
        String symbolId,
        Optional<String> frameworkHint,
        SourceLocation location,
        Evidence evidence
) {
    public Test {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(symbolId, "symbolId");
        Objects.requireNonNull(frameworkHint, "frameworkHint");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(evidence, "evidence");
        if (id.isBlank() || symbolId.isBlank()) {
            throw new IllegalArgumentException("id and symbolId must not be blank");
        }
        frameworkHint = frameworkHint.filter(value -> !value.isBlank());
    }
}

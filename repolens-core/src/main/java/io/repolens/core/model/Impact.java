package io.repolens.core.model;

import java.util.List;
import java.util.Objects;

/**
 * Derived impact of a selected entity (ADR-012). Not stored on {@link RepositoryModel}.
 * Empty categories mean no evidence; they are not guesses.
 */
public record Impact(
        String entityId,
        List<ImpactItem> callers,
        List<ImpactItem> dependents,
        List<ImpactItem> tests,
        List<ImpactItem> endpoints,
        List<ImpactItem> inferred
) {
    public Impact {
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(callers, "callers");
        Objects.requireNonNull(dependents, "dependents");
        Objects.requireNonNull(tests, "tests");
        Objects.requireNonNull(endpoints, "endpoints");
        Objects.requireNonNull(inferred, "inferred");
        if (entityId.isBlank()) {
            throw new IllegalArgumentException("entityId must not be blank");
        }
        callers = List.copyOf(callers);
        dependents = List.copyOf(dependents);
        tests = List.copyOf(tests);
        endpoints = List.copyOf(endpoints);
        inferred = List.copyOf(inferred);
    }
}

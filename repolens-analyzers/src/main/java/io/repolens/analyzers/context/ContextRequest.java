package io.repolens.analyzers.context;

import java.util.Objects;

/**
 * Request to package existing repository intelligence into an AI-ready context.
 */
public record ContextRequest(
        ContextPurpose purpose,
        ContextScope scope,
        ContextBudget budget,
        ContextFormat format,
        String title,
        ContextStrategy strategy
) {
    public ContextRequest {
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(budget, "budget");
        Objects.requireNonNull(format, "format");
        if (strategy == null) {
            strategy = ContextStrategy.ARCHITECTURE_OVERVIEW;
        }
        if (title == null || title.isBlank()) {
            title = strategy.displayName();
        }
    }

    public ContextRequest(
            ContextPurpose purpose,
            ContextScope scope,
            ContextBudget budget,
            ContextFormat format,
            String title
    ) {
        this(purpose, scope, budget, format, title, ContextStrategy.ARCHITECTURE_OVERVIEW);
    }
}

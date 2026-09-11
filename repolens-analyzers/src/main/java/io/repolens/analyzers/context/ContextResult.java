package io.repolens.analyzers.context;

import java.util.List;
import java.util.Objects;

/**
 * Result of packaging repository intelligence into a context document.
 */
public record ContextResult(
        String id,
        String title,
        ContextPurpose purpose,
        ContextScope scope,
        ContextFormat format,
        int tokenBudget,
        int estimatedTokens,
        String content,
        List<String> included,
        List<String> excluded,
        boolean tokenEstimateApproximate,
        ContextStrategy strategy
) {
    public ContextResult {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(content, "content");
        if (strategy == null) {
            strategy = ContextStrategy.ARCHITECTURE_OVERVIEW;
        }
        included = included == null ? List.of() : List.copyOf(included);
        excluded = excluded == null ? List.of() : List.copyOf(excluded);
        if (tokenBudget < 0 || estimatedTokens < 0) {
            throw new IllegalArgumentException("token counts must be >= 0");
        }
    }
}

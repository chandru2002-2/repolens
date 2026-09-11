package io.repolens.api;

import java.util.List;
import java.util.Objects;

/**
 * Context Studio generation request (v1.6). Consumes an existing analysis job.
 * Optional AI task fields produce a ready-to-copy prompt without calling an AI provider.
 */
public record ContextRequestDto(
        String purpose,
        ScopeDto scope,
        Integer tokenBudget,
        String format,
        String title,
        String aiTask,
        String customTask,
        String strategy
) {
    public ContextRequestDto {
        Objects.requireNonNull(purpose, "purpose");
        if (scope == null) {
            scope = new ScopeDto("ENTIRE_REPOSITORY", List.of(), List.of(), List.of());
        }
        if (format == null || format.isBlank()) {
            format = "markdown";
        }
    }

    /** Backward-compatible constructor without AI task fields. */
    public ContextRequestDto(
            String purpose,
            ScopeDto scope,
            Integer tokenBudget,
            String format,
            String title
    ) {
        this(purpose, scope, tokenBudget, format, title, null, null, null);
    }

    public record ScopeDto(
            String mode,
            List<String> filePaths,
            List<String> symbolIds,
            List<String> graphNodeIds
    ) {
        public ScopeDto {
            if (mode == null || mode.isBlank()) {
                mode = "ENTIRE_REPOSITORY";
            }
            filePaths = filePaths == null ? List.of() : List.copyOf(filePaths);
            symbolIds = symbolIds == null ? List.of() : List.copyOf(symbolIds);
            graphNodeIds = graphNodeIds == null ? List.of() : List.copyOf(graphNodeIds);
        }
    }
}

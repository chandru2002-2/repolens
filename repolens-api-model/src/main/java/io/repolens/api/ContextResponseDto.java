package io.repolens.api;

import java.util.List;
import java.util.Objects;

/**
 * Context Studio generation response (v1.6).
 * {@code content} is raw repository context; {@code prompt} is the AI-ready prompt when requested.
 */
public record ContextResponseDto(
        String id,
        String title,
        String purpose,
        String scopeMode,
        String format,
        int tokenBudget,
        int estimatedTokens,
        boolean tokenEstimateApproximate,
        String content,
        List<String> included,
        List<String> excluded,
        ScopeEchoDto scope,
        String aiTask,
        String taskText,
        String prompt,
        String strategy,
        String strategyLabel
) {
    public ContextResponseDto {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(scopeMode, "scopeMode");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(content, "content");
        included = included == null ? List.of() : List.copyOf(included);
        excluded = excluded == null ? List.of() : List.copyOf(excluded);
        if (scope == null) {
            scope = new ScopeEchoDto(List.of(), List.of(), List.of());
        }
    }

    public record ScopeEchoDto(
            List<String> filePaths,
            List<String> symbolIds,
            List<String> graphNodeIds
    ) {
        public ScopeEchoDto {
            filePaths = filePaths == null ? List.of() : List.copyOf(filePaths);
            symbolIds = symbolIds == null ? List.of() : List.copyOf(symbolIds);
            graphNodeIds = graphNodeIds == null ? List.of() : List.copyOf(graphNodeIds);
        }
    }
}

package io.repolens.analyzers.context;

import java.util.List;
import java.util.Objects;

/**
 * User-selected repository scope for context generation.
 */
public record ContextScope(
        ScopeMode mode,
        List<String> filePaths,
        List<String> symbolIds,
        List<String> graphNodeIds
) {
    public ContextScope {
        Objects.requireNonNull(mode, "mode");
        filePaths = filePaths == null ? List.of() : List.copyOf(filePaths);
        symbolIds = symbolIds == null ? List.of() : List.copyOf(symbolIds);
        graphNodeIds = graphNodeIds == null ? List.of() : List.copyOf(graphNodeIds);
    }

    public static ContextScope entireRepository() {
        return new ContextScope(ScopeMode.ENTIRE_REPOSITORY, List.of(), List.of(), List.of());
    }

    public enum ScopeMode {
        ENTIRE_REPOSITORY,
        SELECTED_FILES,
        SELECTED_SYMBOLS,
        SELECTED_GRAPH_NODES,
        CUSTOM
    }
}

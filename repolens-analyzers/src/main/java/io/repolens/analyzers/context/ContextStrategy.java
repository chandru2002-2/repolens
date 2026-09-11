package io.repolens.analyzers.context;

/**
 * Deterministic packaging strategy for Context Studio.
 * Try Another cycles these without changing repository, task, or scope.
 */
public enum ContextStrategy {
    ARCHITECTURE_OVERVIEW("Architecture Overview"),
    SOURCE_AND_SYMBOLS("Architecture + Source"),
    COMPACT_ARCHITECTURE("Compact Architecture"),
    ALTERNATE_PRIORITIZATION("Reordered Detail");

    private final String displayName;

    ContextStrategy(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public ContextStrategy next() {
        ContextStrategy[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public boolean includesModules() {
        return this != SOURCE_AND_SYMBOLS;
    }

    public boolean includesComponents() {
        return true;
    }

    public boolean includesRelationships() {
        return this != SOURCE_AND_SYMBOLS;
    }

    public boolean includesEndpoints() {
        return this == ARCHITECTURE_OVERVIEW || this == COMPACT_ARCHITECTURE || this == ALTERNATE_PRIORITIZATION;
    }

    public boolean includesDataModels() {
        return this == ALTERNATE_PRIORITIZATION || this == ARCHITECTURE_OVERVIEW;
    }

    public boolean includesConfiguration() {
        return this != SOURCE_AND_SYMBOLS;
    }

    public boolean includesDocumentation() {
        return this == ARCHITECTURE_OVERVIEW || this == ALTERNATE_PRIORITIZATION;
    }

    public int maxSourceFiles() {
        return switch (this) {
            case ARCHITECTURE_OVERVIEW -> 2;
            case SOURCE_AND_SYMBOLS -> 12;
            case COMPACT_ARCHITECTURE -> 0;
            case ALTERNATE_PRIORITIZATION -> 6;
        };
    }

    public int maxSnippetChars() {
        return switch (this) {
            case ARCHITECTURE_OVERVIEW -> 1_200;
            case SOURCE_AND_SYMBOLS -> 2_500;
            case COMPACT_ARCHITECTURE -> 0;
            case ALTERNATE_PRIORITIZATION -> 800;
        };
    }
}

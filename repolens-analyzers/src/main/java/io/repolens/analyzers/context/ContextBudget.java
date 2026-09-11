package io.repolens.analyzers.context;

import java.util.Objects;
import java.util.Set;

/**
 * Token budget for context packaging. Presets plus validated custom values.
 */
public final class ContextBudget {

    public static final Set<Integer> PRESETS = Set.of(2_000, 4_000, 8_000, 16_000, 32_000);
    public static final int MIN = 500;
    public static final int MAX = 200_000;

    private final int tokens;

    private ContextBudget(int tokens) {
        this.tokens = tokens;
    }

    public static ContextBudget of(int tokens) {
        if (tokens < MIN || tokens > MAX) {
            throw new IllegalArgumentException(
                    "tokenBudget must be between " + MIN + " and " + MAX + " (got " + tokens + ")");
        }
        return new ContextBudget(tokens);
    }

    public int tokens() {
        return tokens;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ContextBudget other && tokens == other.tokens;
    }

    @Override
    public int hashCode() {
        return Objects.hash(tokens);
    }
}

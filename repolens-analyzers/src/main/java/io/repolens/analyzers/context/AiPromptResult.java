package io.repolens.analyzers.context;

import java.util.Objects;

/**
 * AI prompt representation distinct from raw repository context.
 */
public record AiPromptResult(
        AiTaskPreset preset,
        String taskText,
        String prompt
) {
    public AiPromptResult {
        Objects.requireNonNull(preset, "preset");
        Objects.requireNonNull(taskText, "taskText");
        Objects.requireNonNull(prompt, "prompt");
        if (taskText.isBlank()) {
            throw new IllegalArgumentException("taskText must not be blank");
        }
        if (prompt.isBlank()) {
            throw new IllegalArgumentException("prompt must not be blank");
        }
    }
}

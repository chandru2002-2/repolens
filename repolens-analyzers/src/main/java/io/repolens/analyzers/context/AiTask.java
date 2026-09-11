package io.repolens.analyzers.context;

import java.util.Objects;

/**
 * AI task selection for prompt generation (preset and optional custom text).
 */
public record AiTask(AiTaskPreset preset, String customText) {

    public AiTask {
        Objects.requireNonNull(preset, "preset");
        customText = customText == null ? "" : customText.trim();
        if (preset == AiTaskPreset.CUSTOM && customText.isEmpty()) {
            throw new IllegalArgumentException("CUSTOM ai task requires customText");
        }
    }

    public static AiTask ofPreset(AiTaskPreset preset) {
        return new AiTask(preset, "");
    }

    public static AiTask custom(String customText) {
        return new AiTask(AiTaskPreset.CUSTOM, customText);
    }

    public String resolvedTaskText() {
        if (preset == AiTaskPreset.CUSTOM) {
            return customText;
        }
        if (!customText.isEmpty()) {
            return customText;
        }
        return preset.defaultTaskText();
    }
}

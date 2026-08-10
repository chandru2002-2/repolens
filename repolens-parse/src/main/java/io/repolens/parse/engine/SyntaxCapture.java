package io.repolens.parse.engine;

import java.util.Objects;

/**
 * One captured syntax node from a Tree-sitter query (or fallback extractor).
 */
public record SyntaxCapture(
        String name,
        String text,
        int startLine,
        int startColumn,
        int endLine,
        int endColumn
) {
    public SyntaxCapture {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(text, "text");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (startLine < 0 || startColumn < 0 || endLine < 0 || endColumn < 0) {
            throw new IllegalArgumentException("coordinates must be >= 0");
        }
    }

    public static SyntaxCapture of(String name, String text, int line) {
        return new SyntaxCapture(name, text, line, 0, line, Math.max(0, text.length()));
    }
}

package io.repolens.core.model;

import java.util.Objects;

/**
 * Source span within a file. Lines and columns are 1-based when known.
 */
public record SourceLocation(
        String filePath,
        int startLine,
        int startColumn,
        int endLine,
        int endColumn
) {
    public SourceLocation {
        Objects.requireNonNull(filePath, "filePath");
        if (filePath.isBlank()) {
            throw new IllegalArgumentException("filePath must not be blank");
        }
        if (startLine < 0 || startColumn < 0 || endLine < 0 || endColumn < 0) {
            throw new IllegalArgumentException("location coordinates must be >= 0");
        }
    }

    public static SourceLocation ofFile(String filePath) {
        return new SourceLocation(filePath, 0, 0, 0, 0);
    }
}

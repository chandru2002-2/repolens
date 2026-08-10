package io.repolens.core.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Import declaration observed in a source file.
 */
public record Import(
        String id,
        String sourceFilePath,
        String rawImport,
        Optional<String> resolvedTargetId,
        SourceLocation location
) {
    public Import {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sourceFilePath, "sourceFilePath");
        Objects.requireNonNull(rawImport, "rawImport");
        Objects.requireNonNull(resolvedTargetId, "resolvedTargetId");
        Objects.requireNonNull(location, "location");
        if (id.isBlank() || sourceFilePath.isBlank() || rawImport.isBlank()) {
            throw new IllegalArgumentException("id, sourceFilePath, and rawImport must not be blank");
        }
    }
}

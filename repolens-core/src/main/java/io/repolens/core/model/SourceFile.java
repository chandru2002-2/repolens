package io.repolens.core.model;

import java.util.Objects;
import java.util.Optional;

/**
 * A file belonging to the analyzed repository.
 */
public record SourceFile(
        String path,
        String language,
        String contentHash,
        long sizeBytes
) {
    public SourceFile {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(contentHash, "contentHash");
        if (path.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes must be >= 0");
        }
    }

    public Optional<String> languageOrEmpty() {
        return language.isBlank() ? Optional.empty() : Optional.of(language);
    }
}

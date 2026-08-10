package io.repolens.core.model;

import java.util.List;
import java.util.Objects;

/**
 * Filesystem inventory produced by ingestion. Parsing consumes this; analyzers do not.
 */
public record WorkingTreeInventory(
        List<InventoriedFile> files,
        long totalBytes,
        int skippedFileCount
) {
    public WorkingTreeInventory {
        Objects.requireNonNull(files, "files");
        if (totalBytes < 0 || skippedFileCount < 0) {
            throw new IllegalArgumentException("totals must be >= 0");
        }
        files = List.copyOf(files);
    }

    public int fileCount() {
        return files.size();
    }

    public record InventoriedFile(
            String relativePath,
            long sizeBytes
    ) {
        public InventoriedFile {
            Objects.requireNonNull(relativePath, "relativePath");
            if (relativePath.isBlank()) {
                throw new IllegalArgumentException("relativePath must not be blank");
            }
            if (sizeBytes < 0) {
                throw new IllegalArgumentException("sizeBytes must be >= 0");
            }
        }
    }
}

package io.repolens.core.model;

import java.util.List;
import java.util.Objects;

/**
 * Filesystem inventory produced by ingestion. Parsing consumes this; analyzers do not.
 */
public record WorkingTreeInventory(
        List<InventoriedFile> files,
        long totalBytes,
        int skippedFileCount,
        List<SkippedFile> skippedFiles
) {
    public WorkingTreeInventory(
            List<InventoriedFile> files,
            long totalBytes,
            int skippedFileCount
    ) {
        this(files, totalBytes, skippedFileCount, List.of());
    }

    public WorkingTreeInventory {
        Objects.requireNonNull(files, "files");
        Objects.requireNonNull(skippedFiles, "skippedFiles");
        if (totalBytes < 0 || skippedFileCount < 0) {
            throw new IllegalArgumentException("totals must be >= 0");
        }
        files = List.copyOf(files);
        skippedFiles = List.copyOf(skippedFiles);
    }

    public int fileCount() {
        return files.size();
    }

    public List<SkippedFile> skippedOversizedFiles() {
        return skippedFiles.stream()
                .filter(file -> "maxFileBytes".equals(file.reason()))
                .toList();
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

    /**
     * A path that was intentionally not inventoried for analysis.
     *
     * @param reason {@code maxFileBytes}, {@code binary}, or another ingest reason code
     * @param limitBytes limit associated with the reason (0 when not applicable)
     */
    public record SkippedFile(
            String relativePath,
            String reason,
            long sizeBytes,
            long limitBytes
    ) {
        public SkippedFile {
            Objects.requireNonNull(relativePath, "relativePath");
            Objects.requireNonNull(reason, "reason");
            if (relativePath.isBlank() || reason.isBlank()) {
                throw new IllegalArgumentException("relativePath and reason must not be blank");
            }
            if (sizeBytes < 0 || limitBytes < 0) {
                throw new IllegalArgumentException("sizes must be >= 0");
            }
        }
    }
}

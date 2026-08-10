package io.repolens.ingest;

/**
 * Configurable safety limits for repository ingestion (ADR-010 scaffolding).
 */
public record IngestLimits(
        int maxFileCount,
        long maxTotalBytes,
        long maxFileBytes,
        int maxDepth
) {
    public static final IngestLimits DEFAULT = new IngestLimits(
            50_000,
            512L * 1024L * 1024L,
            5L * 1024L * 1024L,
            64
    );

    public IngestLimits {
        if (maxFileCount <= 0) {
            throw new IllegalArgumentException("maxFileCount must be > 0");
        }
        if (maxTotalBytes <= 0 || maxFileBytes <= 0) {
            throw new IllegalArgumentException("byte limits must be > 0");
        }
        if (maxDepth <= 0) {
            throw new IllegalArgumentException("maxDepth must be > 0");
        }
    }
}

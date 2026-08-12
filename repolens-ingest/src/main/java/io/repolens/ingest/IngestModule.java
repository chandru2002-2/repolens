package io.repolens.ingest;

import io.repolens.core.ports.RepositoryMetadataCollector;

/**
 * Public entry points for the ingestion module.
 */
public final class IngestModule {
    private IngestModule() {
    }

    public static LocalRepositoryIngestor localIngestor() {
        return new LocalRepositoryIngestor();
    }

    public static LocalRepositoryIngestor localIngestor(IngestLimits limits) {
        return new LocalRepositoryIngestor(limits);
    }

    public static RepositoryMetadataCollector metadataCollector() {
        return new DefaultRepositoryMetadataCollector();
    }
}

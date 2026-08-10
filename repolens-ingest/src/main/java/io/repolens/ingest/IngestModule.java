package io.repolens.ingest;

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
}

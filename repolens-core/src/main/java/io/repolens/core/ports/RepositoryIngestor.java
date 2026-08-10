package io.repolens.core.ports;

import io.repolens.core.model.Repository;
import io.repolens.core.model.WorkingTreeInventory;

import java.nio.file.Path;

/**
 * Acquires repository content (local path or remote URL) and returns a working tree path
 * plus repository identity metadata and a filesystem inventory. Does not parse source.
 */
public interface RepositoryIngestor {

    IngestionResult ingest(IngestionRequest request);

    record IngestionRequest(
            String source,
            boolean remote
    ) {
        public IngestionRequest {
            if (source == null || source.isBlank()) {
                throw new IllegalArgumentException("source must not be blank");
            }
        }

        public static IngestionRequest local(String path) {
            return new IngestionRequest(path, false);
        }
    }

    record IngestionResult(
            Repository repository,
            Path workingTree,
            WorkingTreeInventory inventory
    ) {
        public IngestionResult {
            if (repository == null) {
                throw new IllegalArgumentException("repository must not be null");
            }
            if (workingTree == null) {
                throw new IllegalArgumentException("workingTree must not be null");
            }
            if (inventory == null) {
                throw new IllegalArgumentException("inventory must not be null");
            }
        }
    }
}

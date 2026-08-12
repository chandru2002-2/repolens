package io.repolens.core.ports;

import io.repolens.core.model.AnalysisResult;
import io.repolens.core.model.Repository;
import io.repolens.core.model.RepositoryMetadata;
import io.repolens.core.model.WorkingTreeInventory;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Collects optional repository metadata after ingestion. Must not fail analysis.
 */
public interface RepositoryMetadataCollector {

    RepositoryMetadataCollector NOOP = (repository, workingTree, inventory) ->
            new CollectionResult(RepositoryMetadata.EMPTY, Optional.empty());

    CollectionResult collect(Repository repository, Path workingTree, WorkingTreeInventory inventory);

    record CollectionResult(
            RepositoryMetadata metadata,
            Optional<AnalysisResult> notes
    ) {
        public CollectionResult {
            Objects.requireNonNull(metadata, "metadata");
            Objects.requireNonNull(notes, "notes");
        }
    }
}

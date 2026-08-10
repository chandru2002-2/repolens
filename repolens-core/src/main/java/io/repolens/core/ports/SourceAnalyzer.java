package io.repolens.core.ports;

import io.repolens.core.model.Repository;
import io.repolens.core.model.RepositoryModel;
import io.repolens.core.model.WorkingTreeInventory;

import java.nio.file.Path;

/**
 * Parses / indexes a working tree into a RepositoryModel.
 * Language-specific syntax handling belongs behind this port, not in analyzers or adapters.
 */
public interface SourceAnalyzer {

    RepositoryModel analyze(
            Repository repository,
            Path workingTree,
            WorkingTreeInventory inventory
    );
}

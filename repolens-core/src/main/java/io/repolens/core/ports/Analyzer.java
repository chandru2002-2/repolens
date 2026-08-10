package io.repolens.core.ports;

import io.repolens.core.model.AnalysisResult;
import io.repolens.core.model.RepositoryModel;

/**
 * Operates only on RepositoryModel. Must not parse source code.
 */
public interface Analyzer {

    String id();

    AnalysisResult analyze(RepositoryModel model);
}

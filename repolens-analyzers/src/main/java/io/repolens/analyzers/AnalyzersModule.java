package io.repolens.analyzers;

import io.repolens.core.ports.Analyzer;

import java.util.List;

/**
 * Factory for built-in analyzers. All analyzers operate only on RepositoryModel.
 */
public final class AnalyzersModule {
    private AnalyzersModule() {
    }

    public static List<Analyzer> defaultAnalyzers() {
        return List.of(
                new StructureAnalyzer(),
                new DependencyAnalyzer(),
                new MetricsAnalyzer()
        );
    }
}

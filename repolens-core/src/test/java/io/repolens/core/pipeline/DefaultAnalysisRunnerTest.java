package io.repolens.core.pipeline;

import io.repolens.core.model.AnalysisResult;
import io.repolens.core.model.Repository;
import io.repolens.core.model.RepositoryModel;
import io.repolens.core.model.SourceFile;
import io.repolens.core.model.WorkingTreeInventory;
import io.repolens.core.ports.AnalysisRunner;
import io.repolens.core.ports.Analyzer;
import io.repolens.core.ports.RepositoryIngestor;
import io.repolens.core.ports.SourceAnalyzer;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultAnalysisRunnerTest {

    @Test
    void runsIngestParseAnalyzePipeline() {
        RepositoryIngestor ingestor = request -> {
            Repository repository = Repository.local("r1", "demo", request.source());
            WorkingTreeInventory inventory = new WorkingTreeInventory(
                    List.of(new WorkingTreeInventory.InventoriedFile("README.md", 1)),
                    1,
                    0
            );
            return new RepositoryIngestor.IngestionResult(repository, Path.of(request.source()), inventory);
        };

        SourceAnalyzer sourceAnalyzer = (repository, workingTree, inventory) ->
                RepositoryModel.builder(repository)
                        .addFile(new SourceFile("README.md", "markdown", "x", 1))
                        .build();

        Analyzer analyzer = new Analyzer() {
            @Override
            public String id() {
                return "count-files";
            }

            @Override
            public AnalysisResult analyze(RepositoryModel model) {
                return new AnalysisResult(
                        id(),
                        "counted",
                        List.of(),
                        List.of(),
                        List.of()
                );
            }
        };

        DefaultAnalysisRunner runner = new DefaultAnalysisRunner(ingestor, sourceAnalyzer, List.of(analyzer));
        AnalysisRunner.AnalysisRunResult result =
                runner.run(new AnalysisRunner.AnalysisRequest("/tmp/demo", false));

        assertEquals(1, result.model().fileCount());
        assertEquals(1, result.results().size());
        assertEquals("count-files", result.results().getFirst().analyzerId());
    }

    @Test
    void surfacesOversizedFileSkipAsIngestWarning() {
        RepositoryIngestor ingestor = request -> {
            Repository repository = Repository.local("r1", "demo", request.source());
            WorkingTreeInventory inventory = new WorkingTreeInventory(
                    List.of(new WorkingTreeInventory.InventoriedFile("src/App.java", 40)),
                    40,
                    1,
                    List.of(new WorkingTreeInventory.SkippedFile(
                            "assets/big.gif",
                            "maxFileBytes",
                            12_011_610,
                            5L * 1024L * 1024L
                    ))
            );
            return new RepositoryIngestor.IngestionResult(repository, Path.of(request.source()), inventory);
        };

        SourceAnalyzer sourceAnalyzer = (repository, workingTree, inventory) ->
                RepositoryModel.builder(repository)
                        .addFile(new SourceFile("src/App.java", "java", "x", 40))
                        .build();

        DefaultAnalysisRunner runner = new DefaultAnalysisRunner(ingestor, sourceAnalyzer, List.of());
        AnalysisRunner.AnalysisRunResult result =
                runner.run(new AnalysisRunner.AnalysisRequest("/tmp/demo", false));

        assertEquals(1, result.model().fileCount());
        assertEquals(1, result.results().size());
        assertEquals("ingest", result.results().getFirst().analyzerId());
        assertTrue(result.results().getFirst().summary().contains("Skipped 1 file"));
        assertTrue(result.results().getFirst().summary().contains("5 MB"));
        assertTrue(result.results().getFirst().findings().stream()
                .anyMatch(f -> "warning".equals(f.severity()) && f.message().contains("big.gif")));
    }
}

package io.repolens.cli;

import io.repolens.analyzers.AnalyzersModule;
import io.repolens.analyzers.GraphViewProjector;
import io.repolens.api.AnalysisResponseMapper;
import io.repolens.core.pipeline.DefaultAnalysisRunner;
import io.repolens.core.ports.AnalysisRunner;
import io.repolens.ingest.IngestLimits;
import io.repolens.ingest.IngestModule;
import io.repolens.parse.ParseModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Oversized / binary asset handling through the shared analysis pipeline.
 */
class OversizedFileAnalysisTest {

    @TempDir
    Path tempDir;

    @Test
    void analysisContinuesWhenRepositoryContainsOversizedAsset() throws Exception {
        Files.writeString(tempDir.resolve("Hello.java"), "public class Hello {}");
        Files.write(tempDir.resolve("assets-minimax.gif"), new byte[12_000]);

        // maxFileCount, maxTotalBytes, maxFileBytes (5 KB), maxDepth
        IngestLimits limits = new IngestLimits(1_000, 64L * 1024L * 1024L, 5_000L, 32);
        DefaultAnalysisRunner runner = new DefaultAnalysisRunner(
                IngestModule.localIngestor(limits),
                ParseModule.sourceAnalyzer(),
                AnalyzersModule.defaultAnalyzers()
        );

        AnalysisRunner.AnalysisRunResult run =
                runner.run(new AnalysisRunner.AnalysisRequest(tempDir.toString(), false));

        assertTrue(run.model().fileCount() >= 1);
        assertTrue(run.model().files().stream().anyMatch(f -> f.path().equals("Hello.java")));
        assertTrue(run.model().files().stream().noneMatch(f -> f.path().endsWith(".gif")));
        assertTrue(run.results().stream().anyMatch(r -> r.analyzerId().equals("ingest")));
        assertTrue(run.results().stream()
                .filter(r -> r.analyzerId().equals("ingest"))
                .flatMap(r -> r.findings().stream())
                .anyMatch(f -> "warning".equals(f.severity()) && f.message().contains("Skipped")));

        var dto = AnalysisResponseMapper.from(
                run.model(),
                run.results(),
                GraphViewProjector.project(run.model(), run.results())
        );
        assertEquals("v1", dto.schemaVersion());
        assertTrue(dto.results().stream().anyMatch(r -> "ingest".equals(r.analyzerId())));
    }
}

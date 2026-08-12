package io.repolens.ingest;

import io.repolens.core.ports.IngestionException;
import io.repolens.core.ports.RepositoryIngestor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalRepositoryIngestorTest {

    @TempDir
    Path tempDir;

    @Test
    void inventoriesLocalFilesAndSkipsIgnoredPaths() throws Exception {
        Files.writeString(tempDir.resolve("README.md"), "hello");
        Files.createDirectories(tempDir.resolve("src"));
        Files.writeString(tempDir.resolve("src/Main.java"), "class Main {}");
        Files.createDirectories(tempDir.resolve("node_modules/pkg"));
        Files.writeString(tempDir.resolve("node_modules/pkg/index.js"), "module.exports = 1");
        Files.writeString(tempDir.resolve(".gitignore"), "*.tmp\n");
        Files.writeString(tempDir.resolve("scratch.tmp"), "tmp");

        RepositoryIngestor.IngestionResult result =
                IngestModule.localIngestor().ingest(RepositoryIngestor.IngestionRequest.local(tempDir.toString()));

        assertEquals(tempDir.toAbsolutePath().normalize(), result.workingTree());
        assertEquals(3, result.inventory().fileCount());
        assertTrue(result.inventory().files().stream().anyMatch(f -> f.relativePath().equals("README.md")));
        assertTrue(result.inventory().files().stream().anyMatch(f -> f.relativePath().equals("src/Main.java")));
        assertTrue(result.inventory().files().stream().anyMatch(f -> f.relativePath().equals(".gitignore")));
        assertTrue(result.inventory().files().stream().noneMatch(f -> f.relativePath().contains("node_modules")));
        assertTrue(result.inventory().files().stream().noneMatch(f -> f.relativePath().endsWith(".tmp")));
        assertTrue(result.inventory().skippedFileCount() >= 1);
    }

    @Test
    void normalizesGithubUrlsAndRejectsOthers() {
        assertThrows(IngestionException.class, () ->
                IngestModule.localIngestor().ingest(
                        new RepositoryIngestor.IngestionRequest("https://gitlab.com/acme/demo", true)));
    }

    @Test
    void parsesAllowlistedGithubUrl() {
        var remote = RemoteRepositoryUrls.normalizeGithubHttps("https://github.com/octocat/Hello-World");
        assertEquals("octocat/Hello-World", remote.fullName());
        assertEquals("https://github.com/octocat/Hello-World.git", remote.cloneUrl());
    }

    @Test
    void skipsOversizedFilesAndContinuesInventory() throws Exception {
        Files.writeString(tempDir.resolve("ok.java"), "class Ok {}");
        Files.write(tempDir.resolve("huge.bin"), new byte[2_048]);

        IngestLimits limits = new IngestLimits(50, 1024 * 1024, 1024, 16);
        LocalRepositoryIngestor ingestor = IngestModule.localIngestor(limits);

        RepositoryIngestor.IngestionResult result =
                ingestor.ingest(RepositoryIngestor.IngestionRequest.local(tempDir.toString()));

        assertTrue(result.inventory().files().stream().anyMatch(f -> f.relativePath().equals("ok.java")));
        assertTrue(result.inventory().files().stream().noneMatch(f -> f.relativePath().equals("huge.bin")));
        assertEquals(1, result.inventory().skippedOversizedFiles().size());
        assertEquals("huge.bin", result.inventory().skippedOversizedFiles().getFirst().relativePath());
        assertEquals("maxFileBytes", result.inventory().skippedOversizedFiles().getFirst().reason());
        assertEquals(1024, result.inventory().skippedOversizedFiles().getFirst().limitBytes());
    }

    @Test
    void skipsSmallBinaryAssetsWithoutFailing() throws Exception {
        Files.writeString(tempDir.resolve("App.java"), "class App {}");
        Files.write(tempDir.resolve("logo.png"), new byte[] {1, 2, 3, 4});

        RepositoryIngestor.IngestionResult result =
                IngestModule.localIngestor().ingest(RepositoryIngestor.IngestionRequest.local(tempDir.toString()));

        assertTrue(result.inventory().files().stream().anyMatch(f -> f.relativePath().equals("App.java")));
        assertTrue(result.inventory().files().stream().noneMatch(f -> f.relativePath().equals("logo.png")));
        assertTrue(result.inventory().skippedOversizedFiles().isEmpty());
        assertTrue(result.inventory().skippedFileCount() >= 1);
    }

    @Test
    void enforcesMaxFileCount() throws Exception {
        Files.writeString(tempDir.resolve("a.txt"), "a");
        Files.writeString(tempDir.resolve("b.txt"), "b");

        IngestLimits limits = new IngestLimits(1, 1024, 1024, 16);
        LocalRepositoryIngestor ingestor = IngestModule.localIngestor(limits);

        assertThrows(IngestionException.class, () ->
                ingestor.ingest(RepositoryIngestor.IngestionRequest.local(tempDir.toString())));
    }

    @Test
    void rejectsMissingPath() {
        assertThrows(IngestionException.class, () ->
                IngestModule.localIngestor().ingest(
                        RepositoryIngestor.IngestionRequest.local(tempDir.resolve("missing").toString())));
    }
}

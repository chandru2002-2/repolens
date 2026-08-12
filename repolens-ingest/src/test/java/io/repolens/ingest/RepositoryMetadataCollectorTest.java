package io.repolens.ingest;

import io.repolens.core.model.CommitInfo;
import io.repolens.core.model.Repository;
import io.repolens.core.model.RepositoryMetadata;
import io.repolens.core.model.WorkingTreeInventory;
import io.repolens.core.ports.RepositoryMetadataCollector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpServer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryMetadataCollectorTest {

    @TempDir
    Path tempDir;

    @Test
    void localRepositoryMetadataIncludesSizeBranchAndCommits() throws Exception {
        Path repo = tempDir.resolve("demo");
        Files.createDirectories(repo);
        Files.writeString(repo.resolve("App.java"), "class App {}");
        run(repo, "git", "init");
        run(repo, "git", "config", "user.email", "dev@example.com");
        run(repo, "git", "config", "user.name", "Dev Example");
        run(repo, "git", "add", "App.java");
        run(repo, "git", "commit", "-m", "initial commit");
        Files.writeString(repo.resolve("App.java"), "class App { int x; }");
        run(repo, "git", "add", "App.java");
        run(repo, "git", "commit", "-m", "second commit");

        WorkingTreeInventory inventory = new WorkingTreeInventory(
                List.of(new WorkingTreeInventory.InventoriedFile("App.java", 42)),
                42,
                0
        );

        RepositoryMetadataCollector.CollectionResult result =
                new DefaultRepositoryMetadataCollector().collect(
                        Repository.local("local:demo", "demo", repo.toString()),
                        repo,
                        inventory
                );

        RepositoryMetadata metadata = result.metadata();
        assertEquals(42L, metadata.sizeBytes().orElse(-1L));
        assertTrue(metadata.defaultBranch().isPresent());
        assertTrue(metadata.lastCommit().isPresent());
        assertEquals("second commit", metadata.lastCommit().flatMap(CommitInfo::message).orElse(""));
        assertEquals("Dev Example", metadata.lastCommit().flatMap(CommitInfo::author).orElse(""));
        assertTrue(metadata.firstCommitAt().isPresent());
        assertTrue(metadata.commitCount().orElse(0) >= 2);
        assertTrue(result.notes().isEmpty());
    }

    @Test
    void missingGitMetadataIsOmittedGracefully() {
        Path bare = tempDir.resolve("nogit");
        try {
            Files.createDirectories(bare);
            Files.writeString(bare.resolve("a.txt"), "hi");
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
        WorkingTreeInventory inventory = new WorkingTreeInventory(
                List.of(new WorkingTreeInventory.InventoriedFile("a.txt", 2)),
                2,
                0
        );
        RepositoryMetadata metadata = new DefaultRepositoryMetadataCollector().collect(
                Repository.local("local:nogit", "nogit", bare.toString()),
                bare,
                inventory
        ).metadata();

        assertEquals(2L, metadata.sizeBytes().orElse(-1L));
        assertTrue(metadata.lastCommit().isEmpty());
        assertTrue(metadata.defaultBranch().isEmpty());
        assertTrue(metadata.firstCommitAt().isEmpty());
        assertTrue(metadata.commitCount().isEmpty());
    }

    @Test
    void optionalEmptyMetadataRemainsEmpty() {
        assertTrue(RepositoryMetadata.EMPTY.isEmpty());
    }

    @Test
    void githubMetadataParsingViaLocalHttpServer() throws Exception {
        String repoJson = """
                {
                  "name": "RepoLens",
                  "size": 18,
                  "created_at": "2026-01-14T10:00:00Z",
                  "default_branch": "main",
                  "owner": { "login": "chandru2002-2" }
                }
                """;
        String commitsJson = """
                [{
                  "sha": "abc123",
                  "commit": {
                    "message": "Add documentation-aware inspection",
                    "author": { "name": "Chandru M", "date": "2026-08-11T05:00:00Z" },
                    "committer": { "name": "Chandru M", "date": "2026-08-11T05:01:00Z" }
                  }
                }]
                """;

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/repos/chandru2002-2/RepoLens", exchange -> {
            byte[] bytes = repoJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.createContext("/repos/chandru2002-2/RepoLens/commits", exchange -> {
            byte[] bytes = commitsJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
        try {
            URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            GithubMetadataClient client = new GithubMetadataClient(
                    HttpClient.newHttpClient(),
                    base,
                    Duration.ofSeconds(3)
            );
            RepositoryMetadata metadata = client.fetchRepository("chandru2002-2", "RepoLens");
            assertEquals("RepoLens", metadata.name().orElse(""));
            assertEquals("chandru2002-2", metadata.owner().orElse(""));
            assertEquals(18L * 1024L, metadata.sizeBytes().orElse(-1L));
            assertEquals(Instant.parse("2026-01-14T10:00:00Z"), metadata.createdAt().orElse(null));
            assertEquals("main", metadata.defaultBranch().orElse(""));

            CommitInfo commit = client.fetchLatestCommit("chandru2002-2", "RepoLens", "main").orElseThrow();
            assertEquals("abc123", commit.sha().orElse(""));
            assertEquals("Add documentation-aware inspection", commit.message().orElse(""));
            assertEquals("Chandru M", commit.author().orElse(""));
            assertEquals(Instant.parse("2026-08-11T05:00:00Z"), commit.authoredAt().orElse(null));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void githubFailureProducesNonBlockingWarningAndKeepsInventorySize() throws Exception {
        Path repo = tempDir.resolve("clone");
        Files.createDirectories(repo);
        Files.writeString(repo.resolve("App.java"), "class App {}");
        run(repo, "git", "init");
        run(repo, "git", "config", "user.email", "dev@example.com");
        run(repo, "git", "config", "user.name", "Dev Example");
        run(repo, "git", "add", "App.java");
        run(repo, "git", "commit", "-m", "clone tip");

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
        try {
            URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            DefaultRepositoryMetadataCollector collector = new DefaultRepositoryMetadataCollector(
                    new GitMetadataReader(),
                    new GithubMetadataClient(HttpClient.newHttpClient(), base, Duration.ofSeconds(2))
            );
            WorkingTreeInventory inventory = new WorkingTreeInventory(
                    List.of(new WorkingTreeInventory.InventoriedFile("App.java", 12)),
                    12,
                    0
            );
            RepositoryMetadataCollector.CollectionResult result = collector.collect(
                    Repository.remote("remote:owner/repo", "repo", "https://github.com/owner/repo.git"),
                    repo,
                    inventory
            );
            assertTrue(result.notes().isPresent());
            assertEquals("metadata", result.notes().get().analyzerId());
            assertFalse(result.metadata().lastCommit().isEmpty());
            assertEquals(12L, result.metadata().sizeBytes().orElse(-1L));
        } finally {
            server.stop(0);
        }
    }

    private static void run(Path cwd, String... command) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(cwd.toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = process.waitFor();
        if (code != 0) {
            throw new IllegalStateException("Command failed (" + code + "): " + String.join(" ", command) + "\n" + output);
        }
    }
}

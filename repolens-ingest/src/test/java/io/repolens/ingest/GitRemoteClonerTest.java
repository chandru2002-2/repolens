package io.repolens.ingest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitRemoteClonerTest {

    @TempDir
    Path tempDir;

    @Test
    void serializesSameRepositoryAndReusesExistingClone() throws Exception {
        AtomicInteger clones = new AtomicInteger();
        GitRemoteCloner cloner = new GitRemoteCloner(Duration.ofSeconds(5), command -> {
            clones.incrementAndGet();
            Path target = Path.of(command.get(command.size() - 1));
            Files.createDirectories(target.resolve(".git"));
            Files.writeString(target.resolve(".git/HEAD"), "ref: refs/heads/main\n");
            return new GitRemoteCloner.GitProcess.Result(0, "ok");
        });
        var remote = RemoteRepositoryUrls.normalizeGithubHttps("https://github.com/octocat/Hello-World");

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        List<Path> results = Collections.synchronizedList(new ArrayList<>());
        Runnable task = () -> {
            try {
                start.await(2, TimeUnit.SECONDS);
                results.add(cloner.cloneTo(remote, tempDir));
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            } finally {
                done.countDown();
            }
        };
        Thread a = new Thread(task);
        Thread b = new Thread(task);
        a.start();
        b.start();
        start.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals(1, clones.get());
        assertEquals(2, results.size());
        assertEquals(results.get(0), results.get(1));
        assertTrue(GitRemoteCloner.isUsableClone(results.get(0)));
    }

    @Test
    void sanitizesCloneFailureOutput() {
        GitRemoteCloner cloner = new GitRemoteCloner(Duration.ofSeconds(5), command ->
                new GitRemoteCloner.GitProcess.Result(
                        128,
                        "fatal: destination path '/Users/me/.repolens/cache/remotes/octocat/Hello-World' already exists"
                ));
        var remote = RemoteRepositoryUrls.normalizeGithubHttps("https://github.com/octocat/Hello-World");
        var ex = org.junit.jupiter.api.Assertions.assertThrows(
                io.repolens.core.ports.IngestionException.class,
                () -> cloner.cloneTo(remote, tempDir));
        assertFalse(ex.getMessage().contains("/Users/"));
        assertFalse(ex.getMessage().contains(".repolens/cache"));
    }
}

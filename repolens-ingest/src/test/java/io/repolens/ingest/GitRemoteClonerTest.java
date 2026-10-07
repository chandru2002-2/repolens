package io.repolens.ingest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
    void keepsSanitizedRepositoryCachePathInsideWorkspace() throws Exception {
        AtomicReference<List<String>> commandSeen = new AtomicReference<>();
        GitRemoteCloner cloner = new GitRemoteCloner(Duration.ofSeconds(5), command -> {
            commandSeen.set(List.copyOf(command));
            Path target = Path.of(command.getLast());
            Files.createDirectories(target.resolve(".git"));
            Files.writeString(target.resolve(".git/HEAD"), "ref: refs/heads/main\n");
            return new GitRemoteCloner.GitProcess.Result(0, "ok");
        });
        var remote = new RemoteRepositoryUrls.NormalizedRemote(
                "https://github.com/../repo.git",
                "../repo",
                "repo"
        );
        Path cache = tempDir.resolve("cache").toAbsolutePath().normalize();

        Path result = cloner.cloneTo(remote, cache);

        assertTrue(result.toAbsolutePath().normalize().startsWith(cache));
        assertEquals("git", commandSeen.get().getFirst());
        assertEquals("https://github.com/../repo.git", commandSeen.get().get(5));
        assertFalse(commandSeen.get().contains("sh"));
    }

    @Test
    void sanitizesCloneFailureOutput() throws Exception {
        GitRemoteCloner cloner = new GitRemoteCloner(Duration.ofSeconds(5), command ->
                new GitRemoteCloner.GitProcess.Result(
                        128,
                        "fatal: destination path '/Users/me/.repolens/cache/remotes/octocat/Hello-World' already exists"
                ));
        var remote = RemoteRepositoryUrls.normalizeGithubHttps("https://github.com/octocat/Hello-World");
        var ex = org.junit.jupiter.api.Assertions.assertThrows(
                io.repolens.core.ports.IngestionException.class,
            () -> cloner.cloneTo(remote, tempDir.resolve("cache")));
        assertFalse(ex.getMessage().contains("/Users/"));
        assertFalse(ex.getMessage().contains(".repolens/cache"));
        try (var entries = Files.list(tempDir.resolve("cache/octocat"))) {
            assertEquals(0, entries.count(), "failed clone staging directories must be removed");
        }
    }

    @Test
    void timeoutKillsHangingProcessEvenWhileOutputPipeNeverCloses() throws Exception {
        Duration timeout = Duration.ofMillis(80);
        IngestLimits limits = new IngestLimits(10, 1024, 512, 8);
        FakeProcess process = new FakeProcess(new BlockingInputStream(), false, true, 0);
        AtomicReference<List<String>> commandSeen = new AtomicReference<>();
        GitRemoteCloner.GitProcess runner = GitRemoteCloner.processRunner(
                timeout,
                limits,
                command -> {
                    commandSeen.set(List.copyOf(command));
                    return process;
                }
        );
        GitRemoteCloner cloner = new GitRemoteCloner(timeout, limits, runner);
        var remote = RemoteRepositoryUrls.normalizeGithubHttps("https://github.com/octocat/Hello-World");

        var ex = org.junit.jupiter.api.Assertions.assertThrows(
                io.repolens.core.ports.IngestionException.class,
                () -> cloner.cloneTo(remote, tempDir.resolve("cache")));

        assertTrue(ex.getMessage().contains("Could not clone"));
        assertTrue(process.gracefulDestroyCalls > 0);
        assertTrue(process.forceDestroyCalls > 0);
        assertFalse(process.isAlive());
        assertEquals("https://github.com/octocat/Hello-World.git", commandSeen.get().get(5));
        assertTrue(Files.exists(tempDir.resolve("cache/octocat")));
        try (var entries = Files.list(tempDir.resolve("cache/octocat"))) {
            assertEquals(0, entries.count(), "failed staging clones must be removed");
        }
    }

        @Test
        void wallClockTimeoutTerminatesRealProcessWhileOutputIsDrained() throws Exception {
        AtomicReference<Process> child = new AtomicReference<>();
            String helperClassPath = Path.of(
                HangingGitProcess.class.getProtectionDomain().getCodeSource().getLocation().toURI()
            ).toString();
        GitRemoteCloner.GitProcess runner = GitRemoteCloner.processRunner(
            Duration.ofMillis(300),
            IngestLimits.DEFAULT,
            command -> {
                String executable = Path.of(
                    System.getProperty("java.home"),
                    "bin",
                    System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java"
                ).toString();
                Process process = new ProcessBuilder(
                    executable,
                    "-cp",
                        helperClassPath,
                    HangingGitProcess.class.getName()
                ).redirectErrorStream(true).start();
                child.set(process);
                return process;
            }
        );
        long started = System.nanoTime();

        GitRemoteCloner.GitProcess.Result result = runner.run(List.of(
            "git", "clone", "--depth", "1", "--single-branch",
            "https://github.com/octocat/Hello-World.git", tempDir.toString()
        ));

        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        assertEquals(124, result.exitCode());
        assertTrue(result.output().startsWith("Git clone timed out"));
        assertTrue(elapsedMillis < 3_000, "timeout must not wait for the subprocess output stream to close");
        assertFalse(child.get().isAlive());
        }

    @Test
    void capturesOnlyBoundedProcessOutput() throws Exception {
        byte[] output = new byte[16 * 1024];
        java.util.Arrays.fill(output, (byte) 'x');
        FakeProcess process = new FakeProcess(new ByteArrayInputStream(output), true, false, 0);
        GitRemoteCloner.GitProcess runner = GitRemoteCloner.processRunner(
                Duration.ofSeconds(2),
                IngestLimits.DEFAULT,
                command -> process
        );

        GitRemoteCloner.GitProcess.Result result = runner.run(List.of(
                "git", "clone", "--depth", "1", "--single-branch",
                "https://github.com/octocat/Hello-World.git", tempDir.toString()
        ));

        assertEquals(0, result.exitCode());
        assertTrue(result.output().length() <= 4 * 1024 + 32);
        assertTrue(result.output().endsWith("[output truncated]"));
    }

    @Test
    void stopsCloneDuringOperationWhenCloneDiskBudgetIsExceeded() throws Exception {
        IngestLimits limits = new IngestLimits(10, 32, 32, 8);
        FakeProcess process = new FakeProcess(new BlockingInputStream(), false, false, 0);
        GitRemoteCloner.GitProcess runner = GitRemoteCloner.processRunner(
                Duration.ofSeconds(5),
                limits,
                command -> {
                    Path staging = Path.of(command.getLast());
                    Files.createDirectories(staging.resolve(".git/objects"));
                    Files.write(staging.resolve(".git/objects/pack"), new byte[(int) limits.maxCloneBytes() + 1]);
                    return process;
                }
        );
        GitRemoteCloner cloner = new GitRemoteCloner(Duration.ofSeconds(5), limits, runner);
        var remote = RemoteRepositoryUrls.normalizeGithubHttps("https://github.com/octocat/Hello-World");

        var ex = org.junit.jupiter.api.Assertions.assertThrows(
                io.repolens.core.ports.IngestionException.class,
                () -> cloner.cloneTo(remote, tempDir.resolve("cache")));

        assertTrue(ex.getMessage().contains("maxCloneBytes"));
        assertTrue(process.destroyed);
        assertFalse(process.isAlive());
        try (var entries = Files.list(tempDir.resolve("cache/octocat"))) {
            assertEquals(0, entries.count(), "oversized staging clones must be removed");
        }
    }

    @Test
    void stopsCloneWhenPhysicalEntryBudgetIsExceeded() throws Exception {
        IngestLimits limits = new IngestLimits(1, 1024, 512, 8);
        FakeProcess process = new FakeProcess(new BlockingInputStream(), false, false, 0);
        GitRemoteCloner.GitProcess runner = GitRemoteCloner.processRunner(
                Duration.ofSeconds(5),
                limits,
                command -> {
                    Path staging = Path.of(command.getLast());
                    Files.createDirectories(staging.resolve(".git"));
                    Files.writeString(staging.resolve(".git/HEAD"), "ref: refs/heads/main\n");
                    Files.createFile(staging.resolve("empty-a"));
                    Files.createFile(staging.resolve("empty-b"));
                    return process;
                }
        );
        GitRemoteCloner cloner = new GitRemoteCloner(Duration.ofSeconds(5), limits, runner);
        var remote = RemoteRepositoryUrls.normalizeGithubHttps("https://github.com/octocat/Hello-World");

        var ex = org.junit.jupiter.api.Assertions.assertThrows(
                io.repolens.core.ports.IngestionException.class,
                () -> cloner.cloneTo(remote, tempDir.resolve("cache")));

        assertTrue(ex.getMessage().contains("maxCloneEntryCount"));
        assertTrue(process.destroyed);
        assertFalse(process.isAlive());
        try (var entries = Files.list(tempDir.resolve("cache/octocat"))) {
            assertEquals(0, entries.count(), "over-budget staging clones must be removed");
        }
    }

    @Test
    void rejectsOversizedCachedCloneWithoutStartingAnotherGitProcess() throws Exception {
        IngestLimits limits = new IngestLimits(10, 32, 32, 8);
        GitRemoteCloner cloner = new GitRemoteCloner(Duration.ofSeconds(2), limits, command -> {
            throw new AssertionError("an oversized cached clone must not be reused or recloned");
        });
        Path cached = tempDir.resolve("cache/octocat/Hello-World");
        Files.createDirectories(cached.resolve(".git/objects"));
        Files.write(cached.resolve(".git/objects/pack"), new byte[(int) limits.maxCloneBytes() + 1]);
        var remote = RemoteRepositoryUrls.normalizeGithubHttps("https://github.com/octocat/Hello-World");

        var ex = org.junit.jupiter.api.Assertions.assertThrows(
                io.repolens.core.ports.IngestionException.class,
                () -> cloner.cloneTo(remote, tempDir.resolve("cache")));

        assertTrue(ex.getMessage().contains("Cached repository exceeds configured resource limits"));
    }

    private static final class FakeProcess extends Process {
        private final InputStream input;
        private final boolean completesOnWait;
        private final boolean ignoresGracefulDestroy;
        private final int exitCode;
        private final OutputStream output = new ByteArrayOutputStream();
        private volatile boolean alive = true;
        private volatile boolean destroyed;
        private volatile int gracefulDestroyCalls;
        private volatile int forceDestroyCalls;

        private FakeProcess(InputStream input, boolean completesOnWait, boolean ignoresGracefulDestroy, int exitCode) {
            this.input = input;
            this.completesOnWait = completesOnWait;
            this.ignoresGracefulDestroy = ignoresGracefulDestroy;
            this.exitCode = exitCode;
        }

        @Override
        public OutputStream getOutputStream() {
            return output;
        }

        @Override
        public InputStream getInputStream() {
            return input;
        }

        @Override
        public InputStream getErrorStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public int waitFor() throws InterruptedException {
            while (alive) {
                Thread.sleep(5);
            }
            return exitCode;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
            if (completesOnWait) {
                finish();
                return true;
            }
            if (alive) {
                unit.sleep(timeout);
            }
            return !alive;
        }

        @Override
        public int exitValue() {
            if (alive) {
                throw new IllegalThreadStateException("process is still running");
            }
            return exitCode;
        }

        @Override
        public void destroy() {
            gracefulDestroyCalls++;
            destroyed = true;
            if (!ignoresGracefulDestroy) {
                finish();
            }
        }

        @Override
        public Process destroyForcibly() {
            forceDestroyCalls++;
            destroyed = true;
            finish();
            return this;
        }

        @Override
        public boolean isAlive() {
            return alive;
        }

        @Override
        public ProcessHandle toHandle() {
            throw new UnsupportedOperationException("fake process has no operating-system handle");
        }

        private void finish() {
            alive = false;
            try {
                input.close();
            } catch (IOException ignored) {
                // Test process teardown.
            }
        }
    }

    private static final class BlockingInputStream extends InputStream {
        private boolean closed;

        @Override
        public synchronized int read() throws IOException {
            while (!closed) {
                try {
                    wait();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IOException("output reader interrupted", ex);
                }
            }
            return -1;
        }

        @Override
        public synchronized void close() {
            closed = true;
            notifyAll();
        }
    }

    public static final class HangingGitProcess {
        private HangingGitProcess() {
        }

        public static void main(String[] args) throws InterruptedException {
            for (int index = 0; index < 256; index++) {
                System.out.println("x".repeat(1024));
            }
            System.out.flush();
            while (true) {
                Thread.sleep(1_000);
            }
        }
    }
}

package io.repolens.ingest;

import io.repolens.core.ports.IngestionException;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Shallow-clones allowlisted remote repositories into an isolated workspace.
 * Clones of the same repository are serialized and reused when {@code .git}
 * already exists. Reuse does <em>not</em> fetch or pull: a previously cloned
 * working tree is analyzed as-is, including any local files added after clone.
 * Delete the cache directory to force a fresh clone. Freshness is a known
 * limitation, not a guarantee.
 */
public final class GitRemoteCloner {

    private static final int OUTPUT_LIMIT_BYTES = 4 * 1024;
    private static final long MONITOR_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(100);
    private static final Object[] CLONE_LOCKS = newLocks(64);

    @FunctionalInterface
    public interface GitProcess {
        Result run(List<String> command) throws IOException, InterruptedException;

        record Result(int exitCode, String output) {
        }
    }

    @FunctionalInterface
    interface ProcessStarter {
        Process start(List<String> command) throws IOException;
    }

    private final Duration timeout;
    private final IngestLimits limits;
    private final GitProcess gitProcess;

    public GitRemoteCloner() {
        this(Duration.ofMinutes(2), IngestLimits.DEFAULT);
    }

    public GitRemoteCloner(Duration timeout) {
        this(timeout, IngestLimits.DEFAULT);
    }

    public GitRemoteCloner(Duration timeout, IngestLimits limits) {
        this(timeout, limits, processRunner(timeout, limits, command ->
                new ProcessBuilder(command).redirectErrorStream(true).start()));
    }

    public GitRemoteCloner(Duration timeout, GitProcess gitProcess) {
        this(timeout, IngestLimits.DEFAULT, gitProcess);
    }

    GitRemoteCloner(Duration timeout, IngestLimits limits, GitProcess gitProcess) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        this.timeout = timeout;
        this.limits = java.util.Objects.requireNonNull(limits, "limits");
        this.gitProcess = java.util.Objects.requireNonNull(gitProcess, "gitProcess");
    }

    public Path cloneTo(RemoteRepositoryUrls.NormalizedRemote remote, Path workspaceRoot) {
        String cacheKey = cacheKey(remote.fullName());
        Object lock = CLONE_LOCKS[Math.floorMod(cacheKey.hashCode(), CLONE_LOCKS.length)];
        synchronized (lock) {
            Path staging = null;
            try {
                Files.createDirectories(workspaceRoot);
                Path target = workspaceRoot.resolve(cacheKey);
                if (isUsableClone(target)) {
                    try {
                        new CloneResourceMonitor(target, limits).check(System.nanoTime() + timeout.toNanos());
                    } catch (CloneTimeoutException | CloneLimitExceededException ex) {
                        throw new IngestionException(
                                UserFacingErrors.sanitize("Cached repository exceeds configured resource limits"), ex);
                    }
                    // Existing .git is sufficient; no fetch/pull (stale cache is possible).
                    return target;
                }
                if (Files.exists(target)) {
                    deleteRecursive(target);
                }
                Files.createDirectories(target.getParent());
                staging = Files.createTempDirectory(target.getParent(), target.getFileName() + "-clone-");

                List<String> command = List.of(
                        "git", "clone", "--depth", "1", "--single-branch",
                        remote.cloneUrl(),
                        staging.toString()
                );
                GitProcess.Result result = gitProcess.run(command);
                if (result.exitCode() != 0) {
                    throw new IngestionException(UserFacingErrors.sanitize(
                            "Could not clone " + remote.cloneUrl() + ": " + trim(result.output())));
                }
                if (!isUsableClone(staging)) {
                    throw new IngestionException("Git clone completed without producing a repository");
                }
                if (Files.exists(target)) {
                    deleteRecursive(target);
                }
                try {
                    Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                    Files.move(staging, target);
                }
                staging = null;
                return target;
            } catch (IngestionException ex) {
                throw ex;
            } catch (IOException | InterruptedException ex) {
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw new IngestionException(
                        UserFacingErrors.sanitize("Failed to clone " + remote.cloneUrl()), ex);
            } finally {
                if (staging != null) {
                    deleteQuietly(staging);
                }
            }
        }
    }

    static GitProcess processRunner(Duration timeout, IngestLimits limits, ProcessStarter starter) {
        return command -> runBoundedProcess(command, timeout, limits, starter);
    }

    private static GitProcess.Result runBoundedProcess(
            List<String> command,
            Duration timeout,
            IngestLimits limits,
            ProcessStarter starter
    ) throws IOException, InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        Process process = starter.start(command);
        BoundedOutput output = new BoundedOutput(OUTPUT_LIMIT_BYTES);
        Thread outputReader = new Thread(() -> drainOutput(process.getInputStream(), output), "repolens-git-output");
        outputReader.setDaemon(true);
        outputReader.start();
        Set<ProcessHandle> descendants = new HashSet<>();
        Path cloneDirectory = Path.of(command.getLast());
        CloneResourceMonitor resourceMonitor = new CloneResourceMonitor(cloneDirectory, limits);

        try {
            while (true) {
                collectDescendants(process, descendants);
                resourceMonitor.check(deadline);
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    throw new CloneTimeoutException();
                }
                long waitNanos = Math.min(remaining, MONITOR_INTERVAL_NANOS);
                if (process.waitFor(waitNanos, TimeUnit.NANOSECONDS)) {
                    collectDescendants(process, descendants);
                    resourceMonitor.check(deadline);
                    terminateRemainingDescendants(descendants);
                    joinOutputReader(outputReader, process);
                    return new GitProcess.Result(process.exitValue(), output.asString());
                }
            }
        } catch (CloneTimeoutException ex) {
            terminateProcess(process, descendants);
            return new GitProcess.Result(124, "Git clone timed out. " + output.asString());
        } catch (CloneLimitExceededException ex) {
            terminateProcess(process, descendants);
            return new GitProcess.Result(125, ex.getMessage() + ". " + output.asString());
        } catch (InterruptedException ex) {
            terminateProcess(process, descendants);
            Thread.currentThread().interrupt();
            throw ex;
        } catch (IOException ex) {
            terminateProcess(process, descendants);
            throw ex;
        } finally {
            closeProcessStreams(process);
            joinOutputReader(outputReader, process);
        }
    }

    private static final class CloneResourceMonitor {
        private final Path root;
        private final IngestLimits limits;

        private CloneResourceMonitor(Path root, IngestLimits limits) {
            this.root = root;
            this.limits = limits;
        }

        private void check(long deadline)
                throws IOException, CloneTimeoutException, CloneLimitExceededException {
            if (!Files.exists(root)) {
                return;
            }
            long[] cloneBytes = {0};
            int[] cloneFiles = {0};
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                        throws IOException {
                    checkDeadline(deadline);
                    if (attrs.isSymbolicLink()) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (!dir.equals(root) && ++cloneFiles[0] > limits.maxCloneEntryCount()) {
                        throw new CloneLimitExceededException("Remote clone exceeds maxCloneEntryCount");
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    checkDeadline(deadline);
                    if (attrs.isSymbolicLink() || attrs.isRegularFile()) {
                        cloneFiles[0]++;
                        if (cloneFiles[0] > limits.maxCloneEntryCount()) {
                            throw new CloneLimitExceededException("Remote clone exceeds maxCloneEntryCount");
                        }
                    }
                    if (!attrs.isRegularFile() || attrs.isSymbolicLink()) {
                        return FileVisitResult.CONTINUE;
                    }
                    long size = attrs.size();
                    cloneBytes[0] = saturatedAdd(cloneBytes[0], size);
                    if (cloneBytes[0] > limits.maxCloneBytes()) {
                        throw new CloneLimitExceededException("Remote clone exceeds maxCloneBytes");
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    private static long saturatedAdd(long left, long right) {
        long result = left + right;
        return result < left ? Long.MAX_VALUE : result;
    }

    private static void checkDeadline(long deadline) throws CloneTimeoutException {
        if (System.nanoTime() - deadline >= 0) {
            throw new CloneTimeoutException();
        }
    }

    private static void drainOutput(InputStream input, BoundedOutput output) {
        try (input) {
            byte[] buffer = new byte[1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.append(buffer, read);
            }
        } catch (IOException ignored) {
            // The process may close its pipe during normal termination or timeout cleanup.
        }
    }

    private static void collectDescendants(Process process, Set<ProcessHandle> descendants) {
        try (Stream<ProcessHandle> children = process.toHandle().descendants()) {
            children.forEach(descendants::add);
        } catch (UnsupportedOperationException | SecurityException ignored) {
            // Process-tree control is best effort on platforms that do not expose it.
        }
    }

    private static void terminateRemainingDescendants(Set<ProcessHandle> descendants) {
        for (ProcessHandle child : descendants) {
            if (child.isAlive()) {
                child.destroy();
            }
        }
        for (ProcessHandle child : descendants) {
            if (child.isAlive()) {
                child.destroyForcibly();
            }
        }
        awaitDescendants(descendants);
    }

    private static void terminateProcess(Process process, Set<ProcessHandle> descendants) {
        for (ProcessHandle child : descendants) {
            if (child.isAlive()) {
                child.destroy();
            }
        }
        process.destroy();
        boolean interrupted = false;
        try {
            if (!process.waitFor(250, TimeUnit.MILLISECONDS) || process.isAlive()) {
                for (ProcessHandle child : descendants) {
                    if (child.isAlive()) {
                        child.destroyForcibly();
                    }
                }
                process.destroyForcibly();
                process.waitFor(1, TimeUnit.SECONDS);
            }
        } catch (InterruptedException ex) {
            interrupted = true;
            process.destroyForcibly();
            for (ProcessHandle child : descendants) {
                if (child.isAlive()) {
                    child.destroyForcibly();
                }
            }
        } finally {
            awaitDescendants(descendants);
            closeProcessStreams(process);
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void awaitDescendants(Set<ProcessHandle> descendants) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(500);
        for (ProcessHandle child : descendants) {
            long remaining = deadline - System.nanoTime();
            if (!child.isAlive() || remaining <= 0) {
                continue;
            }
            try {
                child.onExit().get(remaining, TimeUnit.NANOSECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (ExecutionException | TimeoutException | UnsupportedOperationException ignored) {
                return;
            }
        }
    }

    private static void closeProcessStreams(Process process) {
        closeQuietly(process.getOutputStream());
        closeQuietly(process.getInputStream());
        closeQuietly(process.getErrorStream());
    }

    private static void closeQuietly(Closeable stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
            // Stream closure is best effort during process cleanup.
        }
    }

    private static void joinOutputReader(Thread outputReader, Process process) {
        try {
            outputReader.join(500);
            if (outputReader.isAlive()) {
                closeQuietly(process.getInputStream());
                outputReader.interrupt();
                outputReader.join(500);
            }
        } catch (InterruptedException ex) {
            outputReader.interrupt();
            Thread.currentThread().interrupt();
        }
    }

    private static Object[] newLocks(int count) {
        Object[] locks = new Object[count];
        for (int index = 0; index < count; index++) {
            locks[index] = new Object();
        }
        return locks;
    }

    static boolean isUsableClone(Path target) {
        return Files.isDirectory(target) && Files.exists(target.resolve(".git"));
    }

    private static String cacheKey(String fullName) {
        String[] parts = fullName.split("/", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IngestionException("Invalid normalized GitHub repository name");
        }
        return safeSegment(parts[0]) + "/" + safeSegment(parts[1]);
    }

    private static String safeSegment(String value) {
        String segment = value.replaceAll("[^A-Za-z0-9._-]", "_");
        return segment.equals(".") || segment.equals("..") ? "_" + segment : segment;
    }

    private static String trim(String output) {
        String value = output == null ? "" : output.trim();
        if (value.length() > 400) {
            return value.substring(0, 400) + "…";
        }
        return value;
    }

    private static void deleteRecursive(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        List<Path> paths = new ArrayList<>();
        try (var walk = Files.walk(root)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(paths::add);
        }
        for (Path path : paths) {
            Files.deleteIfExists(path);
        }
    }

    private static void deleteQuietly(Path root) {
        try {
            deleteRecursive(root);
        } catch (IOException ignored) {
            // Staging directories are retried by the next clone if cleanup fails.
        }
    }

    private static final class BoundedOutput {
        private final byte[] bytes;
        private int size;
        private boolean truncated;

        private BoundedOutput(int maxBytes) {
            bytes = new byte[maxBytes];
        }

        private synchronized void append(byte[] source, int length) {
            int copied = Math.min(length, bytes.length - size);
            if (copied > 0) {
                System.arraycopy(source, 0, bytes, size, copied);
                size += copied;
            }
            if (copied < length) {
                truncated = true;
            }
        }

        private synchronized String asString() {
            String text = new String(bytes, 0, size, StandardCharsets.UTF_8);
            return truncated ? text + " [output truncated]" : text;
        }
    }

    private static final class CloneTimeoutException extends IOException {
        private static final long serialVersionUID = 1L;
    }

    private static final class CloneLimitExceededException extends IOException {
        private static final long serialVersionUID = 1L;

        private CloneLimitExceededException(String message) {
            super(message);
        }
    }
}

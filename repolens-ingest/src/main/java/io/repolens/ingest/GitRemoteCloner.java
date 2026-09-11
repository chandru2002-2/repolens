package io.repolens.ingest;

import io.repolens.core.ports.IngestionException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Shallow-clones allowlisted remote repositories into an isolated workspace.
 * Clones of the same repository are serialized and reused when {@code .git}
 * already exists. Reuse does <em>not</em> fetch or pull: a previously cloned
 * working tree is analyzed as-is, including any local files added after clone.
 * Delete the cache directory to force a fresh clone. Freshness is a known
 * limitation, not a guarantee.
 */
public final class GitRemoteCloner {

    @FunctionalInterface
    public interface GitProcess {
        Result run(List<String> command) throws IOException, InterruptedException;

        record Result(int exitCode, String output) {
        }
    }

    private final Duration timeout;
    private final GitProcess gitProcess;
    private final ConcurrentHashMap<String, Object> cloneLocks = new ConcurrentHashMap<>();

    public GitRemoteCloner() {
        this(Duration.ofMinutes(2));
    }

    public GitRemoteCloner(Duration timeout) {
        this(timeout, processWithTimeout(timeout));
    }

    public GitRemoteCloner(Duration timeout, GitProcess gitProcess) {
        this.timeout = timeout;
        this.gitProcess = gitProcess;
    }

    public Path cloneTo(RemoteRepositoryUrls.NormalizedRemote remote, Path workspaceRoot) {
        String cacheKey = sanitize(remote.fullName());
        Object lock = cloneLocks.computeIfAbsent(cacheKey, ignored -> new Object());
        synchronized (lock) {
            try {
                Files.createDirectories(workspaceRoot);
                Path target = workspaceRoot.resolve(cacheKey);
                if (isUsableClone(target)) {
                    // Existing .git is sufficient; no fetch/pull (stale cache is possible).
                    return target;
                }
                if (Files.exists(target)) {
                    deleteRecursive(target);
                }
                Files.createDirectories(target.getParent());

                List<String> command = List.of(
                        "git", "clone", "--depth", "1", "--single-branch",
                        remote.cloneUrl(),
                        target.toString()
                );
                GitProcess.Result result = gitProcess.run(command);
                if (result.exitCode() != 0) {
                    throw new IngestionException(UserFacingErrors.sanitize(
                            "Could not clone " + remote.cloneUrl() + ": " + trim(result.output())));
                }
                return target;
            } catch (IngestionException ex) {
                throw ex;
            } catch (IOException | InterruptedException ex) {
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw new IngestionException(
                        UserFacingErrors.sanitize("Failed to clone " + remote.cloneUrl()), ex);
            }
        }
    }

    private static GitProcess processWithTimeout(Duration timeout) {
        return command -> {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes());
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new GitProcess.Result(124, "Timed out cloning");
            }
            return new GitProcess.Result(process.exitValue(), output);
        };
    }

    static boolean isUsableClone(Path target) {
        return Files.isDirectory(target) && Files.exists(target.resolve(".git"));
    }

    private static String sanitize(String fullName) {
        return fullName.replaceAll("[^A-Za-z0-9._/-]", "_");
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
}

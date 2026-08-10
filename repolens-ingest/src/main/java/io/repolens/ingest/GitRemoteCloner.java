package io.repolens.ingest;

import io.repolens.core.ports.IngestionException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Shallow-clones allowlisted remote repositories into an isolated workspace.
 */
public final class GitRemoteCloner {

    private final Duration timeout;

    public GitRemoteCloner() {
        this(Duration.ofMinutes(2));
    }

    public GitRemoteCloner(Duration timeout) {
        this.timeout = timeout;
    }

    public Path cloneTo(RemoteRepositoryUrls.NormalizedRemote remote, Path workspaceRoot) {
        try {
            Files.createDirectories(workspaceRoot);
            Path target = workspaceRoot.resolve(sanitize(remote.fullName()));
            if (Files.exists(target)) {
                deleteRecursive(target);
            }
            Files.createDirectories(target.getParent());

            List<String> command = List.of(
                    "git", "clone", "--depth", "1", "--single-branch",
                    remote.cloneUrl(),
                    target.toString()
            );
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes());
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IngestionException("Timed out cloning " + remote.cloneUrl());
            }
            if (process.exitValue() != 0) {
                throw new IngestionException("git clone failed for " + remote.cloneUrl() + ": " + trim(output));
            }
            return target;
        } catch (IngestionException ex) {
            throw ex;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IngestionException("Failed to clone " + remote.cloneUrl(), ex);
        }
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

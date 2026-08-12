package io.repolens.ingest;

import io.repolens.core.model.CommitInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Reads optional Git metadata from a working tree. Never throws to callers — returns empty
 * Optionals when Git is missing or the directory is not a repository.
 */
final class GitMetadataReader {

    private static final int TIMEOUT_SECONDS = 8;

    Optional<String> currentBranch(Path workingTree) {
        Optional<String> symbolic = runGit(workingTree, "rev-parse", "--abbrev-ref", "HEAD");
        if (symbolic.isPresent() && !symbolic.get().equals("HEAD")) {
            return symbolic;
        }
        return runGit(workingTree, "symbolic-ref", "--short", "HEAD")
                .or(() -> runGit(workingTree, "rev-parse", "--short", "HEAD"));
    }

    Optional<CommitInfo> latestCommit(Path workingTree) {
        Optional<String> raw = runGit(
                workingTree,
                "log",
                "-1",
                "--format=%H%n%s%n%an%n%aI%n%cI"
        );
        if (raw.isEmpty()) {
            return Optional.empty();
        }
        String[] lines = raw.get().split("\\R", -1);
        if (lines.length < 3) {
            return Optional.empty();
        }
        return Optional.of(new CommitInfo(
                Optional.ofNullable(blankToNull(lines[0])),
                Optional.ofNullable(blankToNull(lines[1])),
                Optional.ofNullable(blankToNull(lines[2])),
                lines.length > 3 ? parseInstant(lines[3]) : Optional.empty(),
                lines.length > 4 ? parseInstant(lines[4]) : Optional.empty()
        ));
    }

    Optional<Instant> firstCommitAt(Path workingTree) {
        if (isShallow(workingTree)) {
            return Optional.empty();
        }
        return runGit(workingTree, "log", "--reverse", "--format=%aI", "-1")
                .flatMap(this::parseInstant);
    }

    Optional<Integer> commitCount(Path workingTree) {
        if (isShallow(workingTree)) {
            return Optional.empty();
        }
        return runGit(workingTree, "rev-list", "--count", "HEAD")
                .map(String::trim)
                .filter(value -> value.matches("\\d+"))
                .map(Integer::valueOf);
    }

    Optional<String> latestCommitAuthor(Path workingTree) {
        return latestCommit(workingTree).flatMap(CommitInfo::author);
    }

    boolean isGitRepository(Path workingTree) {
        return runGit(workingTree, "rev-parse", "--is-inside-work-tree")
                .map(value -> "true".equalsIgnoreCase(value.trim()))
                .orElse(false);
    }

    private boolean isShallow(Path workingTree) {
        return Files.isRegularFile(workingTree.resolve(".git/shallow"))
                || runGit(workingTree, "rev-parse", "--is-shallow-repository")
                .map(value -> "true".equalsIgnoreCase(value.trim()))
                .orElse(false);
    }

    private Optional<String> runGit(Path workingTree, String... args) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(workingTree.toAbsolutePath().normalize().toString());
        for (String arg : args) {
            command.add(arg);
        }
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return Optional.empty();
            }
            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!sb.isEmpty()) {
                        sb.append('\n');
                    }
                    sb.append(line);
                }
                output = sb.toString().trim();
            }
            if (process.exitValue() != 0) {
                return Optional.empty();
            }
            if (output.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(output);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    private Optional<Instant> parseInstant(String value) {
        String trimmed = blankToNull(value);
        if (trimmed == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.parse(trimmed));
        } catch (DateTimeParseException ex) {
            return Optional.empty();
        }
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

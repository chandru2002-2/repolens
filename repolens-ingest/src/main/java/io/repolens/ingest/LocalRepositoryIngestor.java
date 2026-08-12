package io.repolens.ingest;

import io.repolens.core.model.Repository;
import io.repolens.core.model.WorkingTreeInventory;
import io.repolens.core.ports.IngestionException;
import io.repolens.core.ports.RepositoryIngestor;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Local and allowlisted remote repository ingestion with ignore rules and safety limits.
 */
public final class LocalRepositoryIngestor implements RepositoryIngestor {

    private final IngestLimits limits;
    private final Path remoteWorkspace;
    private final GitRemoteCloner remoteCloner;

    public LocalRepositoryIngestor() {
        this(IngestLimits.DEFAULT, defaultRemoteWorkspace(), new GitRemoteCloner());
    }

    public LocalRepositoryIngestor(IngestLimits limits) {
        this(limits, defaultRemoteWorkspace(), new GitRemoteCloner());
    }

    public LocalRepositoryIngestor(IngestLimits limits, Path remoteWorkspace, GitRemoteCloner remoteCloner) {
        this.limits = Objects.requireNonNull(limits, "limits");
        this.remoteWorkspace = Objects.requireNonNull(remoteWorkspace, "remoteWorkspace");
        this.remoteCloner = Objects.requireNonNull(remoteCloner, "remoteCloner");
    }

    private static Path defaultRemoteWorkspace() {
        return Path.of(System.getProperty("user.home"), ".repolens", "cache", "remotes");
    }

    @Override
    public IngestionResult ingest(IngestionRequest request) {
        Objects.requireNonNull(request, "request");
        boolean remote = request.remote() || RemoteRepositoryUrls.looksRemote(request.source());
        if (remote) {
            return ingestRemote(request.source());
        }
        return ingestLocal(request.source());
    }

    private IngestionResult ingestRemote(String source) {
        RemoteRepositoryUrls.NormalizedRemote remote = RemoteRepositoryUrls.normalizeGithubHttps(source);
        Path workingTree = remoteCloner.cloneTo(remote, remoteWorkspace);
        try {
            WorkingTreeInventory inventory = inventoryOf(workingTree);
            Repository repository = Repository.remote(
                    "remote:" + remote.fullName(),
                    remote.repoName(),
                    remote.cloneUrl()
            );
            return new IngestionResult(repository, workingTree, inventory);
        } catch (IngestionException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new IngestionException("Failed to inventory cloned repository: " + remote.cloneUrl(), ex);
        }
    }

    private IngestionResult ingestLocal(String source) {
        Path workingTree = Path.of(source).toAbsolutePath().normalize();
        if (!Files.exists(workingTree)) {
            throw new IngestionException("Path does not exist: " + workingTree);
        }
        if (!Files.isDirectory(workingTree)) {
            throw new IngestionException("Path is not a directory: " + workingTree);
        }

        try {
            WorkingTreeInventory inventory = inventoryOf(workingTree);
            String name = workingTree.getFileName() == null
                    ? workingTree.toString()
                    : workingTree.getFileName().toString();
            Repository repository = Repository.local(
                    "local:" + workingTree,
                    name,
                    workingTree.toString()
            );
            return new IngestionResult(repository, workingTree, inventory);
        } catch (IngestionException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new IngestionException("Failed to ingest local repository: " + workingTree, ex);
        }
    }

    private WorkingTreeInventory inventoryOf(Path workingTree) throws IOException {
        IgnoreRules ignoreRules = IgnoreRules.builder()
                .addDefaults()
                .addPatterns(IgnoreFileLoader.loadRootIgnorePatterns(workingTree))
                .build();
        return scan(workingTree, ignoreRules);
    }

    private WorkingTreeInventory scan(Path workingTree, IgnoreRules ignoreRules) throws IOException {
        List<WorkingTreeInventory.InventoriedFile> files = new ArrayList<>();
        List<WorkingTreeInventory.SkippedFile> skippedFiles = new ArrayList<>();
        AtomicLong totalBytes = new AtomicLong();
        AtomicInteger skipped = new AtomicInteger();

        Files.walkFileTree(workingTree, new FileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (dir.equals(workingTree)) {
                    return FileVisitResult.CONTINUE;
                }
                if (attrs.isSymbolicLink()) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Path relative = workingTree.relativize(dir);
                if (depth(relative) > limits.maxDepth()) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                if (ignoreRules.isIgnored(relative, true)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                Path relative = workingTree.relativize(file);
                if (depth(relative) > limits.maxDepth()) {
                    skipped.incrementAndGet();
                    return FileVisitResult.CONTINUE;
                }
                if (ignoreRules.isIgnored(relative, false)) {
                    skipped.incrementAndGet();
                    return FileVisitResult.CONTINUE;
                }
                if (!attrs.isRegularFile() || attrs.isSymbolicLink()) {
                    skipped.incrementAndGet();
                    return FileVisitResult.CONTINUE;
                }

                String posixPath = toPosix(relative);
                long size = attrs.size();
                if (size > limits.maxFileBytes()) {
                    skippedFiles.add(new WorkingTreeInventory.SkippedFile(
                            posixPath,
                            "maxFileBytes",
                            size,
                            limits.maxFileBytes()
                    ));
                    skipped.incrementAndGet();
                    return FileVisitResult.CONTINUE;
                }
                if (BinaryExtensions.isBinaryPath(posixPath)) {
                    skipped.incrementAndGet();
                    return FileVisitResult.CONTINUE;
                }

                long nextTotal = totalBytes.addAndGet(size);
                if (nextTotal > limits.maxTotalBytes()) {
                    throw new IngestionException(String.format(
                            Locale.ROOT,
                            "Repository exceeds maxTotalBytes (%d) while scanning %s",
                            limits.maxTotalBytes(),
                            relative
                    ));
                }

                files.add(new WorkingTreeInventory.InventoriedFile(posixPath, size));
                if (files.size() > limits.maxFileCount()) {
                    throw new IngestionException(String.format(
                            Locale.ROOT,
                            "Repository exceeds maxFileCount (%d)",
                            limits.maxFileCount()
                    ));
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                skipped.incrementAndGet();
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });

        files.sort((a, b) -> a.relativePath().compareTo(b.relativePath()));
        skippedFiles.sort((a, b) -> a.relativePath().compareTo(b.relativePath()));
        return new WorkingTreeInventory(files, totalBytes.get(), skipped.get(), skippedFiles);
    }

    private static int depth(Path relative) {
        if (relative.getNameCount() == 1 && relative.toString().isEmpty()) {
            return 0;
        }
        return relative.getNameCount();
    }

    private static String toPosix(Path relative) {
        return relative.toString().replace('\\', '/');
    }
}

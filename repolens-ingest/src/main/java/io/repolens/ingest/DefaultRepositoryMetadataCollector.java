package io.repolens.ingest;

import io.repolens.core.model.AnalysisResult;
import io.repolens.core.model.CommitInfo;
import io.repolens.core.model.Repository;
import io.repolens.core.model.RepositoryMetadata;
import io.repolens.core.model.RepositoryOrigin;
import io.repolens.core.model.WorkingTreeInventory;
import io.repolens.core.ports.RepositoryMetadataCollector;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Collects repository metadata from inventory size, local Git, and (for remotes) the
 * public GitHub API. Failures are non-fatal.
 */
public final class DefaultRepositoryMetadataCollector implements RepositoryMetadataCollector {

    private static final Logger LOGGER = Logger.getLogger(DefaultRepositoryMetadataCollector.class.getName());

    private final GitMetadataReader git;
    private final GithubMetadataClient github;

    public DefaultRepositoryMetadataCollector() {
        this(new GitMetadataReader(), new GithubMetadataClient());
    }

    DefaultRepositoryMetadataCollector(GitMetadataReader git, GithubMetadataClient github) {
        this.git = Objects.requireNonNull(git, "git");
        this.github = Objects.requireNonNull(github, "github");
    }

    @Override
    public CollectionResult collect(Repository repository, Path workingTree, WorkingTreeInventory inventory) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(workingTree, "workingTree");
        Objects.requireNonNull(inventory, "inventory");

        RepositoryMetadata.Builder builder = RepositoryMetadata.builder()
                .name(repository.name())
                .sizeBytes(inventory.totalBytes());

        Optional<AnalysisResult> notes = Optional.empty();

        if (repository.origin() == RepositoryOrigin.REMOTE) {
            notes = enrichFromGithub(repository, builder);
        }

        enrichFromGit(workingTree, builder);

        if (builder.build().owner().isEmpty() && repository.origin() == RepositoryOrigin.REMOTE) {
            parseGithubOwner(repository.remoteUrl().orElse("")).ifPresent(builder::owner);
        }

        return new CollectionResult(builder.build(), notes);
    }

    private Optional<AnalysisResult> enrichFromGithub(Repository repository, RepositoryMetadata.Builder builder) {
        Optional<RemoteRepositoryUrls.NormalizedRemote> remote = tryNormalize(repository);
        if (remote.isEmpty()) {
            return Optional.empty();
        }
        String fullName = remote.get().fullName();
        int slash = fullName.indexOf('/');
        if (slash <= 0 || slash == fullName.length() - 1) {
            return Optional.empty();
        }
        String owner = fullName.substring(0, slash);
        String repo = fullName.substring(slash + 1);
        try {
            RepositoryMetadata api = github.fetchRepository(owner, repo);
            api.name().ifPresent(builder::name);
            api.owner().ifPresent(builder::owner);
            api.sizeBytes().ifPresent(builder::sizeBytes);
            api.createdAt().ifPresent(builder::createdAt);
            api.defaultBranch().ifPresent(builder::defaultBranch);

            String branch = api.defaultBranch().orElse(null);
            Optional<CommitInfo> commit = github.fetchLatestCommit(owner, repo, branch);
            commit.ifPresent(builder::lastCommit);
            return Optional.empty();
        } catch (Exception ex) {
            LOGGER.log(Level.INFO, "GitHub metadata unavailable for {0}: {1}", new Object[]{fullName, ex.toString()});
            return Optional.of(new AnalysisResult(
                    "metadata",
                    "GitHub metadata unavailable; using local clone metadata where possible.",
                    List.of(),
                    List.of(),
                    List.of(new AnalysisResult.Finding(
                            "metadata-github-unavailable",
                            "warning",
                            "Could not fetch GitHub repository metadata for " + fullName + ".",
                            Optional.empty()
                    ))
            ));
        }
    }

    private void enrichFromGit(Path workingTree, RepositoryMetadata.Builder builder) {
        if (!git.isGitRepository(workingTree)) {
            return;
        }
        RepositoryMetadata current = builder.build();
        if (current.defaultBranch().isEmpty()) {
            git.currentBranch(workingTree).ifPresent(builder::defaultBranch);
        }
        if (current.lastCommit().isEmpty()) {
            git.latestCommit(workingTree).ifPresent(builder::lastCommit);
        }
        if (current.firstCommitAt().isEmpty() && current.createdAt().isEmpty()) {
            git.firstCommitAt(workingTree).ifPresent(builder::firstCommitAt);
        }
        if (current.commitCount().isEmpty()) {
            git.commitCount(workingTree).ifPresent(builder::commitCount);
        }
        if (current.owner().isEmpty()) {
            git.latestCommitAuthor(workingTree).ifPresent(builder::owner);
        }
    }

    private static Optional<RemoteRepositoryUrls.NormalizedRemote> tryNormalize(Repository repository) {
        String url = repository.remoteUrl().orElse("");
        try {
            String https = url.endsWith(".git") ? url.substring(0, url.length() - 4) : url;
            return Optional.of(RemoteRepositoryUrls.normalizeGithubHttps(https));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private static Optional<String> parseGithubOwner(String remoteUrl) {
        try {
            String https = remoteUrl.endsWith(".git") ? remoteUrl.substring(0, remoteUrl.length() - 4) : remoteUrl;
            String full = RemoteRepositoryUrls.normalizeGithubHttps(https).fullName();
            int slash = full.indexOf('/');
            if (slash > 0) {
                return Optional.of(full.substring(0, slash));
            }
        } catch (RuntimeException ignored) {
            // omit
        }
        return Optional.empty();
    }
}

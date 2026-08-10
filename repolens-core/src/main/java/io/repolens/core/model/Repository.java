package io.repolens.core.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Repository identity and origin metadata.
 */
public record Repository(
        String id,
        String name,
        RepositoryOrigin origin,
        Optional<String> localPath,
        Optional<String> remoteUrl,
        Optional<String> defaultRef
) {
    public Repository {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(localPath, "localPath");
        Objects.requireNonNull(remoteUrl, "remoteUrl");
        Objects.requireNonNull(defaultRef, "defaultRef");
        if (id.isBlank() || name.isBlank()) {
            throw new IllegalArgumentException("id and name must not be blank");
        }
        if (origin == RepositoryOrigin.LOCAL && localPath.isEmpty()) {
            throw new IllegalArgumentException("LOCAL repositories require localPath");
        }
        if (origin == RepositoryOrigin.REMOTE && remoteUrl.isEmpty()) {
            throw new IllegalArgumentException("REMOTE repositories require remoteUrl");
        }
    }

    public static Repository local(String id, String name, String localPath) {
        return new Repository(
                id,
                name,
                RepositoryOrigin.LOCAL,
                Optional.of(localPath),
                Optional.empty(),
                Optional.empty()
        );
    }

    public static Repository remote(String id, String name, String remoteUrl) {
        return new Repository(
                id,
                name,
                RepositoryOrigin.REMOTE,
                Optional.empty(),
                Optional.of(remoteUrl),
                Optional.empty()
        );
    }
}

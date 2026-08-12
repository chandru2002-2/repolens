package io.repolens.core.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Latest commit snapshot for repository metadata.
 */
public record CommitInfo(
        Optional<String> sha,
        Optional<String> message,
        Optional<String> author,
        Optional<Instant> authoredAt,
        Optional<Instant> committedAt
) {
    public CommitInfo {
        Objects.requireNonNull(sha, "sha");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(author, "author");
        Objects.requireNonNull(authoredAt, "authoredAt");
        Objects.requireNonNull(committedAt, "committedAt");
    }

    public Optional<Instant> primaryDate() {
        return authoredAt.or(() -> committedAt);
    }
}

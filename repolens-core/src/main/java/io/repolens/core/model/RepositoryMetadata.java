package io.repolens.core.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Optional repository identity / history metadata.
 *
 * <p>{@code createdAt} is used for true repository creation (e.g. GitHub {@code created_at}).
 * {@code firstCommitAt} is the earliest commit timestamp when only Git history is available —
 * UI must label that as "First commit", not "Created".
 */
public record RepositoryMetadata(
        Optional<String> name,
        Optional<String> owner,
        Optional<Long> sizeBytes,
        Optional<Instant> createdAt,
        Optional<Instant> firstCommitAt,
        Optional<String> defaultBranch,
        Optional<CommitInfo> lastCommit,
        Optional<Integer> commitCount
) {
    public static final RepositoryMetadata EMPTY = new RepositoryMetadata(
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
    );

    public RepositoryMetadata {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(sizeBytes, "sizeBytes");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(firstCommitAt, "firstCommitAt");
        Objects.requireNonNull(defaultBranch, "defaultBranch");
        Objects.requireNonNull(lastCommit, "lastCommit");
        Objects.requireNonNull(commitCount, "commitCount");
        sizeBytes.ifPresent(value -> {
            if (value < 0) {
                throw new IllegalArgumentException("sizeBytes must be >= 0");
            }
        });
        commitCount.ifPresent(value -> {
            if (value < 0) {
                throw new IllegalArgumentException("commitCount must be >= 0");
            }
        });
    }

    public boolean isEmpty() {
        return name.isEmpty()
                && owner.isEmpty()
                && sizeBytes.isEmpty()
                && createdAt.isEmpty()
                && firstCommitAt.isEmpty()
                && defaultBranch.isEmpty()
                && lastCommit.isEmpty()
                && commitCount.isEmpty();
    }

    public Builder toBuilder() {
        return new Builder()
                .name(name.orElse(null))
                .owner(owner.orElse(null))
                .sizeBytes(sizeBytes.orElse(null))
                .createdAt(createdAt.orElse(null))
                .firstCommitAt(firstCommitAt.orElse(null))
                .defaultBranch(defaultBranch.orElse(null))
                .lastCommit(lastCommit.orElse(null))
                .commitCount(commitCount.orElse(null));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String name;
        private String owner;
        private Long sizeBytes;
        private Instant createdAt;
        private Instant firstCommitAt;
        private String defaultBranch;
        private CommitInfo lastCommit;
        private Integer commitCount;

        public Builder name(String value) {
            this.name = blankToNull(value);
            return this;
        }

        public Builder owner(String value) {
            this.owner = blankToNull(value);
            return this;
        }

        public Builder sizeBytes(Long value) {
            this.sizeBytes = value;
            return this;
        }

        public Builder createdAt(Instant value) {
            this.createdAt = value;
            return this;
        }

        public Builder firstCommitAt(Instant value) {
            this.firstCommitAt = value;
            return this;
        }

        public Builder defaultBranch(String value) {
            this.defaultBranch = blankToNull(value);
            return this;
        }

        public Builder lastCommit(CommitInfo value) {
            this.lastCommit = value;
            return this;
        }

        public Builder commitCount(Integer value) {
            this.commitCount = value;
            return this;
        }

        public RepositoryMetadata build() {
            return new RepositoryMetadata(
                    Optional.ofNullable(name),
                    Optional.ofNullable(owner),
                    Optional.ofNullable(sizeBytes),
                    Optional.ofNullable(createdAt),
                    Optional.ofNullable(firstCommitAt),
                    Optional.ofNullable(defaultBranch),
                    Optional.ofNullable(lastCommit),
                    Optional.ofNullable(commitCount)
            );
        }

        private static String blankToNull(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            return value.trim();
        }
    }
}

package io.repolens.ingest;

import io.repolens.core.model.CommitInfo;
import io.repolens.core.model.RepositoryMetadata;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches public GitHub repository metadata without authentication.
 */
final class GithubMetadataClient {

    private static final Pattern OWNER_LOGIN = Pattern.compile("\"login\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern SIZE = Pattern.compile("\"size\"\\s*:\\s*(\\d+)");
    private static final Pattern CREATED_AT = Pattern.compile("\"created_at\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern DEFAULT_BRANCH = Pattern.compile("\"default_branch\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern NAME = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern SHA = Pattern.compile("\"sha\"\\s*:\\s*\"([a-fA-F0-9]+)\"");
    private static final Pattern MESSAGE = Pattern.compile("\"message\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern AUTHOR_NAME = Pattern.compile(
            "\"author\"\\s*:\\s*\\{[^}]*?\"name\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"",
            Pattern.DOTALL);
    private static final Pattern AUTHOR_DATE = Pattern.compile(
            "\"author\"\\s*:\\s*\\{[^}]*?\"date\"\\s*:\\s*\"([^\"]+)\"",
            Pattern.DOTALL);
    private static final Pattern COMMITTER_DATE = Pattern.compile(
            "\"committer\"\\s*:\\s*\\{[^}]*?\"date\"\\s*:\\s*\"([^\"]+)\"",
            Pattern.DOTALL);

    private final HttpClient httpClient;
    private final URI apiBase;
    private final Duration timeout;

    GithubMetadataClient() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                URI.create("https://api.github.com"),
                Duration.ofSeconds(8));
    }

    GithubMetadataClient(HttpClient httpClient, URI apiBase, Duration timeout) {
        this.httpClient = httpClient;
        this.apiBase = apiBase;
        this.timeout = timeout;
    }

    RepositoryMetadata fetchRepository(String owner, String repo) throws IOException, InterruptedException {
        String body = get("/repos/" + owner + "/" + repo);
        RepositoryMetadata.Builder builder = RepositoryMetadata.builder()
                .owner(first(OWNER_LOGIN, body).orElse(owner))
                .name(first(NAME, body).orElse(repo))
                .defaultBranch(first(DEFAULT_BRANCH, body).orElse(null));

        first(SIZE, body).ifPresent(sizeKb -> {
            try {
                long kb = Long.parseLong(sizeKb);
                builder.sizeBytes(kb * 1024L);
            } catch (NumberFormatException ignored) {
                // omit
            }
        });
        first(CREATED_AT, body).flatMap(GithubMetadataClient::parseInstant).ifPresent(builder::createdAt);
        return builder.build();
    }

    Optional<CommitInfo> fetchLatestCommit(String owner, String repo, String branch)
            throws IOException, InterruptedException {
        String path = "/repos/" + owner + "/" + repo + "/commits?per_page=1";
        if (branch != null && !branch.isBlank()) {
            path = path + "&sha=" + branch;
        }
        String body = get(path);
        // Response is a JSON array; take the first object fields.
        Optional<String> sha = first(SHA, body);
        if (sha.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new CommitInfo(
                sha,
                first(MESSAGE, body).map(GithubMetadataClient::unescapeJson),
                first(AUTHOR_NAME, body).map(GithubMetadataClient::unescapeJson),
                first(AUTHOR_DATE, body).flatMap(GithubMetadataClient::parseInstant),
                first(COMMITTER_DATE, body).flatMap(GithubMetadataClient::parseInstant)
        ));
    }

    private String get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(apiBase.resolve(path))
                .timeout(timeout)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "RepoLens")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("GitHub API HTTP " + response.statusCode() + " for " + path);
        }
        return response.body();
    }

    private static Optional<String> first(Pattern pattern, String body) {
        Matcher matcher = pattern.matcher(body);
        if (matcher.find()) {
            return Optional.ofNullable(blankToNull(matcher.group(1)));
        }
        return Optional.empty();
    }

    private static Optional<Instant> parseInstant(String value) {
        try {
            return Optional.of(Instant.parse(value));
        } catch (DateTimeParseException ex) {
            return Optional.empty();
        }
    }

    private static String unescapeJson(String value) {
        return value
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}

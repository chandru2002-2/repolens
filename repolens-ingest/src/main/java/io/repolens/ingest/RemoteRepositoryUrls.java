package io.repolens.ingest;

import io.repolens.core.ports.IngestionException;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates and normalizes allowlisted remote repository URLs (ADR-010).
 */
public final class RemoteRepositoryUrls {

    private static final Pattern GITHUB = Pattern.compile(
            "^https://github\\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+?)(?:\\.git)?/?$",
            Pattern.CASE_INSENSITIVE
    );

    private RemoteRepositoryUrls() {
    }

    public static boolean looksRemote(String source) {
        if (source == null) {
            return false;
        }
        String trimmed = source.trim().toLowerCase(Locale.ROOT);
        return trimmed.startsWith("https://") || trimmed.startsWith("git@");
    }

    public static NormalizedRemote normalizeGithubHttps(String source) {
        if (source == null || source.isBlank()) {
            throw new IngestionException("Remote source must not be blank");
        }
        String trimmed = source.trim();
        if (trimmed.startsWith("git@")) {
            throw new IngestionException("Only HTTPS GitHub URLs are supported (git@ is not allowlisted)");
        }
        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException ex) {
            throw new IngestionException("Invalid remote URL: " + source, ex);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IngestionException("Only https remote URLs are allowlisted");
        }
        Matcher matcher = GITHUB.matcher(trimmed);
        if (!matcher.matches()) {
            throw new IngestionException(
                    "Only https://github.com/<owner>/<repo> URLs are allowlisted in v1");
        }
        String owner = matcher.group(1);
        String repo = matcher.group(2);
        String cloneUrl = "https://github.com/" + owner + "/" + repo + ".git";
        return new NormalizedRemote(cloneUrl, owner + "/" + repo, repo);
    }

    public record NormalizedRemote(String cloneUrl, String fullName, String repoName) {
    }
}

package io.repolens.analyzers.context;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Redacts common secret-like patterns from packaged source snippets.
 */
public final class SecretRedactor {

    private static final Pattern[] PATTERNS = {
            Pattern.compile("(?i)(api[_-]?key|secret|password|token|passwd|private[_-]?key)\\s*[=:]\\s*['\"][^'\"]{8,}['\"]"),
            Pattern.compile("(?i)(Bearer\\s+)[A-Za-z0-9._\\-]{20,}"),
            Pattern.compile("ghp_[A-Za-z0-9]{20,}"),
            Pattern.compile("github_pat_[A-Za-z0-9_]{20,}"),
            Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----.*?-----END [A-Z ]*PRIVATE KEY-----", Pattern.DOTALL)
    };

    private SecretRedactor() {
    }

    public static String redact(String input) {
        if (input == null || input.isEmpty()) {
            return input == null ? "" : input;
        }
        String out = input;
        for (Pattern pattern : PATTERNS) {
            out = pattern.matcher(out).replaceAll(match -> {
                String g = match.groupCount() >= 1 ? match.group(1) : "";
                if (g != null && !g.isBlank() && match.group().toLowerCase(Locale.ROOT).startsWith("bearer")) {
                    return g + "[REDACTED]";
                }
                if (match.group().contains("BEGIN")) {
                    return "[REDACTED PRIVATE KEY]";
                }
                return match.group().replaceAll("(['\"]).+\\1", "$1[REDACTED]$1");
            });
        }
        return out;
    }

    public static boolean looksSensitivePath(String path) {
        if (path == null) {
            return false;
        }
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.contains(".env")
                || lower.endsWith(".pem")
                || lower.endsWith(".key")
                || lower.contains("credentials")
                || lower.contains("secrets/");
    }
}

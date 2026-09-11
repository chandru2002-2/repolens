package io.repolens.ingest;

import java.util.regex.Pattern;

/**
 * Redacts host filesystem details from messages shown to API/UI users.
 */
public final class UserFacingErrors {

    private static final Pattern UNIX_PATH = Pattern.compile("(?<![\\w.-])(/[^\\s'\"`]+)+");
    private static final Pattern WINDOWS_PATH = Pattern.compile("[A-Za-z]:\\\\[^\\s'\"`]+");
    private static final Pattern CACHE_HINT = Pattern.compile("(?i)\\.repolens");

    private UserFacingErrors() {
    }

    public static String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "Analysis failed.";
        }
        String value = message.trim();
        if (CACHE_HINT.matcher(value).find()) {
            value = UNIX_PATH.matcher(value).replaceAll("the local clone cache");
            value = WINDOWS_PATH.matcher(value).replaceAll("the local clone cache");
            value = value.replace("the local clone cache the local clone cache", "the local clone cache");
            return collapse(value);
        }
        value = UNIX_PATH.matcher(value).replaceAll("[path]");
        value = WINDOWS_PATH.matcher(value).replaceAll("[path]");
        return collapse(value);
    }

    private static String collapse(String value) {
        return value.replaceAll("\\s{2,}", " ").trim();
    }
}

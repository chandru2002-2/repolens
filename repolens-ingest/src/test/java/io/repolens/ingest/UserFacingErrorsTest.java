package io.repolens.ingest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserFacingErrorsTest {

    @Test
    void redactsCacheAndFilesystemPaths() {
        String sanitized = UserFacingErrors.sanitize(
                "git clone failed for https://github.com/acme/demo.git: fatal: destination path "
                        + "/Users/aldro/.repolens/cache/remotes/acme/demo already exists");
        assertFalse(sanitized.contains("/Users/"));
        assertTrue(sanitized.contains("https://github.com/acme/demo.git")
                || sanitized.contains("clone")
                || sanitized.contains("cache"));
    }

    @Test
    void redactsGenericUnixPaths() {
        String sanitized = UserFacingErrors.sanitize("Path does not exist: /tmp/not a url");
        assertFalse(sanitized.contains("/tmp/"));
        assertTrue(sanitized.contains("[path]"));
    }

    @Test
    void blankBecomesGenericFailure() {
        assertEquals("Analysis failed.", UserFacingErrors.sanitize("  "));
    }
}

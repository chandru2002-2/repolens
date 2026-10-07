package io.repolens.web;

import java.time.Duration;
import java.util.Map;

/** Admission and retention limits for the in-memory Web analysis service. */
public record AnalysisJobLimits(
        int maxConcurrentJobs,
        int maxQueuedJobs,
        int maxRetainedJobs,
        Duration retention
) {
    public static final AnalysisJobLimits DEFAULT = new AnalysisJobLimits(2, 4, 10, Duration.ofMinutes(15));

    public AnalysisJobLimits {
        if (maxConcurrentJobs <= 0 || maxQueuedJobs <= 0 || maxRetainedJobs <= 0) {
            throw new IllegalArgumentException("Job limits must be positive");
        }
        if (maxRetainedJobs < (long) maxConcurrentJobs + maxQueuedJobs) {
            throw new IllegalArgumentException("maxRetainedJobs must cover concurrent and queued jobs");
        }
        if (retention == null || retention.isZero() || retention.isNegative()) {
            throw new IllegalArgumentException("retention must be positive");
        }
    }

    public static AnalysisJobLimits fromEnvironment() {
        Map<String, String> environment = System.getenv();
        return new AnalysisJobLimits(
                integer(environment, "REPOLENS_MAX_CONCURRENT_JOBS", DEFAULT.maxConcurrentJobs),
                integer(environment, "REPOLENS_MAX_QUEUED_JOBS", DEFAULT.maxQueuedJobs),
                integer(environment, "REPOLENS_MAX_RETAINED_JOBS", DEFAULT.maxRetainedJobs),
                Duration.ofSeconds(longValue(
                        environment,
                        "REPOLENS_JOB_RETENTION_SECONDS",
                        DEFAULT.retention.toSeconds()))
        );
    }

    private static int integer(Map<String, String> environment, String key, int fallback) {
        String value = environment.get(key);
        return value == null || value.isBlank() ? fallback : Integer.parseInt(value.trim());
    }

    private static long longValue(Map<String, String> environment, String key, long fallback) {
        String value = environment.get(key);
        return value == null || value.isBlank() ? fallback : Long.parseLong(value.trim());
    }
}
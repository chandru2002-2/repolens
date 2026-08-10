package io.repolens.web;

/**
 * Lifecycle status for ephemeral analysis jobs.
 */
public enum JobStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED
}

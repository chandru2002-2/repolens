package io.repolens.web;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Bounded ephemeral in-memory job store (ADR-006).
 */
public final class InMemoryJobStore {

    private final Map<String, AnalysisJob> jobs = new LinkedHashMap<>();
    private final AnalysisJobLimits limits;

    public InMemoryJobStore() {
        this(AnalysisJobLimits.fromEnvironment());
    }

    public InMemoryJobStore(AnalysisJobLimits limits) {
        this.limits = limits;
    }

    public synchronized boolean save(AnalysisJob job) {
        cleanupExpiredAt(Instant.now());
        if (jobs.containsKey(job.id())) {
            throw new IllegalArgumentException("Job id already exists");
        }
        evictOldestTerminalUntilSpace();
        if (jobs.size() >= limits.maxRetainedJobs()) {
            return false;
        }
        jobs.put(job.id(), job);
        return true;
    }

    public synchronized Optional<AnalysisJob> find(String id) {
        cleanupExpiredAt(Instant.now());
        return Optional.ofNullable(jobs.get(id));
    }

    public synchronized List<AnalysisJob> findAll() {
        cleanupExpiredAt(Instant.now());
        return List.copyOf(jobs.values());
    }

    public synchronized boolean remove(String id) {
        return jobs.remove(id) != null;
    }

    public synchronized void cleanup() {
        cleanupExpiredAt(Instant.now());
    }

    synchronized void cleanupExpiredAt(Instant now) {
        jobs.values().removeIf(job -> isTerminal(job.status())
                && expired(job.updatedAt(), now, limits.retention()));
    }

    public synchronized int size() {
        return jobs.size();
    }

    AnalysisJobLimits limits() {
        return limits;
    }

    private void evictOldestTerminalUntilSpace() {
        while (jobs.size() >= limits.maxRetainedJobs()) {
            Optional<String> oldestTerminal = jobs.values().stream()
                    .filter(job -> isTerminal(job.status()))
                    .min(Comparator.comparing(AnalysisJob::updatedAt))
                    .map(AnalysisJob::id);
            if (oldestTerminal.isEmpty()) {
                return;
            }
            jobs.remove(oldestTerminal.get());
        }
    }

    private static boolean expired(Instant updatedAt, Instant now, Duration retention) {
        return !now.isBefore(updatedAt) && Duration.between(updatedAt, now).compareTo(retention) >= 0;
    }

    private static boolean isTerminal(JobStatus status) {
        return status == JobStatus.COMPLETED || status == JobStatus.FAILED;
    }
}

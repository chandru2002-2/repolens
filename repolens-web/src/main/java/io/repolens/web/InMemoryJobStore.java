package io.repolens.web;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ephemeral in-memory job store (ADR-006).
 */
public final class InMemoryJobStore {

    private final ConcurrentHashMap<String, AnalysisJob> jobs = new ConcurrentHashMap<>();

    public void save(AnalysisJob job) {
        jobs.put(job.id(), job);
    }

    public Optional<AnalysisJob> find(String id) {
        return Optional.ofNullable(jobs.get(id));
    }

    public Collection<AnalysisJob> findAll() {
        return jobs.values();
    }

    public int size() {
        return jobs.size();
    }
}

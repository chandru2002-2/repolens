package io.repolens.web;

import io.repolens.api.AnalysisProgressDto;
import io.repolens.api.AnalysisResponseDto;
import io.repolens.core.model.RepositoryModel;
import io.repolens.core.model.Trace;
import io.repolens.core.pipeline.AnalysisProgressSnapshot;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * In-memory analysis job / session record.
 */
public final class AnalysisJob {

    private final String id;
    private final String source;
    private final boolean remote;
    private final Instant createdAt;
    private volatile JobStatus status;
    private volatile Instant updatedAt;
    private volatile String error;
    private volatile AnalysisResponseDto result;
    private volatile RepositoryModel model;
    private volatile List<Trace> traces = List.of();
    private volatile AnalysisProgressSnapshot progress;

    public AnalysisJob(String id, String source, boolean remote) {
        this.id = Objects.requireNonNull(id, "id");
        this.source = Objects.requireNonNull(source, "source");
        this.remote = remote;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
        this.status = JobStatus.QUEUED;
    }

    public String id() {
        return id;
    }

    public String source() {
        return source;
    }

    public boolean remote() {
        return remote;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public JobStatus status() {
        return status;
    }

    public String error() {
        return error;
    }

    public AnalysisResponseDto result() {
        return result;
    }

    public RepositoryModel model() {
        return model;
    }

    public List<Trace> traces() {
        return traces;
    }

    public AnalysisProgressSnapshot progress() {
        return progress;
    }

    public synchronized void updateProgress(AnalysisProgressSnapshot snapshot) {
        this.progress = snapshot;
        this.updatedAt = Instant.now();
    }

    public synchronized boolean markRunning() {
        if (status != JobStatus.QUEUED) {
            return false;
        }
        this.updatedAt = Instant.now();
        this.status = JobStatus.RUNNING;
        return true;
    }

    public synchronized void markCompleted(AnalysisResponseDto result) {
        markCompleted(result, null, List.of());
    }

    public synchronized void markCompleted(AnalysisResponseDto result, RepositoryModel model, List<Trace> traces) {
        this.result = Objects.requireNonNull(result, "result");
        this.model = model;
        this.traces = traces == null ? List.of() : List.copyOf(traces);
        this.error = null;
        this.updatedAt = Instant.now();
        this.status = JobStatus.COMPLETED;
    }

    public synchronized void markFailed(String error) {
        this.error = Objects.requireNonNull(error, "error");
        this.updatedAt = Instant.now();
        this.status = JobStatus.FAILED;
    }
}

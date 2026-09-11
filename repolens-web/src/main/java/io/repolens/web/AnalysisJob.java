package io.repolens.web;

import io.repolens.api.AnalysisResponseDto;
import io.repolens.core.model.AnalysisResult;
import io.repolens.core.model.RepositoryModel;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * In-memory analysis job / session record.
 * Retains RepositoryModel + working tree for Context Studio without re-analysis.
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
    private volatile List<AnalysisResult> analysisResults;
    private volatile Path workingTree;

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

    public List<AnalysisResult> analysisResults() {
        return analysisResults;
    }

    public Path workingTree() {
        return workingTree;
    }

    public synchronized void markRunning() {
        this.status = JobStatus.RUNNING;
        this.updatedAt = Instant.now();
    }

    public synchronized void markCompleted(AnalysisResponseDto result) {
        markCompleted(result, null, null, null);
    }

    public synchronized void markCompleted(
            AnalysisResponseDto result,
            RepositoryModel model,
            List<AnalysisResult> analysisResults,
            Path workingTree
    ) {
        this.status = JobStatus.COMPLETED;
        this.result = Objects.requireNonNull(result, "result");
        this.model = model;
        this.analysisResults = analysisResults == null ? null : List.copyOf(analysisResults);
        this.workingTree = workingTree;
        this.error = null;
        this.updatedAt = Instant.now();
    }

    public synchronized void markFailed(String error) {
        this.status = JobStatus.FAILED;
        this.error = Objects.requireNonNull(error, "error");
        this.updatedAt = Instant.now();
    }
}

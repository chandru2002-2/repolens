package io.repolens.web;

import io.repolens.analyzers.DiagramProjector;
import io.repolens.analyzers.GraphViewProjector;
import io.repolens.analyzers.context.ContextGenerator;
import io.repolens.analyzers.context.ContextRequest;
import io.repolens.analyzers.context.ContextResult;
import io.repolens.api.AnalysisJobDto;
import io.repolens.api.AnalysisResponseDto;
import io.repolens.api.AnalysisResponseMapper;
import io.repolens.api.ContextRequestDto;
import io.repolens.api.ContextResponseDto;
import io.repolens.core.model.AnalysisResult;
import io.repolens.core.model.GraphView;
import io.repolens.core.model.NamedDiagram;
import io.repolens.core.ports.AnalysisRunner;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Orchestrates async analysis jobs over RepoLens Core and Context Studio packaging.
 */
public final class AnalysisJobService implements AutoCloseable {

    private final AnalysisRunner analysisRunner;
    private final InMemoryJobStore store;
    private final ExecutorService executor;
    private final ContextGenerator contextGenerator;

    public AnalysisJobService(AnalysisRunner analysisRunner, InMemoryJobStore store) {
        this(analysisRunner, store, Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() / 2)));
    }

    public AnalysisJobService(AnalysisRunner analysisRunner, InMemoryJobStore store, ExecutorService executor) {
        this.analysisRunner = Objects.requireNonNull(analysisRunner, "analysisRunner");
        this.store = Objects.requireNonNull(store, "store");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.contextGenerator = new ContextGenerator();
    }

    public AnalysisJob submit(String source, boolean remote) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source must not be blank");
        }
        boolean effectiveRemote = remote || source.trim().startsWith("https://") || source.trim().startsWith("git@");
        AnalysisJob job = new AnalysisJob(UUID.randomUUID().toString(), source.trim(), effectiveRemote);
        store.save(job);
        executor.execute(() -> runJob(job));
        return job;
    }

    public Optional<AnalysisJob> find(String id) {
        return store.find(id);
    }

    public AnalysisJobDto toDto(AnalysisJob job) {
        return new AnalysisJobDto(
                job.id(),
                job.status().name(),
                job.source(),
                job.remote(),
                job.createdAt().toString(),
                job.updatedAt().toString(),
                job.error(),
                job.status() == JobStatus.COMPLETED ? job.result() : null
        );
    }

    public ContextResponseDto generateContext(String jobId, ContextRequestDto requestDto) {
        AnalysisJob job = store.find(jobId).orElseThrow(() -> new IllegalArgumentException("job not found"));
        if (job.status() != JobStatus.COMPLETED || job.model() == null || job.workingTree() == null) {
            throw new IllegalStateException("analysis result not ready for context generation");
        }
        ContextRequest request = ContextDtoMapper.toDomain(requestDto);
        ContextResult result = contextGenerator.generate(
                job.model(),
                job.analysisResults() == null ? List.of() : job.analysisResults(),
                job.workingTree(),
                request
        );
        var aiTask = ContextDtoMapper.toAiTask(requestDto);
        if (aiTask == null) {
            return ContextDtoMapper.toDto(result);
        }
        return ContextDtoMapper.toDto(result, ContextDtoMapper.buildPrompt(aiTask, result));
    }

    private void runJob(AnalysisJob job) {
        job.markRunning();
        try {
            AnalysisRunner.AnalysisRunResult run =
                    analysisRunner.run(new AnalysisRunner.AnalysisRequest(job.source(), job.remote()));
            List<AnalysisResult> results = run.results();
            GraphView graph = GraphViewProjector.project(run.model(), results);
            List<NamedDiagram> diagrams = DiagramProjector.projectAll(run.model());
            AnalysisResponseDto response = AnalysisResponseMapper.from(run.model(), results, graph, diagrams);
            job.markCompleted(response, run.model(), results, run.workingTree());
        } catch (Exception ex) {
            job.markFailed(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}

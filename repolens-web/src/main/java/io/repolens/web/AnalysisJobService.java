package io.repolens.web;

import io.repolens.analyzers.DiagramProjector;
import io.repolens.analyzers.GraphViewProjector;
import io.repolens.analyzers.ImpactComposer;
import io.repolens.analyzers.TraceComposer;
import io.repolens.api.AnalysisJobDto;
import io.repolens.api.AnalysisProgressDto;
import io.repolens.api.AnalysisResponseDto;
import io.repolens.api.AnalysisResponseMapper;
import io.repolens.core.model.AnalysisResult;
import io.repolens.core.model.GraphView;
import io.repolens.core.model.NamedDiagram;
import io.repolens.core.model.RepositoryModel;
import io.repolens.core.model.Trace;
import io.repolens.core.pipeline.AnalysisProgress;
import io.repolens.core.pipeline.AnalysisProgressTracker;
import io.repolens.core.ports.AnalysisRunner;
import io.repolens.ingest.UserFacingErrors;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Orchestrates async analysis jobs over RepoLens Core.
 */
public final class AnalysisJobService implements AutoCloseable {

    private final AnalysisRunner analysisRunner;
    private final InMemoryJobStore store;
    private final ThreadPoolExecutor executor;
    private final ScheduledExecutorService cleanupExecutor;
    private final AnalysisJobLimits limits;
    private final Semaphore admission;

    public AnalysisJobService(AnalysisRunner analysisRunner, InMemoryJobStore store) {
        this(analysisRunner, store, store.limits());
    }

    AnalysisJobService(AnalysisRunner analysisRunner, InMemoryJobStore store, AnalysisJobLimits limits) {
        this.analysisRunner = Objects.requireNonNull(analysisRunner, "analysisRunner");
        this.store = Objects.requireNonNull(store, "store");
        this.limits = Objects.requireNonNull(limits, "limits");
        if (!store.limits().equals(limits)) {
            throw new IllegalArgumentException("Job service and store limits must match");
        }
        this.executor = new ThreadPoolExecutor(
                limits.maxConcurrentJobs(),
                limits.maxConcurrentJobs(),
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(limits.maxQueuedJobs()),
                new ThreadPoolExecutor.AbortPolicy()
        );
        this.admission = new Semaphore(limits.maxConcurrentJobs() + limits.maxQueuedJobs(), true);
        this.cleanupExecutor = newCleanupExecutor();
        long cleanupSeconds = Math.max(1L, Math.min(60L, limits.retention().toSeconds() / 4L));
        cleanupExecutor.scheduleAtFixedRate(store::cleanup, cleanupSeconds, cleanupSeconds, TimeUnit.SECONDS);
    }

    private static ScheduledExecutorService newCleanupExecutor() {
        return Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "repolens-job-retention");
            thread.setDaemon(true);
            return thread;
        });
    }

    public AnalysisJob submit(String source, boolean remote) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source must not be blank");
        }
        boolean effectiveRemote = remote || source.trim().startsWith("https://") || source.trim().startsWith("git@");
        if (!admission.tryAcquire()) {
            throw new CapacityExceededException();
        }
        AnalysisJob job = new AnalysisJob(UUID.randomUUID().toString(), source.trim(), effectiveRemote);
        boolean stored = false;
        try {
            if (!store.save(job)) {
                throw new CapacityExceededException();
            }
            stored = true;
            executor.execute(new AnalysisTask(job));
            return job;
        } catch (RejectedExecutionException ex) {
            if (stored) {
                store.remove(job.id());
            }
            admission.release();
            throw new CapacityExceededException();
        } catch (RuntimeException ex) {
            if (stored) {
                store.remove(job.id());
            }
            admission.release();
            throw ex;
        }
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
                job.status() == JobStatus.COMPLETED ? job.result() : null,
                AnalysisProgressDto.from(job.progress())
        );
    }

    private void runJob(AnalysisJob job) {
        if (Thread.currentThread().isInterrupted()) {
            job.markFailed("Analysis stopped before execution");
            admission.release();
            return;
        }
        if (!job.markRunning()) {
            admission.release();
            return;
        }
        AnalysisProgressTracker tracker = new AnalysisProgressTracker(job::updateProgress);
        try {
            AnalysisProgress.use(tracker, () -> runTracked(job, tracker));
        } catch (Exception ex) {
            job.markFailed(UserFacingErrors.sanitize(
                    ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
        } finally {
            admission.release();
        }
    }

    private void runTracked(AnalysisJob job, AnalysisProgressTracker tracker) {
        AnalysisRunner.AnalysisRunResult run =
                analysisRunner.run(new AnalysisRunner.AnalysisRequest(job.source(), job.remote()));
        List<AnalysisResult> results = run.results();
        GraphView graph = GraphViewProjector.project(run.model(), results);
        AnalysisProgress.graphPrepared();
        List<NamedDiagram> diagrams = DiagramProjector.projectAll(run.model());
        AnalysisProgress.diagramsProjected();
        List<Trace> traces = TraceComposer.compose(run.model());
        AnalysisResponseDto response = AnalysisResponseMapper.from(run.model(), results, graph, diagrams, traces);
        tracker.markFinished();
        job.markCompleted(response, run.model(), traces);
    }

    @Override
    public void close() {
        cleanupExecutor.shutdownNow();
        for (Runnable pending : executor.shutdownNow()) {
            if (pending instanceof AnalysisTask task) {
                task.cancelBeforeStart();
            }
        }
    }

    public Optional<AnalysisResponseDto.ImpactDto> impactFor(AnalysisJob job, String entityId) {
        Objects.requireNonNull(job, "job");
        if (entityId == null || entityId.isBlank()) {
            throw new IllegalArgumentException("entityId is required");
        }
        if (job.status() != JobStatus.COMPLETED || job.model() == null) {
            return Optional.empty();
        }
        RepositoryModel model = job.model();
        if (!knownEntity(model, entityId)) {
            return Optional.empty();
        }
        return Optional.of(AnalysisResponseMapper.mapImpact(
                ImpactComposer.compose(model, entityId, job.traces())
        ));
    }

    private static boolean knownEntity(RepositoryModel model, String entityId) {
        if (model.findSymbol(entityId).isPresent()
                || model.findEndpoint(entityId).isPresent()
                || model.findTest(entityId).isPresent()) {
            return true;
        }
        return model.tests().stream().anyMatch(test -> test.symbolId().equals(entityId));
    }

    private final class AnalysisTask implements Runnable {
        private final AnalysisJob job;

        private AnalysisTask(AnalysisJob job) {
            this.job = job;
        }

        @Override
        public void run() {
            runJob(job);
        }

        private void cancelBeforeStart() {
            job.markFailed("Analysis stopped before execution");
            admission.release();
        }
    }

    public static final class CapacityExceededException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private CapacityExceededException() {
            super("Analysis capacity is full");
        }
    }
}

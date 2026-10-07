package io.repolens.web;

import io.repolens.core.model.AnalysisResult;
import io.repolens.core.model.Repository;
import io.repolens.core.model.RepositoryModel;
import io.repolens.core.model.SourceFile;
import io.repolens.core.ports.AnalysisRunner;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisJobServiceTest {

    @Test
    void submitsAndCompletesLocalJob() throws Exception {
        AnalysisRunner runner = request -> {
            Repository repository = Repository.local("r1", "demo", request.source());
            RepositoryModel model = RepositoryModel.builder(repository)
                    .addFile(new SourceFile("A.java", "java", "h", 1))
                    .build();
            AnalysisResult result = new AnalysisResult("metrics", "ok", List.of(), List.of(), List.of());
            return new AnalysisRunner.AnalysisRunResult(model, List.of(result), java.nio.file.Path.of(request.source()));
        };

        InMemoryJobStore store = new InMemoryJobStore();
        try (AnalysisJobService service = new AnalysisJobService(runner, store)) {
            AnalysisJob job = service.submit("/tmp/demo", false);
            assertTrue(job.status() == JobStatus.QUEUED
                    || job.status() == JobStatus.RUNNING
                    || job.status() == JobStatus.COMPLETED);

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (job.status() == JobStatus.QUEUED || job.status() == JobStatus.RUNNING) {
                if (System.nanoTime() > deadline) {
                    break;
                }
                Thread.sleep(20);
            }

            assertEquals(
                    JobStatus.COMPLETED,
                    job.status(),
                    () -> "Analysis job did not complete. Final status: " + job.status()
            );
            assertNotNull(job.result());
            assertEquals("v1", job.result().schemaVersion());
            assertTrue(job.result().endpoints().isEmpty());
            assertTrue(job.result().tests().isEmpty());
            assertTrue(job.result().traces().isEmpty());
            assertTrue(service.toDto(job).status().equals("COMPLETED"));
            assertTrue(store.find(job.id()).isPresent());
            store.cleanupExpiredAt(job.updatedAt().plus(store.limits().retention()).plusMillis(1));
            assertTrue(store.find(job.id()).isEmpty());
        }
    }

    @Test
    void acceptsRemoteFlagForSubmission() {
        AnalysisRunner runner = request -> {
            throw new UnsupportedOperationException("should not run in this unit test");
        };
        // Submission itself must accept remote=true; execution is async and may fail later.
        AnalysisJobLimits limits = new AnalysisJobLimits(1, 1, 2, Duration.ofMinutes(1));
        try (AnalysisJobService service = new AnalysisJobService(runner, new InMemoryJobStore(limits), limits)) {
            AnalysisJob job = service.submit("https://github.com/octocat/Hello-World", true);
            assertEquals(true, job.remote());
            assertNotNull(job.id());
        }
    }

    @Test
    void boundsRunningAndQueuedJobsAndRejectsExcessSubmission() throws Exception {
        AnalysisJobLimits limits = new AnalysisJobLimits(1, 1, 2, Duration.ofMinutes(1));
        InMemoryJobStore store = new InMemoryJobStore(limits);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximumActive = new AtomicInteger();
        AnalysisRunner runner = request -> {
            int current = active.incrementAndGet();
            maximumActive.accumulateAndGet(current, Math::max);
            started.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Test runner was not released");
                }
                Repository repository = Repository.local("r1", "demo", request.source());
                RepositoryModel model = RepositoryModel.builder(repository).build();
                return new AnalysisRunner.AnalysisRunResult(model, List.of(), java.nio.file.Path.of(request.source()));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Test runner interrupted", ex);
            } finally {
                active.decrementAndGet();
            }
        };

        try (AnalysisJobService service = new AnalysisJobService(runner, store, limits)) {
            AnalysisJob running = service.submit("/tmp/first", false);
            assertTrue(started.await(5, TimeUnit.SECONDS));
            AnalysisJob queued = service.submit("/tmp/second", false);
            assertEquals(JobStatus.QUEUED, queued.status());
            assertEquals(2, store.size());
            org.junit.jupiter.api.Assertions.assertThrows(
                    AnalysisJobService.CapacityExceededException.class,
                    () -> service.submit("/tmp/rejected", false));
            assertEquals(2, store.size(), "rejected jobs must not remain in the store");

            release.countDown();
            awaitTerminal(running);
            awaitTerminal(queued);
            assertEquals(1, maximumActive.get());
        } finally {
            release.countDown();
        }
    }

    @Test
    void shutdownMarksRunningAndQueuedJobsFailed() throws Exception {
        AnalysisJobLimits limits = new AnalysisJobLimits(1, 1, 2, Duration.ofMinutes(1));
        InMemoryJobStore store = new InMemoryJobStore(limits);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch neverReleased = new CountDownLatch(1);
        AnalysisRunner runner = request -> {
            started.countDown();
            try {
                neverReleased.await();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted by shutdown", ex);
            }
            throw new IllegalStateException("test runner should not complete");
        };
        AnalysisJobService service = new AnalysisJobService(runner, store, limits);
        AnalysisJob running = service.submit("/tmp/running", false);
        assertTrue(started.await(5, TimeUnit.SECONDS));
        AnalysisJob queued = service.submit("/tmp/queued", false);

        service.close();

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while ((running.status() != JobStatus.FAILED || queued.status() != JobStatus.FAILED)
                && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(JobStatus.FAILED, running.status());
        assertEquals(JobStatus.FAILED, queued.status());
        assertEquals(2, store.size());
    }

    private static void awaitTerminal(AnalysisJob job) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while ((job.status() == JobStatus.QUEUED || job.status() == JobStatus.RUNNING)
                && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(JobStatus.COMPLETED, job.status());
    }
}

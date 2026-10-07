package io.repolens.web;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryJobStoreTest {

    private static final AnalysisJobLimits LIMITS =
            new AnalysisJobLimits(1, 1, 2, Duration.ofMinutes(1));

    @Test
    void expiresFailedJobsAndKeepsThemBeforeExpiry() {
        InMemoryJobStore store = new InMemoryJobStore(LIMITS);
        AnalysisJob failed = failedJob("failed");
        assertTrue(store.save(failed));
        assertTrue(store.find(failed.id()).isPresent());

        store.cleanupExpiredAt(failed.updatedAt().plus(LIMITS.retention()).plusMillis(1));

        assertTrue(store.find(failed.id()).isEmpty());
        assertEquals(0, store.size());
    }

    @Test
    void doesNotEvictQueuedOrRunningJobsWhenStoreIsFull() {
        InMemoryJobStore store = new InMemoryJobStore(LIMITS);
        AnalysisJob running = new AnalysisJob("running", "/tmp/running", false);
        AnalysisJob queued = new AnalysisJob("queued", "/tmp/queued", false);
        assertTrue(running.markRunning());
        assertTrue(store.save(running));
        assertTrue(store.save(queued));

        assertFalse(store.save(failedJob("rejected")));
        store.cleanupExpiredAt(Instant.now().plus(Duration.ofDays(1)));

        assertEquals(JobStatus.RUNNING, store.find("running").orElseThrow().status());
        assertEquals(JobStatus.QUEUED, store.find("queued").orElseThrow().status());
        assertTrue(store.find("rejected").isEmpty());
    }

    @Test
    void evictsTerminalJobsToStayWithinTheRetainedJobLimit() {
        InMemoryJobStore store = new InMemoryJobStore(LIMITS);
        AnalysisJob first = failedJob("first");
        AnalysisJob second = failedJob("second");
        AnalysisJob newest = failedJob("newest");
        assertTrue(store.save(first));
        assertTrue(store.save(second));

        assertTrue(store.save(newest));

        assertEquals(LIMITS.maxRetainedJobs(), store.size());
        assertTrue(store.find("newest").isPresent());
    }

    @Test
    void cleanupIsSafeWithConcurrentReadsAndMaintenance() throws Exception {
        InMemoryJobStore store = new InMemoryJobStore(LIMITS);
        AnalysisJob running = new AnalysisJob("running", "/tmp/running", false);
        assertTrue(running.markRunning());
        assertTrue(store.save(running));
        var pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int index = 0; index < 32; index++) {
                tasks.add(() -> {
                    store.cleanupExpiredAt(Instant.now().plus(Duration.ofDays(1)));
                    store.findAll();
                    store.find("running");
                    return null;
                });
            }
            for (var result : pool.invokeAll(tasks)) {
                result.get(5, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(JobStatus.RUNNING, store.find("running").orElseThrow().status());
    }

    private static AnalysisJob failedJob(String id) {
        AnalysisJob job = new AnalysisJob(id, "/tmp/" + id, false);
        job.markFailed("expected test failure");
        return job;
    }
}
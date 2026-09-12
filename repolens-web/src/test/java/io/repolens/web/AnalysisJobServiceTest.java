package io.repolens.web;

import io.repolens.core.model.AnalysisResult;
import io.repolens.core.model.Repository;
import io.repolens.core.model.RepositoryModel;
import io.repolens.core.model.SourceFile;
import io.repolens.core.ports.AnalysisRunner;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

        try (AnalysisJobService service = new AnalysisJobService(runner, new InMemoryJobStore())) {
            AnalysisJob job = service.submit("/tmp/demo", false);
            assertEquals(JobStatus.QUEUED, job.status());

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
            assertTrue(service.toDto(job).status().equals("COMPLETED"));
        }
    }

    @Test
    void acceptsRemoteFlagForSubmission() {
        AnalysisRunner runner = request -> {
            throw new UnsupportedOperationException("should not run in this unit test");
        };
        // Submission itself must accept remote=true; execution is async and may fail later.
        ExecutorService immediate = Executors.newSingleThreadExecutor();
        try (AnalysisJobService service = new AnalysisJobService(runner, new InMemoryJobStore(), immediate)) {
            AnalysisJob job = service.submit("https://github.com/octocat/Hello-World", true);
            assertEquals(true, job.remote());
            assertNotNull(job.id());
        }
    }
}

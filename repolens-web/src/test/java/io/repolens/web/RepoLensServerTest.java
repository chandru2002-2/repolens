package io.repolens.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.repolens.core.model.AnalysisResult;
import io.repolens.core.model.Repository;
import io.repolens.core.model.RepositoryModel;
import io.repolens.core.model.SourceFile;
import io.repolens.core.ports.AnalysisRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepoLensServerTest {

    @TempDir
    Path tempDir;

    @Test
    void healthAndAnalyzeFlow() throws Exception {
        Files.writeString(tempDir.resolve("Hello.java"), "public class Hello {}");

        AnalysisRunner runner = request -> {
            Repository repository = Repository.local("r1", "demo", request.source());
            RepositoryModel model = RepositoryModel.builder(repository)
                    .addFile(new SourceFile("Hello.java", "java", "h", 10))
                    .build();
            return new AnalysisRunner.AnalysisRunResult(
                    model,
                    List.of(new AnalysisResult("structure", "ok", List.of(), List.of(), List.of())),
                    tempDir
            );
        };

        int port = 18080 + (int) (Math.abs(System.nanoTime()) % 1000);
        AnalysisJobService service = new AnalysisJobService(runner, new InMemoryJobStore(), Executors.newSingleThreadExecutor());
        RepoLensServer server = new RepoLensServer(port, service, new ObjectMapper());
        server.start();
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            ObjectMapper mapper = new ObjectMapper();

            HttpResponse<String> health = client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/health")).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(200, health.statusCode());
            assertTrue(health.body().contains("ok"));

            String body = mapper.writeValueAsString(new RepoLensServer.AnalyzeRequest(tempDir.toString(), false));
            HttpResponse<String> accepted = client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/v1/analyze"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(202, accepted.statusCode());
            JsonNode jobNode = mapper.readTree(accepted.body());
            String jobId = jobNode.get("id").asText();

            String status = "QUEUED";
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (("QUEUED".equals(status) || "RUNNING".equals(status)) && System.nanoTime() < deadline) {
                HttpResponse<String> statusResponse = client.send(
                        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/v1/jobs/" + jobId)).GET().build(),
                        HttpResponse.BodyHandlers.ofString()
                );
                status = mapper.readTree(statusResponse.body()).get("status").asText();
                if ("COMPLETED".equals(status) || "FAILED".equals(status)) {
                    break;
                }
                Thread.sleep(30);
            }
            assertEquals("COMPLETED", status);

            HttpResponse<String> result = client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/v1/jobs/" + jobId + "/result")).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(200, result.statusCode());
            assertTrue(result.body().contains("\"schemaVersion\":\"v1\""));
        } finally {
            server.stop();
        }
    }
}

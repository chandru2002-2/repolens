package io.repolens.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.repolens.core.model.AnalysisResult;
import io.repolens.core.model.Endpoint;
import io.repolens.core.model.Evidence;
import io.repolens.core.model.InferenceMethod;
import io.repolens.core.model.Relationship;
import io.repolens.core.model.RelationshipType;
import io.repolens.core.model.Repository;
import io.repolens.core.model.RepositoryModel;
import io.repolens.core.model.SourceFile;
import io.repolens.core.model.SourceLocation;
import io.repolens.core.model.Symbol;
import io.repolens.core.model.SymbolKind;
import io.repolens.core.model.Test;
import io.repolens.core.ports.AnalysisRunner;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntelligenceApiServerTest {

    @TempDir
    Path tempDir;

    @org.junit.jupiter.api.Test
    void exposesIntelligenceAndPreservesV1Result() throws Exception {
        Files.writeString(tempDir.resolve("Hello.java"), "public class Hello {}");
        int port = startPort();
        try (RepoLensServer server = start(port, intelligenceRunner())) {
            HttpClient client = client();
            ObjectMapper mapper = new ObjectMapper();
            String jobId = completeJob(client, mapper, port, tempDir.toString());

            HttpResponse<String> result = get(client, port, "/v1/jobs/" + jobId + "/result");
            assertEquals(200, result.statusCode());
            JsonNode body = mapper.readTree(result.body());
            assertEquals("v1", body.get("schemaVersion").asText());
            assertTrue(body.has("graph"));
            assertTrue(body.has("results"));
            assertEquals("ep-1", body.get("endpoints").get(0).get("id").asText());
            assertEquals("test:1", body.get("tests").get(0).get("id").asText());
            assertEquals("static", body.get("traces").get(0).get("inferenceKind").asText());

            HttpResponse<String> endpoints = get(client, port, "/v1/jobs/" + jobId + "/endpoints");
            assertEquals(200, endpoints.statusCode());
            assertEquals("GET", mapper.readTree(endpoints.body()).get(0).get("httpMethod").asText());

            HttpResponse<String> tests = get(client, port, "/v1/jobs/" + jobId + "/tests");
            assertEquals(200, tests.statusCode());
            assertEquals("sym:testMethod", mapper.readTree(tests.body()).get(0).get("symbolId").asText());

            HttpResponse<String> traces = get(client, port, "/v1/jobs/" + jobId + "/traces");
            assertEquals(200, traces.statusCode());
            JsonNode trace = mapper.readTree(traces.body()).get(0);
            assertEquals("static", trace.get("inferenceKind").asText());
            assertEquals("ep-1", trace.get("endpointId").asText());

            String entity = URLEncoder.encode("sym:service", StandardCharsets.UTF_8);
            HttpResponse<String> impact = get(client, port, "/v1/jobs/" + jobId + "/impact/" + entity);
            assertEquals(200, impact.statusCode());
            JsonNode impactBody = mapper.readTree(impact.body());
            assertEquals("sym:service", impactBody.get("entityId").asText());
            assertFalse(impactBody.get("callers").get(0).get("inferred").asBoolean());
            assertEquals(0.9, impactBody.get("callers").get(0).get("confidence").asDouble(), 0.0001);
            assertTrue(impactBody.get("inferred").isArray());
        }
    }

    @org.junit.jupiter.api.Test
    void emptyIntelligenceIsValid() throws Exception {
        Files.writeString(tempDir.resolve("Hello.java"), "public class Hello {}");
        int port = startPort();
        AnalysisRunner runner = request -> new AnalysisRunner.AnalysisRunResult(
                RepositoryModel.builder(Repository.local("r1", "demo", request.source()))
                        .addFile(new SourceFile("Hello.java", "java", "h", 10))
                        .build(),
                List.of(new AnalysisResult("structure", "ok", List.of(), List.of(), List.of())),
                tempDir
        );
        try (RepoLensServer server = start(port, runner)) {
            HttpClient client = client();
            ObjectMapper mapper = new ObjectMapper();
            String jobId = completeJob(client, mapper, port, tempDir.toString());
            JsonNode result = mapper.readTree(get(client, port, "/v1/jobs/" + jobId + "/result").body());
            assertEquals(0, result.get("endpoints").size());
            assertEquals(0, result.get("tests").size());
            assertEquals(0, result.get("traces").size());
            assertEquals("[]", get(client, port, "/v1/jobs/" + jobId + "/endpoints").body());
        }
    }

    @org.junit.jupiter.api.Test
    void missingJobReturnsNotFound() throws Exception {
        int port = startPort();
        try (RepoLensServer server = start(port, unusedRunner())) {
            HttpClient client = client();
            assertEquals(404, get(client, port, "/v1/jobs/missing/result").statusCode());
            assertEquals(404, get(client, port, "/v1/jobs/missing/endpoints").statusCode());
            assertEquals(404, get(client, port, "/v1/jobs/missing/tests").statusCode());
            assertEquals(404, get(client, port, "/v1/jobs/missing/traces").statusCode());
            assertEquals(404, get(client, port, "/v1/jobs/missing/impact/sym:service").statusCode());
            JsonNode error = new ObjectMapper().readTree(get(client, port, "/v1/jobs/missing/endpoints").body());
            assertEquals("not found", error.get("error").asText());
        }
    }

    @org.junit.jupiter.api.Test
    void missingEntityReturnsNotFound() throws Exception {
        Files.writeString(tempDir.resolve("Hello.java"), "public class Hello {}");
        int port = startPort();
        try (RepoLensServer server = start(port, intelligenceRunner())) {
            HttpClient client = client();
            ObjectMapper mapper = new ObjectMapper();
            String jobId = completeJob(client, mapper, port, tempDir.toString());
            HttpResponse<String> unknown = get(client, port, "/v1/jobs/" + jobId + "/impact/sym:missing");
            assertEquals(404, unknown.statusCode());
            assertEquals("not found", mapper.readTree(unknown.body()).get("error").asText());
        }
    }

    private AnalysisRunner intelligenceRunner() {
        return request -> {
            RepositoryModel model = RepositoryModel.builder(Repository.local("r1", "demo", request.source()))
                    .addFile(new SourceFile("UserController.java", "java", "h", 10))
                    .addFile(new SourceFile("UserService.java", "java", "h", 10))
                    .addFile(new SourceFile("UserServiceTest.java", "java", "h", 10))
                    .addSymbol(symbol("sym:controller", "UserController", SymbolKind.CLASS, Optional.empty()))
                    .addSymbol(symbol("sym:handler", "list", SymbolKind.METHOD, Optional.of("sym:controller")))
                    .addSymbol(symbol("sym:service", "UserService", SymbolKind.CLASS, Optional.empty()))
                    .addSymbol(symbol("sym:testMethod", "loads", SymbolKind.METHOD, Optional.of("sym:testClass")))
                    .addSymbol(symbol("sym:testClass", "UserServiceTest", SymbolKind.CLASS, Optional.empty()))
                    .addEndpoint(new Endpoint(
                            "ep-1",
                            "GET",
                            "/users",
                            Optional.of("sym:controller"),
                            Optional.of("sym:handler"),
                            SourceLocation.ofFile("UserController.java"),
                            Evidence.of(InferenceMethod.ANNOTATION)
                    ))
                    .addTest(new Test(
                            "test:1",
                            "sym:testMethod",
                            Optional.of("junit5"),
                            SourceLocation.ofFile("UserServiceTest.java"),
                            Evidence.of(InferenceMethod.ANNOTATION)
                    ))
                    .addRelationship(new Relationship(
                            "call-1",
                            RelationshipType.CALLS,
                            "sym:controller",
                            "sym:service",
                            0.90,
                            Optional.of("call:x;via=field"),
                            Optional.empty()
                    ))
                    .build();
            return new AnalysisRunner.AnalysisRunResult(
                    model,
                    List.of(new AnalysisResult("structure", "ok", List.of(), List.of(), List.of())),
                    tempDir
            );
        };
    }

    private AnalysisRunner unusedRunner() {
        return request -> {
            throw new UnsupportedOperationException("unused");
        };
    }

    private static Symbol symbol(String id, String name, SymbolKind kind, Optional<String> parent) {
        return new Symbol(id, name, kind, parent, Optional.empty(), SourceLocation.ofFile("UserController.java"), Optional.empty());
    }

    private RepoLensServer start(int port, AnalysisRunner runner) {
        AnalysisJobService service = new AnalysisJobService(runner, new InMemoryJobStore(), Executors.newSingleThreadExecutor());
        RepoLensServer server = new RepoLensServer(port, service, new ObjectMapper());
        server.start();
        return server;
    }

    private static int startPort() {
        return 19000 + (int) (Math.abs(System.nanoTime()) % 2000);
    }

    private static HttpClient client() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }

    private static String completeJob(HttpClient client, ObjectMapper mapper, int port, String source) throws Exception {
        String body = mapper.writeValueAsString(new RepoLensServer.AnalyzeRequest(source, false));
        HttpResponse<String> accepted = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/v1/analyze"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(202, accepted.statusCode());
        String jobId = mapper.readTree(accepted.body()).get("id").asText();
        String status = "QUEUED";
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (("QUEUED".equals(status) || "RUNNING".equals(status)) && System.nanoTime() < deadline) {
            status = mapper.readTree(get(client, port, "/v1/jobs/" + jobId).body()).get("status").asText();
            if ("COMPLETED".equals(status) || "FAILED".equals(status)) {
                break;
            }
            Thread.sleep(30);
        }
        assertEquals("COMPLETED", status);
        return jobId;
    }

    private static HttpResponse<String> get(HttpClient client, int port, String path) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }
}

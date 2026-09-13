package io.repolens.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntelligenceCliTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void analyzeHumanOutputPreservesV16Sections() throws Exception {
        Files.writeString(tempDir.resolve("Hello.java"), "public class Hello {}");
        Captured captured = run("analyze", tempDir.toString());
        assertEquals(0, captured.code());
        assertTrue(captured.stdout().contains("== RepoLens Analyze =="));
        assertTrue(captured.stdout().contains("Files      :"));
        assertTrue(captured.stdout().contains("-- Analyzers --"));
        assertTrue(captured.stdout().contains("-- Intelligence (static, not runtime) --"));
        assertTrue(captured.stdout().contains("Endpoints :"));
        assertTrue(captured.stdout().contains("static paths"));
    }

    @Test
    void analyzeJsonContainsV17Collections() throws Exception {
        Files.writeString(tempDir.resolve("Hello.java"), "public class Hello {}");
        Captured captured = run("analyze", tempDir.toString(), "--json");
        assertEquals(0, captured.code());
        JsonNode json = mapper.readTree(captured.stdout());
        assertEquals("v1", json.get("schemaVersion").asText());
        assertTrue(json.has("graph"));
        assertTrue(json.has("results"));
        assertTrue(json.get("endpoints").isArray());
        assertTrue(json.get("tests").isArray());
        assertTrue(json.get("traces").isArray());
        assertEquals(0, json.get("endpoints").size());
        assertEquals(0, json.get("tests").size());
        assertEquals(0, json.get("traces").size());
    }

    @Test
    void emptyIntelligenceCommandsAreValid() throws Exception {
        Files.writeString(tempDir.resolve("Hello.java"), "public class Hello {}");
        Captured endpoints = run("endpoints", tempDir.toString());
        assertEquals(0, endpoints.code());
        assertTrue(endpoints.stdout().contains("Static declarations"));
        assertTrue(endpoints.stdout().contains("No endpoints found."));

        Captured tests = run("tests", tempDir.toString());
        assertEquals(0, tests.code());
        assertTrue(tests.stdout().contains("inferred from relationships"));
        assertTrue(tests.stdout().contains("No tests found."));

        Captured traces = run("trace", tempDir.toString());
        assertEquals(0, traces.code());
        assertTrue(traces.stdout().contains("Not runtime execution."));
        assertTrue(traces.stdout().contains("No traces found."));
    }

    @Test
    void endpointsTestsAndTraceHumanOutputUseAnalysisFacts() throws Exception {
        Files.writeString(tempDir.resolve("UserController.java"), """
                @RestController
                public class UserController {
                  @GetMapping("/users")
                  public String list() { return "ok"; }
                }
                """);
        Files.writeString(tempDir.resolve("UserServiceTest.java"), """
                import org.junit.jupiter.api.Test;
                public class UserServiceTest {
                  @Test
                  void loads() {}
                }
                """);

        Captured endpoints = run("endpoints", tempDir.toString());
        assertEquals(0, endpoints.code());
        assertTrue(endpoints.stdout().contains("== RepoLens Endpoints =="));
        assertTrue(endpoints.stdout().contains("GET /users"));
        assertTrue(endpoints.stdout().contains("Not inferred at runtime"));

        Captured tests = run("tests", tempDir.toString());
        assertEquals(0, tests.code());
        assertTrue(tests.stdout().contains("== RepoLens Tests =="));
        assertTrue(tests.stdout().contains("inferred from relationships"));
        assertTrue(tests.stdout().contains("symbol=") || tests.stdout().contains("No tests found."));

        Captured traces = run("trace", tempDir.toString());
        assertEquals(0, traces.code());
        assertTrue(traces.stdout().contains("== RepoLens Traces =="));
        assertTrue(traces.stdout().contains("kind=static") || traces.stdout().contains("No traces found."));

        Captured json = run("analyze", tempDir.toString(), "--json");
        JsonNode body = mapper.readTree(json.stdout());
        assertTrue(body.get("endpoints").size() >= 1);
        assertTrue(body.get("tests").isArray());
        assertTrue(body.get("traces").isArray());
        if (body.get("traces").size() > 0) {
            assertEquals("static", body.get("traces").get(0).get("inferenceKind").asText());
        }
    }

    @Test
    void invalidPathFailsWithoutParsing() {
        Captured captured = run("analyze", tempDir.resolve("missing").toString());
        assertEquals(1, captured.code());
        assertTrue(captured.stderr().contains("Analyze failed during ingest"));
        assertTrue(captured.stderr().contains("does not exist"));
    }

    private Captured run(String... args) {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
        try {
            int code = RepoLensCli.execute(args);
            return new Captured(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    private record Captured(int code, String stdout, String err) {
        String stderr() {
            return err;
        }
    }
}

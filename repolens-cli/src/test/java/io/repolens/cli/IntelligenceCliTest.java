package io.repolens.cli;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.repolens.core.model.AnalysisResult;
import io.repolens.ingest.IngestLimits;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

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
    void analyzeKeepsExistingSectionsInOrderAndAppendsSummaryLast() throws Exception {
        writeJavaAndPythonFixture();
        String stdout = run("analyze", tempDir.toString()).stdout();

        int previous = -1;
        for (String heading : List.of(
                "== RepoLens Analyze ==",
                "-- Analyzers --",
                "-- Type symbols (sample) --",
                "-- Intelligence (static, not runtime) --",
                "-- Summary --")) {
            int at = stdout.indexOf(heading);
            assertTrue(at > previous, "'" + heading + "' missing or out of order in:\n" + stdout);
            previous = at;
        }
        assertEquals(stdout.indexOf("-- Summary --"), stdout.lastIndexOf("-- Summary --"),
                "summary must appear exactly once");
    }

    @Test
    void analyzeSummaryCountsFileTypesAndAllInventoriedFiles() throws Exception {
        Files.writeString(tempDir.resolve("Greeter.java"), """
                package demo;

                public class Greeter {
                    public String greet(String name) { return "Hello, " + name; }
                }
                """);
        // A non-source file is inventoried too, so it is counted and listed as its own file type.
        Files.writeString(tempDir.resolve("README.md"), "# Demo\n");
        writeJavaAndPythonFixture();

        String summary = summaryOf(run("analyze", tempDir.toString()));

        assertEquals("java (2), md (1), python (1)", summaryValue(summary, "File types"), summary);
        assertEquals("4 inventoried", summaryValue(summary, "Files"), summary);
        assertEquals("0", summaryValue(summary, "Endpoints"), summary);
        assertTrue(summaryValue(summary, "Modules") != null, summary);
        assertTrue(summaryValue(summary, "Types") != null, summary);
    }

    @Test
    void analyzeSummaryOrdersEqualFileTypeCountsAlphabeticallyAndRepeatably() throws Exception {
        writeJavaAndPythonFixture();

        String first = summaryOf(run("analyze", tempDir.toString()));
        String second = summaryOf(run("analyze", tempDir.toString()));

        assertEquals("java (1), python (1)", summaryValue(first, "File types"), first);
        assertEquals(first, second);
    }

    @Test
    void analyzeSummaryReportsNoIngestionWarningsForCleanRepository() throws Exception {
        writeJavaAndPythonFixture();

        String summary = summaryOf(run("analyze", tempDir.toString()));

        assertEquals("none", summaryValue(summary, "Ingestion warnings"), summary);
    }

    @Test
    void analyzeSummaryListsIngestionWarningForOversizedFile() throws Exception {
        writeJavaAndPythonFixture();
        // One byte over the default per-file limit, so ingestion skips it with a warning.
        Files.write(tempDir.resolve("banner.gif"), new byte[(int) IngestLimits.DEFAULT.maxFileBytes() + 1]);

        String summary = summaryOf(run("analyze", tempDir.toString()));

        // One warning finding is emitted for the skip; the per-file "info" finding must not be counted.
        assertEquals("1", summaryValue(summary, "Ingestion warnings"), summary);
        assertTrue(summary.contains("  - Skipped 1 file exceeding"), summary);
        assertTrue(summary.contains("banner.gif"), summary);
    }

    @Test
    void ingestionWarningsListEveryDistinctWarningOnceInFirstSeenOrder() {
        String skipped = "Skipped 2 files exceeding the 5 MB file-size limit. Paths: a.gif, b.gif";
        String metadata = "Could not fetch GitHub repository metadata for acme/widgets.";
        List<AnalysisResult> analyses = List.of(
                analysis("ingest",
                        finding("ingest-oversized-files", "warning", skipped),
                        finding("ingest-oversized-1", "info", "Skipped oversized file (6291456 bytes): a.gif")),
                analysis("dependencies",
                        finding("dep-finding-1", "warning", "High import fan-out (31) in module demo")),
                analysis("metadata",
                        finding("metadata-github-unavailable", "WARNING", metadata)),
                analysis("ingest",
                        finding("ingest-oversized-files", "warning", skipped)));

        StringBuilder report = new StringBuilder();
        RepoLensCli.appendIngestionWarnings(report, analyses);

        assertEquals(String.join("\n",
                "Ingestion warnings: 2",
                "  - " + skipped,
                "  - " + metadata) + "\n", report.toString());
    }

    @Test
    void ingestionWarningsIgnoreAnalysisWarningsAndInfoFindings() {
        List<AnalysisResult> analyses = List.of(
                analysis("dependencies",
                        finding("dep-finding-1", "warning", "High import fan-out (31) in module demo")),
                analysis("structure",
                        finding("structure-1", "info", "Large module")),
                analysis("ingest",
                        finding("ingest-oversized-1", "info", "Skipped oversized file (6291456 bytes): a.gif")));

        StringBuilder report = new StringBuilder();
        RepoLensCli.appendIngestionWarnings(report, analyses);
        assertEquals("Ingestion warnings: none\n", report.toString());

        StringBuilder empty = new StringBuilder();
        RepoLensCli.appendIngestionWarnings(empty, List.of());
        assertEquals("Ingestion warnings: none\n", empty.toString());
    }

    @Test
    void analyzeJsonIsOneDocumentWithExistingSchemaAndNoSummary() throws Exception {
        writeJavaAndPythonFixture();
        // Include an ingestion warning to prove it only appears inside the JSON payload.
        Files.write(tempDir.resolve("banner.gif"), new byte[(int) IngestLimits.DEFAULT.maxFileBytes() + 1]);

        Captured captured = run("analyze", tempDir.toString(), "--json");
        assertEquals(0, captured.code());
        assertTrue(!captured.stdout().contains("-- Summary --"), captured.stdout());
        assertTrue(!captured.stdout().contains("Ingestion warnings:"), captured.stdout());

        // readTree alone ignores trailing text, so fail on anything after the first document.
        JsonNode json = new ObjectMapper()
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .readTree(captured.stdout());
        assertTrue(json.isObject());
        assertEquals("v1", json.get("schemaVersion").asText());
        for (String field : List.of("repository", "modelStats", "results", "graph", "documentation",
                "symbols", "metadata", "diagrams", "endpoints", "tests", "traces")) {
            assertTrue(json.has(field), "missing top-level field: " + field);
        }
        assertTrue(!json.has("summary"));
        boolean ingestWarningInJson = false;
        for (JsonNode result : json.get("results")) {
            if ("ingest".equals(result.get("analyzerId").asText())) {
                for (JsonNode finding : result.get("findings")) {
                    ingestWarningInJson |= "warning".equals(finding.get("severity").asText());
                }
            }
        }
        assertTrue(ingestWarningInJson, "ingest warning should remain in the JSON results");
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

    /** One Java and one Python source file. */
    private void writeJavaAndPythonFixture() throws Exception {
        Files.writeString(tempDir.resolve("Hello.java"), """
                package demo;

                public class Hello {}
                """);
        Files.writeString(tempDir.resolve("tool.py"), """
                class Tool:
                    def run(self):
                        return 1
                """);
    }

    /** Returns the {@code -- Summary --} block of a successful {@code analyze} run. */
    private static String summaryOf(Captured captured) {
        assertEquals(0, captured.code(), captured.stderr());
        int summaryAt = captured.stdout().indexOf("-- Summary --");
        assertTrue(summaryAt >= 0, "summary block missing:\n" + captured.stdout());
        return captured.stdout().substring(summaryAt);
    }

    /** Value of a {@code Label : value} summary line, ignoring column padding; null when absent. */
    private static String summaryValue(String summary, String label) {
        for (String line : summary.split("\n")) {
            int colon = line.indexOf(':');
            if (colon > 0 && line.substring(0, colon).trim().equals(label)) {
                return line.substring(colon + 1).trim();
            }
        }
        return null;
    }

    private static AnalysisResult analysis(String analyzerId, AnalysisResult.Finding... findings) {
        return new AnalysisResult(analyzerId, analyzerId + " summary", List.of(), List.of(), List.of(findings));
    }

    private static AnalysisResult.Finding finding(String id, String severity, String message) {
        return new AnalysisResult.Finding(id, severity, message, Optional.empty());
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

package io.repolens.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.repolens.analyzers.AnalyzersModule;
import io.repolens.analyzers.GraphViewProjector;
import io.repolens.api.AnalysisResponseDto;
import io.repolens.api.AnalysisResponseMapper;
import io.repolens.core.model.AnalysisResult;
import io.repolens.core.model.GraphView;
import io.repolens.core.model.RepositoryModel;
import io.repolens.core.model.SymbolKind;
import io.repolens.core.pipeline.DefaultAnalysisRunner;
import io.repolens.core.ports.AnalysisRunner;
import io.repolens.core.ports.IngestionException;
import io.repolens.core.ports.RepositoryIngestor;
import io.repolens.ingest.IngestModule;
import io.repolens.ingest.LocalRepositoryIngestor;
import io.repolens.parse.ParseModule;
import io.repolens.parse.ProfiledSourceAnalyzer;
import io.repolens.web.RepoLensServer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thin CLI adapter over RepoLens Core / Web.
 * Parsing and language-specific logic must not live here.
 */
public final class RepoLensCli {

    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private RepoLensCli() {
    }

    public static void main(String[] args) {
        quietNativeProbeLogs();

        if (args.length == 0 || isHelp(args[0])) {
            printUsage();
            return;
        }

        List<String> tokens = new ArrayList<>(List.of(args));
        boolean json = tokens.remove("--json") || hasFormat(tokens, "json");
        Path output = consumeOption(tokens, "-o");
        Integer port = consumeIntOption(tokens, "--port");

        String command = tokens.isEmpty() ? "" : tokens.getFirst();
        try {
            switch (command) {
                case "ingest" -> {
                    requireArg(tokens, 1, "ingest <local-path>");
                    runIngest(tokens.get(1), json, output);
                }
                case "analyze" -> {
                    requireArg(tokens, 1, "analyze <local-path> [--json] [-o file]");
                    runAnalyze(tokens.get(1), json, output);
                }
                case "serve" -> runServe(port == null ? 8080 : port);
                default -> {
                    if (tokens.size() >= 1 && !command.startsWith("-")) {
                        runAnalyze(command, json, output);
                    } else {
                        System.err.println("Unknown command: " + command);
                        printUsage();
                        System.exit(2);
                    }
                }
            }
        } catch (CliException ex) {
            System.err.println(ex.getMessage());
            System.exit(ex.exitCode());
        } catch (Exception ex) {
            System.err.println("Command failed: " + ex.getMessage());
            System.exit(1);
        }
    }

    private static void runIngest(String path, boolean json, Path output) {
        LocalRepositoryIngestor ingestor = IngestModule.localIngestor();
        try {
            RepositoryIngestor.IngestionResult result =
                    ingestor.ingest(RepositoryIngestor.IngestionRequest.local(path));
            if (json) {
                var payload = java.util.Map.of(
                        "repository", result.repository().name(),
                        "origin", result.repository().origin().name(),
                        "workingTree", result.workingTree().toString(),
                        "fileCount", result.inventory().fileCount(),
                        "totalBytes", result.inventory().totalBytes(),
                        "skippedFileCount", result.inventory().skippedFileCount(),
                        "files", result.inventory().files().stream()
                                .limit(100)
                                .map(f -> java.util.Map.of(
                                        "path", f.relativePath(),
                                        "sizeBytes", f.sizeBytes()
                                ))
                                .toList()
                );
                writeOutput(JSON.writeValueAsString(payload), output);
                return;
            }

            String report = """
                    == RepoLens Ingest ==
                    Repository : %s
                    Origin     : %s
                    Path       : %s
                    Files      : %d
                    Bytes      : %d
                    Skipped    : %d
                    """.formatted(
                    result.repository().name(),
                    result.repository().origin(),
                    result.workingTree(),
                    result.inventory().fileCount(),
                    result.inventory().totalBytes(),
                    result.inventory().skippedFileCount()
            );
            StringBuilder sample = new StringBuilder(report);
            sample.append("Sample files:\n");
            result.inventory().files().stream().limit(20).forEach(file ->
                    sample.append("  - ").append(file.relativePath())
                            .append(" (").append(file.sizeBytes()).append(" bytes)\n"));
            writeOutput(sample.toString(), output);
        } catch (IngestionException ex) {
            throw new CliException("Ingest failed: " + ex.getMessage(), 1);
        } catch (Exception ex) {
            throw new CliException("Ingest failed: " + ex.getMessage(), 1);
        }
    }

    private static void runAnalyze(String path, boolean json, Path output) {
        LocalRepositoryIngestor ingestor = IngestModule.localIngestor();
        ProfiledSourceAnalyzer sourceAnalyzer = ParseModule.sourceAnalyzer();
        DefaultAnalysisRunner runner = new DefaultAnalysisRunner(
                ingestor,
                sourceAnalyzer,
                AnalyzersModule.defaultAnalyzers()
        );
        try {
            AnalysisRunner.AnalysisRunResult result =
                    runner.run(new AnalysisRunner.AnalysisRequest(path, false));
            RepositoryModel model = result.model();
            List<AnalysisResult> analyses = result.results();
            GraphView graph = GraphViewProjector.project(model, analyses);
            AnalysisResponseDto dto = AnalysisResponseMapper.from(model, analyses, graph);

            if (json) {
                writeOutput(JSON.writeValueAsString(dto), output);
                return;
            }

            StringBuilder report = new StringBuilder();
            report.append("== RepoLens Analyze ==\n");
            report.append("Repository : ").append(model.repository().name()).append('\n');
            report.append("Parse engine: ").append(sourceAnalyzer.engineId()).append('\n');
            report.append("Files      : ").append(model.fileCount()).append('\n');
            report.append("Modules    : ").append(model.modules().size()).append('\n');
            report.append("Symbols    : ").append(model.symbolCount()).append('\n');
            report.append("Imports    : ").append(model.imports().size()).append('\n');
            report.append("Relationships: ").append(model.relationships().size()).append('\n');
            report.append("Graph      : ").append(graph.nodes().size()).append(" nodes / ")
                    .append(graph.edges().size()).append(" edges\n\n");

            report.append("-- Analyzers --\n");
            for (AnalysisResult analysis : analyses) {
                report.append('[').append(analysis.analyzerId()).append("] ")
                        .append(analysis.summary()).append('\n');
                analysis.findings().stream().limit(8).forEach(finding ->
                        report.append("  - (").append(finding.severity()).append(") ")
                                .append(finding.message()).append('\n'));
            }

            report.append("\n-- Type symbols (sample) --\n");
            model.symbols().stream()
                    .filter(symbol -> symbol.kind() == SymbolKind.CLASS
                            || symbol.kind() == SymbolKind.INTERFACE
                            || symbol.kind() == SymbolKind.ENUM)
                    .limit(20)
                    .forEach(symbol -> report.append("  - ").append(symbol.kind()).append(' ')
                            .append(symbol.name()).append(" @ ")
                            .append(symbol.location().filePath()).append(':')
                            .append(symbol.location().startLine()).append('\n'));

            writeOutput(report.toString(), output);
        } catch (IngestionException ex) {
            throw new CliException("Analyze failed during ingest: " + ex.getMessage(), 1);
        } catch (Exception ex) {
            throw new CliException("Analyze failed: " + ex.getMessage(), 1);
        }
    }

    private static void runServe(int port) {
        RepoLensServer server = RepoLensServer.createDefault(port);
        server.start();
        System.out.println("RepoLens Web API listening on http://localhost:" + port);
        System.out.println("Endpoints: GET /health, POST /v1/analyze, GET /v1/jobs/{id}, GET /v1/jobs/{id}/result");
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        try {
            Thread.currentThread().join();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            server.stop();
        }
    }

    private static void writeOutput(String content, Path output) throws Exception {
        if (output == null) {
            System.out.print(content);
            if (!content.endsWith("\n")) {
                System.out.println();
            }
            return;
        }
        Files.writeString(output, content.endsWith("\n") ? content : content + "\n");
        System.err.println("Wrote " + output.toAbsolutePath());
    }

    private static void quietNativeProbeLogs() {
        Logger.getLogger("io.repolens.parse.engine.SeartTreeSitterEngine").setLevel(Level.WARNING);
    }

    private static boolean hasFormat(List<String> tokens, String format) {
        for (int i = 0; i < tokens.size(); i++) {
            if ("--format".equals(tokens.get(i)) && i + 1 < tokens.size()) {
                String value = tokens.get(i + 1);
                tokens.remove(i + 1);
                tokens.remove(i);
                return format.equalsIgnoreCase(value);
            }
        }
        return false;
    }

    private static Path consumeOption(List<String> tokens, String name) {
        for (int i = 0; i < tokens.size(); i++) {
            if (name.equals(tokens.get(i)) && i + 1 < tokens.size()) {
                Path path = Path.of(tokens.get(i + 1));
                tokens.remove(i + 1);
                tokens.remove(i);
                return path;
            }
        }
        return null;
    }

    private static Integer consumeIntOption(List<String> tokens, String name) {
        for (int i = 0; i < tokens.size(); i++) {
            if (name.equals(tokens.get(i)) && i + 1 < tokens.size()) {
                int value = Integer.parseInt(tokens.get(i + 1));
                tokens.remove(i + 1);
                tokens.remove(i);
                return value;
            }
        }
        return null;
    }

    private static void requireArg(List<String> tokens, int index, String usage) {
        if (tokens.size() <= index) {
            throw new CliException("Missing argument. Usage: " + usage, 2);
        }
    }

    private static boolean isHelp(String value) {
        return "-h".equals(value) || "--help".equals(value) || "help".equals(value);
    }

    private static void printUsage() {
        System.out.println("""
                RepoLens CLI

                Usage:
                  ingest <local-path> [--json] [-o file]
                  analyze <local-path> [--json|--format json] [-o file]
                  serve [--port 8080]
                  <local-path> [--json]

                Notes:
                  - Core analysis works without an LLM.
                  - Web jobs are ephemeral (in-memory).
                  - Remote URL ingest is not implemented yet.
                """);
    }

    private static final class CliException extends RuntimeException {
        private final int exitCode;

        private CliException(String message, int exitCode) {
            super(message);
            this.exitCode = exitCode;
        }

        private int exitCode() {
            return exitCode;
        }
    }
}

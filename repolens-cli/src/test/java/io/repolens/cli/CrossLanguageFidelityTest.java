package io.repolens.cli;

import io.repolens.analyzers.AnalyzersModule;
import io.repolens.analyzers.GraphViewProjector;
import io.repolens.core.model.GraphView;
import io.repolens.core.model.RelationshipType;
import io.repolens.core.model.SymbolKind;
import io.repolens.core.pipeline.DefaultAnalysisRunner;
import io.repolens.core.ports.AnalysisRunner;
import io.repolens.ingest.IngestModule;
import io.repolens.parse.ParseModule;
import io.repolens.parse.ProfiledSourceAnalyzer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end cross-language fidelity through the shared analysis engine.
 */
class CrossLanguageFidelityTest {

    @TempDir
    Path tempDir;

    @Test
    void javascriptRepositoryProducesModulesImportsDependenciesAndGraphNodes() throws Exception {
        write("src/app.js", """
                import util from "./util";
                const helper = require("./helper");
                export function main() {}
                export class App {}
                """);
        write("src/util.js", "export function util() {}\n");
        write("src/helper.js", "export function helper() {}\n");

        AnalysisRunner.AnalysisRunResult result = run(tempDir);
        ProfiledSourceAnalyzer parser = ParseModule.sourceAnalyzer();
        assertTrue(parser.engineId().equals("structural-fallback")
                || parser.engineId().equals("tree-sitter-seart"));

        assertTrue(result.model().fileCount() >= 3);
        assertTrue(result.model().modules().stream().anyMatch(m -> m.name().equals("src/app")));
        assertTrue(result.model().symbols().stream().anyMatch(s ->
                s.name().equals("main") && s.kind() == SymbolKind.FUNCTION));
        assertTrue(result.model().imports().stream().anyMatch(i -> i.rawImport().equals("./util")));
        assertTrue(hasDependsOn(result, "module:src/app", "module:src/util"));
        assertTrue(hasDependsOn(result, "module:src/app", "module:src/helper"));

        GraphView graph = GraphViewProjector.project(result.model(), result.results());
        assertTrue(graph.nodes().stream().anyMatch(n -> n.kind().equals("function")));
        assertTrue(graph.nodes().stream().anyMatch(n -> n.kind().equals("class")));
        assertTrue(graph.edges().stream().anyMatch(e -> e.type().equals("DEPENDS_ON")));
    }

    @Test
    void typescriptRepositoryProducesTypeSymbolsAndDependsOn() throws Exception {
        write("src/main.ts", """
                import type { User } from "./types";
                import { load } from "./loader";
                export function boot() {}
                """);
        write("src/types.ts", """
                export interface User { id: string }
                export type Id = string;
                """);
        write("src/loader.ts", "export function load() {}\n");

        AnalysisRunner.AnalysisRunResult result = run(tempDir);
        assertTrue(result.model().modules().stream().anyMatch(m -> m.name().equals("src/main")));
        assertTrue(result.model().symbols().stream().anyMatch(s ->
                s.name().equals("User") && s.kind() == SymbolKind.INTERFACE));
        assertTrue(result.model().symbols().stream().anyMatch(s ->
                s.name().equals("Id") && s.kind() == SymbolKind.TYPE));
        assertTrue(result.model().imports().stream().anyMatch(i -> i.rawImport().equals("./types")));
        assertTrue(hasDependsOn(result, "module:src/main", "module:src/types"));
        assertTrue(hasDependsOn(result, "module:src/main", "module:src/loader"));

        GraphView graph = GraphViewProjector.project(result.model(), result.results());
        assertTrue(graph.nodes().stream().anyMatch(n -> n.kind().equals("interface")));
        assertTrue(graph.nodes().stream().anyMatch(n -> n.kind().equals("type")));
        assertTrue(graph.nodes().stream().anyMatch(n -> n.kind().equals("function")));
        assertTrue(graph.edges().stream().anyMatch(e -> e.type().equals("DEPENDS_ON")));
    }

    @Test
    void pythonRepositoryProducesModulesImportsDependenciesAndGraphNodes() throws Exception {
        write("app/main.py", """
                from .utils import helper
                import package.core
                class Service:
                    def run(self):
                        pass
                def start():
                    pass
                """);
        write("app/utils.py", "def helper():\n    pass\n");
        write("package/core.py", "VALUE = 1\n");

        AnalysisRunner.AnalysisRunResult result = run(tempDir);
        assertTrue(result.model().modules().stream().anyMatch(m -> m.name().equals("app/main")));
        assertTrue(result.model().modules().stream().anyMatch(m -> m.name().equals("app/utils")));
        assertTrue(result.model().symbols().stream().anyMatch(s ->
                s.name().equals("Service") && s.kind() == SymbolKind.CLASS));
        assertTrue(result.model().symbols().stream().anyMatch(s ->
                s.name().equals("start") && s.kind() == SymbolKind.FUNCTION));
        assertTrue(result.model().imports().stream().anyMatch(i -> i.rawImport().equals(".utils")));
        assertTrue(hasDependsOn(result, "module:app/main", "module:app/utils"));
        assertTrue(hasDependsOn(result, "module:app/main", "module:package/core"));

        GraphView graph = GraphViewProjector.project(result.model(), result.results());
        assertTrue(graph.nodes().stream().anyMatch(n -> n.kind().equals("class")));
        assertTrue(graph.nodes().stream().anyMatch(n -> n.kind().equals("function")));
        assertTrue(graph.edges().stream().anyMatch(e -> e.type().equals("DEPENDS_ON")));
    }

    @Test
    void javaPackageDependencyStillResolves() throws Exception {
        write("src/demo/core/Core.java", """
                package demo.core;
                public class Core {}
                """);
        write("src/demo/app/App.java", """
                package demo.app;
                import demo.core.Core;
                public class App {
                  public void run() {}
                }
                """);

        AnalysisRunner.AnalysisRunResult result = run(tempDir);
        assertTrue(result.model().modules().stream().anyMatch(m -> m.name().equals("demo.core")));
        assertTrue(result.model().modules().stream().anyMatch(m -> m.name().equals("demo.app")));
        assertTrue(result.model().symbols().stream().anyMatch(s ->
                s.name().equals("App") && s.kind() == SymbolKind.CLASS));
        assertTrue(hasDependsOn(result, "module:demo.app", "module:demo.core"));

        GraphView graph = GraphViewProjector.project(result.model(), result.results());
        assertTrue(graph.nodes().stream().anyMatch(n -> n.kind().equals("class")));
        assertTrue(graph.nodes().stream().anyMatch(n -> n.kind().equals("method") && n.label().equals("run")));
        assertTrue(graph.edges().stream().anyMatch(e -> e.type().equals("DEPENDS_ON")));
    }

    private static boolean hasDependsOn(AnalysisRunner.AnalysisRunResult result, String from, String to) {
        return result.results().stream()
                .flatMap(r -> r.relationships().stream())
                .anyMatch(rel -> rel.type() == RelationshipType.DEPENDS_ON
                        && rel.fromId().equals(from)
                        && rel.toId().equals(to));
    }

    private static AnalysisRunner.AnalysisRunResult run(Path root) {
        DefaultAnalysisRunner runner = new DefaultAnalysisRunner(
                IngestModule.localIngestor(),
                ParseModule.sourceAnalyzer(),
                AnalyzersModule.defaultAnalyzers()
        );
        return runner.run(new AnalysisRunner.AnalysisRequest(root.toString(), false));
    }

    private void write(String relative, String content) throws Exception {
        Path file = tempDir.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase A end-to-end fidelity: Go, Rust, C#, Kotlin + mixed Java repo.
 */
class PhaseALanguageFidelityTest {

    @TempDir
    Path tempDir;

    @Test
    void goRepositoryProducesModulesImportsDependenciesAndGraph() throws Exception {
        write("demo/util/util.go", """
                package util

                func Helper() string { return "ok" }
                """);
        write("demo/app/app.go", """
                package app

                import "demo/util"

                type Service struct{}

                func Main() {}
                """);

        AnalysisRunner.AnalysisRunResult result = run(tempDir);
        assertTrue(result.model().modules().stream().anyMatch(m -> m.name().equals("demo/app")));
        assertTrue(result.model().modules().stream().anyMatch(m -> m.name().equals("demo/util")));
        assertTrue(result.model().symbols().stream().anyMatch(s ->
                s.name().equals("Service") && s.kind() == SymbolKind.CLASS));
        assertTrue(result.model().symbols().stream().anyMatch(s ->
                s.name().equals("Main") && s.kind() == SymbolKind.FUNCTION));
        assertTrue(result.model().imports().stream().anyMatch(i -> i.rawImport().equals("demo/util")));
        assertTrue(hasDependsOn(result, "module:demo/app", "module:demo/util"));

        GraphView graph = GraphViewProjector.project(result.model(), result.results());
        assertTrue(graph.nodes().stream().anyMatch(n -> n.kind().equals("class")));
        assertTrue(graph.nodes().stream().anyMatch(n -> n.kind().equals("function")));
        assertTrue(graph.edges().stream().anyMatch(e -> e.type().equals("DEPENDS_ON")));
        assertFalse(graph.nodes().stream().anyMatch(n -> n.kind().equals("method")));
    }

    @Test
    void rustRepositoryProducesModulesImportsDependenciesAndGraph() throws Exception {
        write("util.rs", """
                pub fn helper() -> i32 { 1 }
                """);
        write("main.rs", """
                use crate::util::helper;

                pub struct App {}
                pub trait Runner {}
                pub fn main() {}
                """);

        AnalysisRunner.AnalysisRunResult result = run(tempDir);
        assertTrue(result.model().modules().stream().anyMatch(m -> m.name().equals("main")));
        assertTrue(result.model().modules().stream().anyMatch(m -> m.name().equals("util")));
        assertTrue(result.model().symbols().stream().anyMatch(s ->
                s.name().equals("App") && s.kind() == SymbolKind.CLASS));
        assertTrue(result.model().symbols().stream().anyMatch(s ->
                s.name().equals("Runner") && s.kind() == SymbolKind.INTERFACE));
        assertTrue(result.model().imports().stream().anyMatch(i -> i.rawImport().equals("util")));
        assertTrue(hasDependsOn(result, "module:main", "module:util"));

        GraphView graph = GraphViewProjector.project(result.model(), result.results());
        assertTrue(graph.nodes().stream().anyMatch(n -> n.kind().equals("class")));
        assertTrue(graph.nodes().stream().anyMatch(n -> n.kind().equals("interface")));
        assertTrue(graph.edges().stream().anyMatch(e -> e.type().equals("DEPENDS_ON")));
    }

    @Test
    void csharpRepositoryProducesModulesImportsDependenciesAndGraph() throws Exception {
        write("Demo/Core/Core.cs", """
                namespace Demo.Core;
                public class Core {}
                """);
        write("Demo/App/App.cs", """
                namespace Demo.App;
                using Demo.Core;
                public class App {
                  public void Run() {}
                }
                public interface IHandler {}
                """);

        AnalysisRunner.AnalysisRunResult result = run(tempDir);
        assertTrue(result.model().modules().stream().anyMatch(m -> m.name().equals("Demo.App")));
        assertTrue(result.model().modules().stream().anyMatch(m -> m.name().equals("Demo.Core")));
        assertTrue(result.model().symbols().stream().anyMatch(s ->
                s.name().equals("App") && s.kind() == SymbolKind.CLASS));
        assertTrue(result.model().symbols().stream().anyMatch(s ->
                s.name().equals("IHandler") && s.kind() == SymbolKind.INTERFACE));
        assertTrue(hasDependsOn(result, "module:Demo.App", "module:Demo.Core"));

        GraphView graph = GraphViewProjector.project(result.model(), result.results());
        assertTrue(graph.nodes().stream().anyMatch(n -> n.kind().equals("class")));
        assertTrue(graph.nodes().stream().anyMatch(n -> n.kind().equals("interface")));
        assertTrue(graph.edges().stream().anyMatch(e -> e.type().equals("DEPENDS_ON")));
        assertFalse(graph.nodes().stream().anyMatch(n -> n.kind().equals("method")));
    }

    @Test
    void kotlinRepositoryProducesModulesImportsDependenciesAndGraph() throws Exception {
        write("demo/core/Core.kt", """
                package demo.core
                class Core
                """);
        write("demo/app/App.kt", """
                package demo.app
                import demo.core.Core
                class App {
                    fun run() {}
                }
                fun boot() {}
                """);

        AnalysisRunner.AnalysisRunResult result = run(tempDir);
        assertTrue(result.model().modules().stream().anyMatch(m -> m.name().equals("demo.app")));
        assertTrue(result.model().modules().stream().anyMatch(m -> m.name().equals("demo.core")));
        assertTrue(result.model().symbols().stream().anyMatch(s ->
                s.name().equals("App") && s.kind() == SymbolKind.CLASS));
        assertTrue(result.model().symbols().stream().anyMatch(s ->
                s.name().equals("boot") && s.kind() == SymbolKind.FUNCTION));
        assertTrue(hasDependsOn(result, "module:demo.app", "module:demo.core"));

        GraphView graph = GraphViewProjector.project(result.model(), result.results());
        assertTrue(graph.nodes().stream().anyMatch(n -> n.kind().equals("class")));
        assertTrue(graph.nodes().stream().anyMatch(n -> n.kind().equals("function")));
        assertTrue(graph.edges().stream().anyMatch(e -> e.type().equals("DEPENDS_ON")));
        assertFalse(graph.nodes().stream().anyMatch(n -> n.kind().equals("method")));
    }

    @Test
    void mixedJavaAndKotlinRepositoryAnalyzesBothLanguages() throws Exception {
        write("src/demo/core/Core.java", """
                package demo.core;
                public class Core {}
                """);
        write("src/demo/app/App.kt", """
                package demo.app
                import demo.core.Core
                class App
                """);

        AnalysisRunner.AnalysisRunResult result = run(tempDir);
        assertTrue(result.model().files().stream().anyMatch(f -> f.language().equals("java")));
        assertTrue(result.model().files().stream().anyMatch(f -> f.language().equals("kotlin")));
        assertTrue(result.model().modules().stream().anyMatch(m -> m.name().equals("demo.core")));
        assertTrue(result.model().modules().stream().anyMatch(m -> m.name().equals("demo.app")));
        assertTrue(hasDependsOn(result, "module:demo.app", "module:demo.core"));

        GraphView graph = GraphViewProjector.project(result.model(), result.results());
        assertTrue(graph.nodes().stream().anyMatch(n -> n.kind().equals("class")));
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
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.writeString(file, content);
    }
}

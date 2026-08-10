package io.repolens.analyzers;

import io.repolens.core.model.Import;
import io.repolens.core.model.Module;
import io.repolens.core.model.RelationshipType;
import io.repolens.core.model.Repository;
import io.repolens.core.model.RepositoryModel;
import io.repolens.core.model.SourceFile;
import io.repolens.core.model.SourceLocation;
import io.repolens.core.model.Symbol;
import io.repolens.core.model.SymbolKind;
import io.repolens.core.model.AnalysisResult;
import io.repolens.core.model.GraphView;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreAnalyzersTest {

    @Test
    void structureMetricsAndDependencyEdgesAreDerivedFromModel() {
        RepositoryModel model = sampleModel();

        AnalysisResult structure = new StructureAnalyzer().analyze(model);
        assertEquals("structure", structure.analyzerId());
        assertTrue(structure.metrics().stream().anyMatch(m -> m.name().equals("module_count") && m.value() == 2));
        assertTrue(structure.metrics().stream().anyMatch(m -> m.name().equals("symbol_count_class") && m.value() == 2));

        AnalysisResult dependencies = new DependencyAnalyzer().analyze(model);
        assertEquals("dependencies", dependencies.analyzerId());
        assertTrue(dependencies.relationships().stream()
                .anyMatch(r -> r.type() == RelationshipType.DEPENDS_ON
                        && r.fromId().equals("module:demo.app")
                        && r.toId().equals("module:demo.core")));

        AnalysisResult metrics = new MetricsAnalyzer().analyze(model);
        assertEquals("metrics", metrics.analyzerId());
        assertTrue(metrics.metrics().stream().anyMatch(m -> m.name().equals("total_source_bytes") && m.value() == 30));

        GraphView graph = GraphViewProjector.project(model, List.of(structure, dependencies, metrics));
        assertTrue(graph.nodes().stream().anyMatch(n -> n.kind().equals("repository")));
        assertTrue(graph.nodes().stream().anyMatch(n -> n.kind().equals("module")));
        assertTrue(graph.nodes().stream().anyMatch(n -> n.kind().equals("class")));
        assertTrue(graph.edges().stream().anyMatch(e -> e.type().equals("DEPENDS_ON")));
        assertFalse(graph.nodes().stream().anyMatch(n -> n.kind().equals("method")));
    }

    @Test
    void defaultAnalyzersAreWired() {
        assertEquals(3, AnalyzersModule.defaultAnalyzers().size());
    }

    @Test
    void resolvesRelativeJavascriptStyleImportsToDependsOn() {
        RepositoryModel model = RepositoryModel.builder(Repository.local("r1", "js", "/tmp/js"))
                .addFile(new SourceFile("src/app.js", "javascript", "h1", 10))
                .addFile(new SourceFile("src/util.js", "javascript", "h2", 10))
                .addModule(Module.of("module:src/app", "src/app", "javascript"))
                .addModule(Module.of("module:src/util", "src/util", "javascript"))
                .addSymbol(new Symbol(
                        "sym:main",
                        "main",
                        SymbolKind.FUNCTION,
                        Optional.empty(),
                        Optional.of("module:src/app"),
                        SourceLocation.ofFile("src/app.js"),
                        Optional.empty()
                ))
                .addSymbol(new Symbol(
                        "sym:util",
                        "util",
                        SymbolKind.FUNCTION,
                        Optional.empty(),
                        Optional.of("module:src/util"),
                        SourceLocation.ofFile("src/util.js"),
                        Optional.empty()
                ))
                .addImport(new Import(
                        "import:1",
                        "src/app.js",
                        "./util",
                        Optional.empty(),
                        SourceLocation.ofFile("src/app.js")
                ))
                .build();

        AnalysisResult dependencies = new DependencyAnalyzer().analyze(model);
        assertTrue(dependencies.relationships().stream()
                .anyMatch(r -> r.type() == RelationshipType.DEPENDS_ON
                        && r.fromId().equals("module:src/app")
                        && r.toId().equals("module:src/util")));

        GraphView graph = GraphViewProjector.project(model, List.of(dependencies));
        assertTrue(graph.nodes().stream().anyMatch(n -> n.kind().equals("function") && n.label().equals("main")));
        assertTrue(graph.edges().stream().anyMatch(e -> e.type().equals("DEPENDS_ON")));
    }

    @Test
    void resolvesPythonRelativeAndPackageImports() {
        RepositoryModel model = RepositoryModel.builder(Repository.local("r1", "py", "/tmp/py"))
                .addFile(new SourceFile("app/main.py", "python", "h1", 10))
                .addFile(new SourceFile("app/utils.py", "python", "h2", 10))
                .addFile(new SourceFile("package/core.py", "python", "h3", 10))
                .addModule(Module.of("module:app/main", "app/main", "python"))
                .addModule(Module.of("module:app/utils", "app/utils", "python"))
                .addModule(Module.of("module:package/core", "package/core", "python"))
                .addSymbol(new Symbol(
                        "sym:1",
                        "start",
                        SymbolKind.FUNCTION,
                        Optional.empty(),
                        Optional.of("module:app/main"),
                        SourceLocation.ofFile("app/main.py"),
                        Optional.empty()
                ))
                .addImport(new Import(
                        "import:1",
                        "app/main.py",
                        ".utils",
                        Optional.empty(),
                        SourceLocation.ofFile("app/main.py")
                ))
                .addImport(new Import(
                        "import:2",
                        "app/main.py",
                        "package.core",
                        Optional.empty(),
                        SourceLocation.ofFile("app/main.py")
                ))
                .build();

        AnalysisResult dependencies = new DependencyAnalyzer().analyze(model);
        assertTrue(dependencies.relationships().stream()
                .anyMatch(r -> r.type() == RelationshipType.DEPENDS_ON
                        && r.fromId().equals("module:app/main")
                        && r.toId().equals("module:app/utils")));
        assertTrue(dependencies.relationships().stream()
                .anyMatch(r -> r.type() == RelationshipType.DEPENDS_ON
                        && r.fromId().equals("module:app/main")
                        && r.toId().equals("module:package/core")));
    }

    @Test
    void graphIncludesTopLevelFunctionsButNotMethods() {
        RepositoryModel model = sampleModel();
        GraphView graph = GraphViewProjector.project(model, List.of());
        assertFalse(graph.nodes().stream().anyMatch(n -> n.kind().equals("method")));

        RepositoryModel withFunctions = RepositoryModel.builder(Repository.local("r2", "fn", "/tmp/fn"))
                .addFile(new SourceFile("src/app.js", "javascript", "h1", 10))
                .addModule(Module.of("module:src/app", "src/app", "javascript"))
                .addSymbol(new Symbol(
                        "sym:fn",
                        "bootstrap",
                        SymbolKind.FUNCTION,
                        Optional.empty(),
                        Optional.of("module:src/app"),
                        SourceLocation.ofFile("src/app.js"),
                        Optional.empty()
                ))
                .addSymbol(new Symbol(
                        "sym:method",
                        "inner",
                        SymbolKind.METHOD,
                        Optional.empty(),
                        Optional.of("module:src/app"),
                        SourceLocation.ofFile("src/app.js"),
                        Optional.empty()
                ))
                .build();

        GraphView fnGraph = GraphViewProjector.project(withFunctions, List.of());
        assertTrue(fnGraph.nodes().stream().anyMatch(n -> n.kind().equals("function") && n.label().equals("bootstrap")));
        assertFalse(fnGraph.nodes().stream().anyMatch(n -> n.kind().equals("method")));
    }

    @Test
    void resolvesJavaImportsUnderSrcLayout() {
        RepositoryModel model = RepositoryModel.builder(Repository.local("r1", "java", "/tmp/java"))
                .addFile(new SourceFile("src/demo/core/Core.java", "java", "h1", 10))
                .addFile(new SourceFile("src/demo/app/App.java", "java", "h2", 20))
                .addModule(Module.of("module:demo.core", "demo.core", "java"))
                .addModule(Module.of("module:demo.app", "demo.app", "java"))
                .addSymbol(new Symbol(
                        "sym:1",
                        "Core",
                        SymbolKind.CLASS,
                        Optional.empty(),
                        Optional.of("module:demo.core"),
                        SourceLocation.ofFile("src/demo/core/Core.java"),
                        Optional.empty()
                ))
                .addSymbol(new Symbol(
                        "sym:2",
                        "App",
                        SymbolKind.CLASS,
                        Optional.empty(),
                        Optional.of("module:demo.app"),
                        SourceLocation.ofFile("src/demo/app/App.java"),
                        Optional.empty()
                ))
                .addImport(new Import(
                        "import:1",
                        "src/demo/app/App.java",
                        "demo.core.Core",
                        Optional.empty(),
                        SourceLocation.ofFile("src/demo/app/App.java")
                ))
                .build();

        AnalysisResult dependencies = new DependencyAnalyzer().analyze(model);
        assertTrue(dependencies.relationships().stream()
                .anyMatch(r -> r.type() == RelationshipType.DEPENDS_ON
                        && r.fromId().equals("module:demo.app")
                        && r.toId().equals("module:demo.core")),
                () -> "relationships=" + dependencies.relationships()
                        + " summary=" + dependencies.summary());
    }

    private static RepositoryModel sampleModel() {
        return RepositoryModel.builder(Repository.local("r1", "sample", "/tmp/sample"))
                .addFile(new SourceFile("demo/core/Core.java", "java", "h1", 10))
                .addFile(new SourceFile("demo/app/App.java", "java", "h2", 20))
                .addModule(Module.of("module:demo.core", "demo.core", "java"))
                .addModule(Module.of("module:demo.app", "demo.app", "java"))
                .addSymbol(new Symbol(
                        "sym:1",
                        "Core",
                        SymbolKind.CLASS,
                        Optional.empty(),
                        Optional.of("module:demo.core"),
                        SourceLocation.ofFile("demo/core/Core.java"),
                        Optional.empty()
                ))
                .addSymbol(new Symbol(
                        "sym:2",
                        "App",
                        SymbolKind.CLASS,
                        Optional.empty(),
                        Optional.of("module:demo.app"),
                        SourceLocation.ofFile("demo/app/App.java"),
                        Optional.empty()
                ))
                .addSymbol(new Symbol(
                        "sym:3",
                        "run",
                        SymbolKind.METHOD,
                        Optional.of("sym:2"),
                        Optional.of("module:demo.app"),
                        new SourceLocation("demo/app/App.java", 4, 0, 4, 10),
                        Optional.empty()
                ))
                .addImport(new Import(
                        "import:1",
                        "demo/app/App.java",
                        "demo.core.Core",
                        Optional.empty(),
                        SourceLocation.ofFile("demo/app/App.java")
                ))
                .build();
    }
}

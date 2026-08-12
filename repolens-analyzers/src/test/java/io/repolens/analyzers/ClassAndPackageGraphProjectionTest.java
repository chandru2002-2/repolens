package io.repolens.analyzers;

import io.repolens.core.model.AnalysisResult;
import io.repolens.core.model.GraphView;
import io.repolens.core.model.Module;
import io.repolens.core.model.Relationship;
import io.repolens.core.model.RelationshipType;
import io.repolens.core.model.Repository;
import io.repolens.core.model.RepositoryModel;
import io.repolens.core.model.SourceFile;
import io.repolens.core.model.SourceLocation;
import io.repolens.core.model.Symbol;
import io.repolens.core.model.SymbolKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassAndPackageGraphProjectionTest {

    @Test
    void projectsPackageDependenciesAndClassInheritance() {
        RepositoryModel model = RepositoryModel.builder(Repository.local("r1", "demo", "/tmp/demo"))
                .addFile(new SourceFile("a/A.java", "java", "h", 10))
                .addFile(new SourceFile("b/B.java", "java", "h", 10))
                .addModule(Module.of("module:a", "a", "java"))
                .addModule(Module.of("module:b", "b", "java"))
                .addSymbol(new Symbol(
                        "sym:A", "A", SymbolKind.CLASS, Optional.empty(), Optional.of("module:a"),
                        new SourceLocation("a/A.java", 1, 0, 1, 1), Optional.empty()))
                .addSymbol(new Symbol(
                        "sym:B", "B", SymbolKind.CLASS, Optional.empty(), Optional.of("module:b"),
                        new SourceLocation("b/B.java", 1, 0, 1, 1), Optional.empty()))
                .addSymbol(new Symbol(
                        "sym:I", "I", SymbolKind.INTERFACE, Optional.empty(), Optional.of("module:b"),
                        new SourceLocation("b/B.java", 2, 0, 2, 1), Optional.empty()))
                .addSymbol(new Symbol(
                        "sym:Bf", "id", SymbolKind.FIELD, Optional.of("sym:B"), Optional.of("module:b"),
                        new SourceLocation("b/B.java", 3, 0, 3, 1), Optional.empty()))
                .addSymbol(new Symbol(
                        "sym:Bm", "run", SymbolKind.METHOD, Optional.of("sym:B"), Optional.of("module:b"),
                        new SourceLocation("b/B.java", 4, 0, 4, 1), Optional.empty()))
                .addRelationship(Relationship.of("r1", RelationshipType.EXTENDS, "sym:B", "sym:A"))
                .addRelationship(Relationship.of("r2", RelationshipType.IMPLEMENTS, "sym:B", "sym:I"))
                .build();

        AnalysisResult deps = new AnalysisResult(
                "dependencies",
                "ok",
                List.of(),
                List.of(Relationship.of("d1", RelationshipType.DEPENDS_ON, "module:b", "module:a")),
                List.of()
        );

        GraphView graph = GraphViewProjector.project(model, List.of(deps));

        assertTrue(graph.nodes().stream().anyMatch(n -> n.kind().equals("module") && n.label().equals("a")));
        assertTrue(graph.nodes().stream().anyMatch(n -> n.kind().equals("class") && n.label().equals("B")));
        assertTrue(graph.nodes().stream().anyMatch(n -> n.kind().equals("field") && n.label().equals("id")));
        assertTrue(graph.nodes().stream().anyMatch(n -> n.kind().equals("method") && n.label().equals("run")));
        assertTrue(graph.edges().stream().anyMatch(e -> e.type().equals("DEPENDS_ON")));
        assertTrue(graph.edges().stream().anyMatch(e -> e.type().equals("EXTENDS")));
        assertTrue(graph.edges().stream().anyMatch(e -> e.type().equals("IMPLEMENTS")));
    }
}

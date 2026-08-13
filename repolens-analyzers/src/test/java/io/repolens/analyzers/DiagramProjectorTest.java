package io.repolens.analyzers;

import io.repolens.core.model.NamedDiagram;
import io.repolens.core.model.Repository;
import io.repolens.core.model.RepositoryModel;
import io.repolens.core.model.SourceFile;
import io.repolens.core.model.SourceLocation;
import io.repolens.core.model.StructuralFact;
import io.repolens.core.model.Symbol;
import io.repolens.core.model.SymbolKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagramProjectorTest {

    @Test
    void sequenceProjectsControllerServiceRepositoryChain() {
        RepositoryModel model = base()
                .addSymbol(type("sym:c", "UserController", "c/UserController.java"))
                .addSymbol(type("sym:s", "UserService", "s/UserService.java"))
                .addSymbol(type("sym:r", "UserRepository", "r/UserRepository.java"))
                .addStructuralFact(fact("f1", "sequence", "controller", "UserController", "sym:c", null))
                .addStructuralFact(fact("f2", "sequence", "service", "UserService", "sym:s", null))
                .addStructuralFact(fact("f3", "sequence", "repository", "UserRepository", "sym:r", null))
                .addStructuralFact(fact("f4", "sequence", "call", "UserController->UserService", "sym:c", "sym:s"))
                .addStructuralFact(fact("f5", "sequence", "database", "Database", null, null))
                .build();

        NamedDiagram diagram = DiagramProjector.projectSequence(model);
        assertTrue(diagram.graph().nodes().stream().anyMatch(n -> n.kind().equals("actor")));
        assertTrue(diagram.graph().nodes().stream().anyMatch(n -> n.label().equals("UserController")));
        assertTrue(diagram.graph().edges().stream().anyMatch(e -> e.type().startsWith("CALLS")));
        assertTrue(diagram.emptyMessage().isEmpty());
    }

    @Test
    void erProjectsEntityRelationships() {
        RepositoryModel model = base()
                .addSymbol(type("sym:u", "User", "User.java"))
                .addSymbol(type("sym:o", "Order", "Order.java"))
                .addStructuralFact(fact("e1", "er", "entity", "User", "sym:u", null))
                .addStructuralFact(fact("e2", "er", "entity", "Order", "sym:o", null))
                .addStructuralFact(fact("e3", "er", "one_to_many", "User->Order", "sym:u", "sym:o"))
                .addStructuralFact(fact("e4", "er", "primary_key", "id", "sym:u", null))
                .build();

        NamedDiagram diagram = DiagramProjector.projectEr(model);
        assertTrue(diagram.graph().nodes().stream().anyMatch(n -> n.kind().equals("entity")));
        assertTrue(diagram.graph().edges().stream().anyMatch(e -> e.type().equals("ONE_TO_MANY")));
    }

    @Test
    void emptyStatesDoNotInventRelationships() {
        RepositoryModel model = base().build();
        assertTrue(DiagramProjector.projectEr(model).emptyMessage().isPresent());
        assertTrue(DiagramProjector.projectSequence(model).emptyMessage().isPresent());
        assertTrue(DiagramProjector.projectDeployment(model).emptyMessage().isPresent());
        assertTrue(DiagramProjector.projectDfd(model).emptyMessage().isPresent());
    }

    @Test
    void deploymentProjectsComposeServices() {
        RepositoryModel model = base()
                .addStructuralFact(fact("d1", "deployment", "service", "api", null, null))
                .addStructuralFact(fact("d2", "deployment", "database", "mysql", null, null))
                .addStructuralFact(fact("d3", "deployment", "links", "api->mysql", null, null))
                .build();
        NamedDiagram diagram = DiagramProjector.projectDeployment(model);
        assertTrue(diagram.graph().nodes().stream().anyMatch(n -> n.label().equals("api")));
        assertTrue(diagram.graph().nodes().stream().anyMatch(n -> n.kind().equals("database")));
    }

    @Test
    void useCaseAndStateMachineProjectFromFacts() {
        RepositoryModel model = base()
                .addSymbol(type("sym:c", "UserController", "UserController.java"))
                .addSymbol(new Symbol(
                        "sym:e", "Status", SymbolKind.ENUM, Optional.empty(), Optional.empty(),
                        new SourceLocation("Status.java", 1, 0, 1, 1), Optional.empty()))
                .addFile(new SourceFile("Status.java", "java", "h", 10))
                .addStructuralFact(fact("u1", "usecase", "use_case", "Get User", "sym:c", null))
                .addStructuralFact(fact("s1", "state", "state", "PENDING", "sym:e", null))
                .addStructuralFact(fact("s2", "state", "state", "DONE", "sym:e", null))
                .addStructuralFact(fact("s3", "state", "transition", "PENDING->DONE", "sym:e", null))
                .build();

        NamedDiagram useCase = DiagramProjector.projectUseCase(model);
        assertTrue(useCase.graph().nodes().stream().anyMatch(n -> n.kind().equals("use_case")));
        NamedDiagram state = DiagramProjector.projectStateMachine(model);
        assertTrue(state.graph().edges().stream().anyMatch(e -> e.type().equals("TRANSITION")));
    }

    @Test
    void respectsNodeLimitTruncationFlag() {
        RepositoryModel.Builder builder = base();
        for (int i = 0; i < DiagramProjector.MAX_NODES + 20; i++) {
            builder.addStructuralFact(fact("n" + i, "deployment", "service", "svc" + i, null, null));
        }
        NamedDiagram diagram = DiagramProjector.projectDeployment(builder.build());
        assertTrue(diagram.truncated());
        assertEquals(DiagramProjector.MAX_NODES, diagram.graph().nodes().size());
        assertTrue(diagram.totalNodeCount() > DiagramProjector.MAX_NODES);
    }

    private static RepositoryModel.Builder base() {
        return RepositoryModel.builder(Repository.local("r1", "demo", "/tmp/demo"))
                .addFile(new SourceFile("c/UserController.java", "java", "h", 10))
                .addFile(new SourceFile("s/UserService.java", "java", "h", 10))
                .addFile(new SourceFile("r/UserRepository.java", "java", "h", 10))
                .addFile(new SourceFile("User.java", "java", "h", 10))
                .addFile(new SourceFile("Order.java", "java", "h", 10))
                .addFile(new SourceFile("UserController.java", "java", "h", 10));
    }

    private static Symbol type(String id, String name, String path) {
        return new Symbol(
                id,
                name,
                SymbolKind.CLASS,
                Optional.empty(),
                Optional.empty(),
                new SourceLocation(path, 1, 0, 1, 1),
                Optional.empty()
        );
    }

    private static StructuralFact fact(
            String id,
            String category,
            String kind,
            String label,
            String source,
            String target
    ) {
        return new StructuralFact(
                id,
                category,
                kind,
                label,
                Optional.ofNullable(source),
                Optional.ofNullable(target),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
    }
}

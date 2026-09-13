package io.repolens.analyzers;

import io.repolens.core.model.Evidence;
import io.repolens.core.model.Import;
import io.repolens.core.model.InferenceMethod;
import io.repolens.core.model.Module;
import io.repolens.core.model.Relationship;
import io.repolens.core.model.RelationshipType;
import io.repolens.core.model.Repository;
import io.repolens.core.model.RepositoryModel;
import io.repolens.core.model.SourceFile;
import io.repolens.core.model.SourceLocation;
import io.repolens.core.model.Symbol;
import io.repolens.core.model.SymbolKind;
import io.repolens.core.model.Test;
import io.repolens.core.model.AnalysisResult;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSubjectAnalyzerTest {

    @org.junit.jupiter.api.Test
    void linksTestMethodToProductionMethodViaCalls() {
        RepositoryModel model = base()
                .addSymbol(method("sym:testMethod", "loads", "sym:testClass"))
                .addSymbol(method("sym:prodMethod", "find", "sym:prodClass"))
                .addTest(testFact("test:1", "sym:testMethod"))
                .addRelationship(new Relationship(
                        "call-rel-1",
                        RelationshipType.CALLS,
                        "sym:testMethod",
                        "sym:prodMethod",
                        0.90,
                        Optional.of("call:find;confidence=high;via=field")
                ))
                .build();

        AnalysisResult result = new TestSubjectAnalyzer().analyze(model);
        Relationship tests = onlyTests(result);
        assertEquals("sym:testMethod", tests.fromId());
        assertEquals("sym:prodMethod", tests.toId());
        assertEquals(0.90, tests.confidence());
        assertEquals(InferenceMethod.TYPE_RESOLUTION, tests.evidence().orElseThrow().inferenceMethod());
        assertEquals("call-rel-1", tests.evidence().orElseThrow().referencedId().orElseThrow());
        assertEquals(1, model.relationships().stream().filter(r -> r.type() == RelationshipType.CALLS).count());
    }

    @org.junit.jupiter.api.Test
    void linksTestClassToProductionClassViaCalls() {
        RepositoryModel model = base()
                .addTest(testFact("test:1", "sym:testClass"))
                .addRelationship(Relationship.of("call-rel-1", RelationshipType.CALLS, "sym:testClass", "sym:prodClass"))
                .build();

        Relationship tests = onlyTests(new TestSubjectAnalyzer().analyze(model));
        assertEquals("sym:testClass", tests.fromId());
        assertEquals("sym:prodClass", tests.toId());
    }

    @org.junit.jupiter.api.Test
    void propagatesNameHeuristicCallEvidence() {
        RepositoryModel model = base()
                .addTest(testFact("test:1", "sym:testClass"))
                .addRelationship(new Relationship(
                        "call-rel-1",
                        RelationshipType.CALLS,
                        "sym:testClass",
                        "sym:prodClass",
                        0.55,
                        Optional.of("call:find;confidence=medium;via=name-heuristic")
                ))
                .build();

        Relationship tests = onlyTests(new TestSubjectAnalyzer().analyze(model));
        assertEquals(0.55, tests.confidence());
        assertEquals(InferenceMethod.NAME_HEURISTIC, tests.evidence().orElseThrow().inferenceMethod());
        assertTrue(tests.provenance().orElse("").contains("call:find"));
    }

    @org.junit.jupiter.api.Test
    void doesNotLinkTestToTest() {
        RepositoryModel model = base()
                .addSymbol(type("sym:otherTest", "OtherTest", "OtherTest.java"))
                .addFile(new SourceFile("OtherTest.java", "java", "h", 10))
                .addTest(testFact("test:1", "sym:testClass"))
                .addTest(testFact("test:2", "sym:otherTest"))
                .addRelationship(Relationship.of("call-rel-1", RelationshipType.CALLS, "sym:testClass", "sym:otherTest"))
                .build();

        AnalysisResult result = new TestSubjectAnalyzer().analyze(model);
        assertTrue(result.relationships().isEmpty());
    }

    @org.junit.jupiter.api.Test
    void doesNotInventSubjectWithoutCallsOrImports() {
        RepositoryModel model = base()
                .addTest(testFact("test:1", "sym:testClass"))
                .build();
        assertTrue(new TestSubjectAnalyzer().analyze(model).relationships().isEmpty());
    }

    @org.junit.jupiter.api.Test
    void avoidsDuplicateTestsEdges() {
        RepositoryModel model = base()
                .addTest(testFact("test:1", "sym:testClass"))
                .addRelationship(new Relationship(
                        "call-rel-1",
                        RelationshipType.CALLS,
                        "sym:testClass",
                        "sym:prodClass",
                        0.90,
                        Optional.of("call:find;confidence=high;via=field")
                ))
                .addRelationship(new Relationship(
                        "call-rel-2",
                        RelationshipType.CALLS,
                        "sym:testClass",
                        "sym:prodClass",
                        0.90,
                        Optional.of("call:save;confidence=high;via=field")
                ))
                .addImport(new Import(
                        "import:1",
                        "UserServiceTest.java",
                        "import demo.UserService;",
                        Optional.empty(),
                        SourceLocation.ofFile("UserServiceTest.java")
                ))
                .build();

        AnalysisResult result = new TestSubjectAnalyzer().analyze(model);
        assertEquals(1, result.relationships().size());
        assertEquals(0.90, result.relationships().getFirst().confidence());
        assertEquals(2, model.relationships().stream().filter(r -> r.type() == RelationshipType.CALLS).count());
    }

    @org.junit.jupiter.api.Test
    void existingCallsRelationshipsRemainOnTheModel() {
        RepositoryModel model = base()
                .addRelationship(Relationship.of("call-rel-1", RelationshipType.CALLS, "sym:testClass", "sym:prodClass"))
                .build();
        new TestSubjectAnalyzer().analyze(model);
        assertEquals(1, model.relationships().size());
        assertEquals(RelationshipType.CALLS, model.relationships().iterator().next().type());
    }

    private static Relationship onlyTests(AnalysisResult result) {
        assertEquals(1, result.relationships().size(), () -> "rels=" + result.relationships());
        Relationship relationship = result.relationships().getFirst();
        assertEquals(RelationshipType.TESTS, relationship.type());
        return relationship;
    }

    private static RepositoryModel.Builder base() {
        return RepositoryModel.builder(Repository.local("r1", "demo", "/tmp/demo"))
                .addFile(new SourceFile("UserServiceTest.java", "java", "h", 10))
                .addFile(new SourceFile("UserService.java", "java", "h", 10))
                .addModule(Module.of("module:demo", "demo", "java"))
                .addSymbol(type("sym:testClass", "UserServiceTest", "UserServiceTest.java"))
                .addSymbol(type("sym:prodClass", "UserService", "UserService.java"));
    }

    private static Symbol type(String id, String name, String file) {
        return new Symbol(
                id,
                name,
                SymbolKind.CLASS,
                Optional.empty(),
                Optional.of("module:demo"),
                SourceLocation.ofFile(file),
                Optional.empty()
        );
    }

    private static Symbol method(String id, String name, String parent) {
        String file = parent.equals("sym:testClass") ? "UserServiceTest.java" : "UserService.java";
        return new Symbol(
                id,
                name,
                SymbolKind.METHOD,
                Optional.of(parent),
                Optional.of("module:demo"),
                SourceLocation.ofFile(file),
                Optional.empty()
        );
    }

    private static Test testFact(String id, String symbolId) {
        String file = symbolId.contains("prod") ? "UserService.java" : "UserServiceTest.java";
        return new Test(
                id,
                symbolId,
                Optional.of("junit5"),
                SourceLocation.ofFile(file),
                Evidence.of(InferenceMethod.ANNOTATION)
        );
    }
}

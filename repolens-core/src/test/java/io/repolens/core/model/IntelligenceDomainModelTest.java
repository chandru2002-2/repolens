package io.repolens.core.model;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntelligenceDomainModelTest {

    @org.junit.jupiter.api.Test
    void evidenceRejectsNullMethodAndDropsBlankOptionals() {
        Evidence evidence = new Evidence(
                InferenceMethod.ANNOTATION,
                Optional.empty(),
                Optional.of("  "),
                Optional.of(" ")
        );
        assertEquals(InferenceMethod.ANNOTATION, evidence.inferenceMethod());
        assertTrue(evidence.referencedId().isEmpty());
        assertTrue(evidence.summary().isEmpty());
        assertThrows(NullPointerException.class, () -> Evidence.of(null));
    }

    @org.junit.jupiter.api.Test
    void endpointAndTestRoundTripOnRepositoryModel() {
        Repository repository = Repository.local("repo-1", "demo", "/tmp/demo");
        SourceFile file = new SourceFile("src/OwnerController.java", "java", "abc", 12);
        Module module = Module.of("mod-1", "demo", "java");
        Symbol owner = new Symbol(
                "sym-owner",
                "OwnerController",
                SymbolKind.CLASS,
                Optional.empty(),
                Optional.of("mod-1"),
                SourceLocation.ofFile("src/OwnerController.java"),
                Optional.of("public")
        );
        Symbol handler = new Symbol(
                "sym-handler",
                "showOwner",
                SymbolKind.METHOD,
                Optional.of("sym-owner"),
                Optional.of("mod-1"),
                SourceLocation.ofFile("src/OwnerController.java"),
                Optional.of("public")
        );
        Symbol testSymbol = new Symbol(
                "sym-test",
                "OwnerControllerTests",
                SymbolKind.CLASS,
                Optional.empty(),
                Optional.of("mod-1"),
                SourceLocation.ofFile("src/OwnerController.java"),
                Optional.empty()
        );
        SourceLocation location = new SourceLocation("src/OwnerController.java", 10, 1, 10, 40);
        Evidence annotation = new Evidence(
                InferenceMethod.ANNOTATION,
                Optional.of(location),
                Optional.empty(),
                Optional.of("@GetMapping")
        );
        Endpoint endpoint = new Endpoint(
                "ep-1",
                "GET",
                "/owners/{id}",
                Optional.of("sym-owner"),
                Optional.of("sym-handler"),
                location,
                annotation
        );
        Test discovered = new Test(
                "test-1",
                "sym-test",
                Optional.of("junit5"),
                SourceLocation.ofFile("src/OwnerController.java"),
                Evidence.of(InferenceMethod.NAME_HEURISTIC)
        );

        RepositoryModel model = RepositoryModel.builder(repository)
                .addFile(file)
                .addModule(module)
                .addSymbol(owner)
                .addSymbol(handler)
                .addSymbol(testSymbol)
                .addEndpoint(endpoint)
                .addTest(discovered)
                .build();

        assertEquals(1, model.endpoints().size());
        assertEquals("/owners/{id}", model.findEndpoint("ep-1").orElseThrow().path());
        assertEquals("GET", model.findEndpoint("ep-1").orElseThrow().httpMethod());
        assertEquals(InferenceMethod.ANNOTATION, model.findEndpoint("ep-1").orElseThrow().evidence().inferenceMethod());
        assertEquals("sym-test", model.findTest("test-1").orElseThrow().symbolId());
        assertTrue(model.findTest("test-1").orElseThrow().frameworkHint().isPresent());

        RepositoryModel copied = model.withMetadata(model.metadata());
        assertEquals("ep-1", copied.findEndpoint("ep-1").orElseThrow().id());
        assertEquals("test-1", copied.findTest("test-1").orElseThrow().id());
    }

    @org.junit.jupiter.api.Test
    void unknownHttpMethodIsExplicitNotBlank() {
        Endpoint endpoint = new Endpoint(
                "ep-unknown",
                Endpoint.UNKNOWN_METHOD,
                "/owners",
                Optional.empty(),
                Optional.empty(),
                SourceLocation.ofFile("src/OwnerController.java"),
                Evidence.of(InferenceMethod.ANNOTATION)
        );
        assertEquals("UNKNOWN", endpoint.httpMethod());
    }

    @org.junit.jupiter.api.Test
    void rejectsEndpointWithUnknownOwner() {
        Repository repository = Repository.local("repo-1", "demo", "/tmp/demo");
        SourceFile file = new SourceFile("src/A.java", "java", "h", 1);
        Endpoint endpoint = new Endpoint(
                "ep-1",
                "GET",
                "/",
                Optional.of("missing"),
                Optional.empty(),
                SourceLocation.ofFile("src/A.java"),
                Evidence.of(InferenceMethod.ANNOTATION)
        );
        assertThrows(IllegalStateException.class, () ->
                RepositoryModel.builder(repository).addFile(file).addEndpoint(endpoint).build());
    }

    @org.junit.jupiter.api.Test
    void rejectsTestWithUnknownSymbol() {
        Repository repository = Repository.local("repo-1", "demo", "/tmp/demo");
        SourceFile file = new SourceFile("src/A.java", "java", "h", 1);
        Test discovered = new Test(
                "test-1",
                "missing",
                Optional.empty(),
                SourceLocation.ofFile("src/A.java"),
                Evidence.of(InferenceMethod.ANNOTATION)
        );
        assertThrows(IllegalStateException.class, () ->
                RepositoryModel.builder(repository).addFile(file).addTest(discovered).build());
    }

    @org.junit.jupiter.api.Test
    void rejectsDuplicateEndpointIds() {
        Repository repository = Repository.local("repo-1", "demo", "/tmp/demo");
        Endpoint endpoint = new Endpoint(
                "ep-1",
                "GET",
                "/",
                Optional.empty(),
                Optional.empty(),
                SourceLocation.ofFile("a.java"),
                Evidence.of(InferenceMethod.ANNOTATION)
        );
        assertThrows(IllegalArgumentException.class, () ->
                RepositoryModel.builder(repository).addEndpoint(endpoint).addEndpoint(endpoint));
    }

    @org.junit.jupiter.api.Test
    void provenanceStringOnRelationshipIsUnchanged() {
        Relationship relationship = new Relationship(
                "rel-1",
                RelationshipType.CALLS,
                "a",
                "b",
                0.9,
                Optional.of("call:save;confidence=high")
        );
        assertEquals(0.9, relationship.confidence());
        assertEquals("call:save;confidence=high", relationship.provenance().orElseThrow());
        Relationship simple = Relationship.of("rel-2", RelationshipType.CONTAINS, "a", "b");
        assertEquals(1.0, simple.confidence());
        assertTrue(simple.provenance().isEmpty());
    }
}

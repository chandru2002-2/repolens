package io.repolens.analyzers;

import io.repolens.core.model.Endpoint;
import io.repolens.core.model.Evidence;
import io.repolens.core.model.Impact;
import io.repolens.core.model.ImpactItem;
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
import io.repolens.core.model.Trace;
import io.repolens.core.model.TraceHop;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImpactComposerTest {

    @org.junit.jupiter.api.Test
    void directCallsImpact() {
        Evidence evidence = new Evidence(
                InferenceMethod.TYPE_RESOLUTION,
                Optional.of(SourceLocation.ofFile("UserController.java")),
                Optional.of("call-1"),
                Optional.of("via=field")
        );
        RepositoryModel model = base()
                .addRelationship(call(
                        "call-1",
                        "sym:controller",
                        "sym:service",
                        0.90,
                        Optional.of("call:x;via=field"),
                        Optional.of(evidence)
                ))
                .addRelationship(call(
                        "call-low",
                        "sym:other",
                        "sym:service",
                        0.40,
                        Optional.of("call:x;via=name-heuristic"),
                        Optional.empty()
                ))
                .build();

        Impact impact = ImpactComposer.compose(model, "sym:service");
        assertEquals(1, impact.callers().size());
        ImpactItem caller = impact.callers().getFirst();
        assertEquals("sym:controller", caller.entityId());
        assertEquals(ImpactItem.CATEGORY_CALLER, caller.category());
        assertFalse(caller.inferred());
        assertEquals(0.90, caller.confidence());
        assertEquals(Optional.of("call-1"), caller.relationshipId());
        assertEquals(evidence, caller.evidence());
        assertTrue(impact.inferred().isEmpty());
    }

    @org.junit.jupiter.api.Test
    void directDependencyImpact() {
        RepositoryModel model = base()
                .addRelationship(rel("dep-1", RelationshipType.DEPENDS_ON, "sym:controller", "sym:service", 0.80))
                .addRelationship(rel("imp-1", RelationshipType.IMPORTS, "module:demo", "sym:service", 1.0))
                .addRelationship(rel("ext-1", RelationshipType.EXTENDS, "sym:other", "sym:service", 1.0))
                .addRelationship(rel("impl-1", RelationshipType.IMPLEMENTS, "sym:controller", "sym:service", 1.0))
                .build();

        Impact impact = ImpactComposer.compose(model, "sym:service");
        assertEquals(
                List.of("module:demo", "sym:controller", "sym:controller", "sym:other"),
                impact.dependents().stream().map(ImpactItem::entityId).toList()
        );
        assertTrue(impact.dependents().stream().noneMatch(ImpactItem::inferred));
        assertEquals(
                List.of(RelationshipType.IMPORTS, RelationshipType.DEPENDS_ON, RelationshipType.IMPLEMENTS, RelationshipType.EXTENDS)
                        .size(),
                impact.dependents().size()
        );
    }

    @org.junit.jupiter.api.Test
    void testsImpactFromAnalyzerRelationships() {
        RepositoryModel model = base()
                .addSymbol(method("sym:testMethod", "loads", "sym:testClass", "UserServiceTest.java"))
                .addSymbol(type("sym:testClass", "UserServiceTest", "UserServiceTest.java"))
                .addTest(new Test(
                        "test:1",
                        "sym:testMethod",
                        Optional.of("junit5"),
                        SourceLocation.ofFile("UserServiceTest.java"),
                        Evidence.of(InferenceMethod.ANNOTATION)
                ))
                .addRelationship(call(
                        "call-test",
                        "sym:testMethod",
                        "sym:service",
                        0.90,
                        Optional.of("call:find;confidence=high;via=field"),
                        Optional.empty()
                ))
                .build();

        Impact impact = ImpactComposer.compose(model, "sym:service");
        assertEquals(1, impact.tests().size());
        ImpactItem test = impact.tests().getFirst();
        assertEquals("sym:testMethod", test.entityId());
        assertEquals(ImpactItem.CATEGORY_TEST, test.category());
        assertFalse(test.inferred());
        assertEquals(0.90, test.confidence());
        assertTrue(test.evidence().summary().orElse("").contains("CALLS"));
        assertEquals(0, model.relationships().stream().filter(r -> r.type() == RelationshipType.TESTS).count());
    }

    @org.junit.jupiter.api.Test
    void endpointDiscoveredThroughTrace() {
        RepositoryModel model = base()
                .addEndpoint(endpoint("ep-1", "sym:controller", "sym:handler"))
                .addRelationship(call(
                        "call-1",
                        "sym:controller",
                        "sym:service",
                        0.90,
                        Optional.of("call:x;via=field"),
                        Optional.empty()
                ))
                .build();

        Impact impact = ImpactComposer.compose(model, "sym:service");
        assertEquals(1, impact.endpoints().size());
        ImpactItem endpoint = impact.endpoints().getFirst();
        assertEquals("ep-1", endpoint.entityId());
        assertEquals(ImpactItem.CATEGORY_ENDPOINT, endpoint.category());
        assertFalse(endpoint.inferred());
        assertTrue(endpoint.traceId().isPresent());
        assertEquals(0.90, endpoint.confidence());
    }

    @org.junit.jupiter.api.Test
    void inferredVersusDirectDistinction() {
        RepositoryModel model = base()
                .addSymbol(method("sym:testMethod", "loads", "sym:testClass", "UserServiceTest.java"))
                .addSymbol(type("sym:testClass", "UserServiceTest", "UserServiceTest.java"))
                .addTest(new Test(
                        "test:1",
                        "sym:testMethod",
                        Optional.of("junit5"),
                        SourceLocation.ofFile("UserServiceTest.java"),
                        Evidence.of(InferenceMethod.ANNOTATION)
                ))
                .addRelationship(call(
                        "call-direct",
                        "sym:controller",
                        "sym:service",
                        0.90,
                        Optional.of("call:x;via=field"),
                        Optional.empty()
                ))
                .addRelationship(call(
                        "call-two-hop",
                        "sym:other",
                        "sym:controller",
                        0.80,
                        Optional.of("call:x;via=field"),
                        Optional.empty()
                ))
                .addRelationship(call(
                        "call-test",
                        "sym:testMethod",
                        "sym:controller",
                        0.85,
                        Optional.of("call:x;via=field"),
                        Optional.empty()
                ))
                .build();

        Impact impact = ImpactComposer.compose(model, "sym:service");
        assertEquals(List.of("sym:controller"), impact.callers().stream().map(ImpactItem::entityId).toList());
        assertTrue(impact.callers().stream().noneMatch(ImpactItem::inferred));
        assertTrue(impact.tests().isEmpty());
        assertFalse(impact.inferred().isEmpty());
        assertTrue(impact.inferred().stream().allMatch(ImpactItem::inferred));
        assertTrue(impact.inferred().stream().allMatch(item -> item.category().equals(ImpactItem.CATEGORY_INFERRED)));
        assertTrue(impact.inferred().stream().anyMatch(item -> item.entityId().equals("sym:other")));
        assertTrue(impact.inferred().stream().anyMatch(item -> item.entityId().equals("sym:testMethod")));
        assertEquals(Math.min(0.90, 0.80), impact.inferred().stream()
                .filter(item -> item.entityId().equals("sym:other"))
                .findFirst()
                .orElseThrow()
                .confidence());
    }

    @org.junit.jupiter.api.Test
    void preservesConfidenceAndEvidence() {
        Evidence evidence = new Evidence(
                InferenceMethod.AST_CAPTURE,
                Optional.of(SourceLocation.ofFile("UserController.java")),
                Optional.of("call-1"),
                Optional.of("preserved")
        );
        RepositoryModel model = base()
                .addRelationship(call(
                        "call-1",
                        "sym:controller",
                        "sym:service",
                        0.73,
                        Optional.of("call:x;via=field"),
                        Optional.of(evidence)
                ))
                .build();

        ImpactItem caller = ImpactComposer.compose(model, "sym:service").callers().getFirst();
        assertEquals(0.73, caller.confidence());
        assertEquals(evidence, caller.evidence());
        assertEquals(InferenceMethod.AST_CAPTURE, caller.evidence().inferenceMethod());
        assertEquals(Optional.of("preserved"), caller.evidence().summary());
    }

    @org.junit.jupiter.api.Test
    void noNamingOnlyImpact() {
        RepositoryModel model = base()
                .addSymbol(type("sym:testClass", "UserServiceTest", "UserServiceTest.java"))
                .addSymbol(method("sym:testMethod", "userService", "sym:testClass", "UserServiceTest.java"))
                .addTest(new Test(
                        "test:1",
                        "sym:testMethod",
                        Optional.of("junit5"),
                        SourceLocation.ofFile("UserServiceTest.java"),
                        Evidence.of(InferenceMethod.NAME_HEURISTIC)
                ))
                .build();

        Impact impact = ImpactComposer.compose(model, "sym:service");
        assertTrue(impact.callers().isEmpty());
        assertTrue(impact.dependents().isEmpty());
        assertTrue(impact.tests().isEmpty());
        assertTrue(impact.endpoints().isEmpty());
        assertTrue(impact.inferred().isEmpty());
    }

    @org.junit.jupiter.api.Test
    void capsPerCategory() {
        RepositoryModel.Builder builder = base();
        for (int i = 0; i < ImpactComposer.MAX_PER_CATEGORY + 5; i++) {
            String caller = "sym:caller-" + String.format("%02d", i);
            builder.addSymbol(type(caller, "Caller" + i, "UserController.java"));
            builder.addRelationship(call(
                    "call-" + i,
                    caller,
                    "sym:service",
                    0.90,
                    Optional.of("call:x;via=field"),
                    Optional.empty()
            ));
        }
        Impact impact = ImpactComposer.compose(builder.build(), "sym:service");
        assertEquals(ImpactComposer.MAX_PER_CATEGORY, impact.callers().size());
        assertEquals("sym:caller-00", impact.callers().getFirst().entityId());
        assertEquals("sym:caller-39", impact.callers().getLast().entityId());
    }

    @org.junit.jupiter.api.Test
    void deterministicOrdering() {
        RepositoryModel model = base()
                .addRelationship(call(
                        "call-b",
                        "sym:other",
                        "sym:service",
                        0.70,
                        Optional.of("call:x;via=field"),
                        Optional.empty()
                ))
                .addRelationship(call(
                        "call-a",
                        "sym:controller",
                        "sym:service",
                        0.90,
                        Optional.of("call:x;via=field"),
                        Optional.empty()
                ))
                .build();

        List<String> first = ImpactComposer.compose(model, "sym:service").callers()
                .stream().map(item -> item.entityId() + "/" + item.relationshipId().orElse("")).toList();
        List<String> second = ImpactComposer.compose(model, "sym:service").callers()
                .stream().map(item -> item.entityId() + "/" + item.relationshipId().orElse("")).toList();
        assertEquals(List.of("sym:controller/call-a", "sym:other/call-b"), first);
        assertEquals(first, second);
    }

    @org.junit.jupiter.api.Test
    void entityWithNoImpact() {
        RepositoryModel model = base()
                .addRelationship(call(
                        "call-1",
                        "sym:controller",
                        "sym:service",
                        0.90,
                        Optional.of("call:x;via=field"),
                        Optional.empty()
                ))
                .build();

        Impact impact = ImpactComposer.compose(model, "sym:repository");
        assertEquals("sym:repository", impact.entityId());
        assertTrue(impact.callers().isEmpty());
        assertTrue(impact.dependents().isEmpty());
        assertTrue(impact.tests().isEmpty());
        assertTrue(impact.endpoints().isEmpty());
        assertTrue(impact.inferred().isEmpty());
    }

    @org.junit.jupiter.api.Test
    void doesNotWriteImpactOntoTheModel() {
        RepositoryModel model = base()
                .addRelationship(call(
                        "call-1",
                        "sym:controller",
                        "sym:service",
                        0.90,
                        Optional.of("call:x;via=field"),
                        Optional.empty()
                ))
                .build();
        int before = model.relationships().size();
        ImpactComposer.compose(model, "sym:service");
        assertEquals(before, model.relationships().size());
        assertTrue(model.relationships().stream().noneMatch(r -> r.id().startsWith("impact")));
    }

    @org.junit.jupiter.api.Test
    void usesProvidedTracesWithoutRequiringComposerChange() {
        TraceHop hop = new TraceHop(
                "sym:service",
                TraceHop.ROLE_SYMBOL,
                Optional.of("call-1"),
                Evidence.of(InferenceMethod.TYPE_RESOLUTION),
                0.88,
                true
        );
        Trace trace = new Trace(
                "trace-custom",
                "ep-custom",
                List.of(
                        new TraceHop(
                                "ep-custom",
                                TraceHop.ROLE_ENDPOINT,
                                Optional.empty(),
                                Evidence.of(InferenceMethod.ANNOTATION),
                                1.0,
                                true
                        ),
                        hop
                ),
                0.88,
                false,
                Trace.STATIC
        );
        RepositoryModel model = base().build();
        Impact impact = ImpactComposer.compose(model, "sym:service", List.of(trace));
        assertEquals("ep-custom", impact.endpoints().getFirst().entityId());
        assertEquals(Optional.of("trace-custom"), impact.endpoints().getFirst().traceId());
        assertEquals(0.88, impact.endpoints().getFirst().confidence());
        assertEquals(hop.evidence(), impact.endpoints().getFirst().evidence());
    }

    private static RepositoryModel.Builder base() {
        return RepositoryModel.builder(Repository.local("r1", "demo", "/tmp/demo"))
                .addFile(new SourceFile("UserController.java", "java", "h", 10))
                .addFile(new SourceFile("UserService.java", "java", "h", 10))
                .addFile(new SourceFile("UserRepository.java", "java", "h", 10))
                .addFile(new SourceFile("UserServiceTest.java", "java", "h", 10))
                .addModule(Module.of("module:demo", "demo", "java"))
                .addSymbol(type("sym:controller", "UserController", "UserController.java"))
                .addSymbol(method("sym:handler", "list", "sym:controller", "UserController.java"))
                .addSymbol(type("sym:service", "UserService", "UserService.java"))
                .addSymbol(type("sym:repository", "UserRepository", "UserRepository.java"))
                .addSymbol(type("sym:other", "OtherService", "UserController.java"));
    }

    private static Endpoint endpoint(String id, String owner, String handler) {
        return new Endpoint(
                id,
                "GET",
                "/users",
                Optional.of(owner),
                Optional.of(handler),
                SourceLocation.ofFile("UserController.java"),
                Evidence.of(InferenceMethod.ANNOTATION)
        );
    }

    private static Relationship call(
            String id,
            String from,
            String to,
            double confidence,
            Optional<String> provenance,
            Optional<Evidence> evidence
    ) {
        return new Relationship(id, RelationshipType.CALLS, from, to, confidence, provenance, evidence);
    }

    private static Relationship rel(String id, RelationshipType type, String from, String to, double confidence) {
        return new Relationship(id, type, from, to, confidence, Optional.empty(), Optional.empty());
    }

    private static Symbol type(String id, String name, String file) {
        return new Symbol(
                id, name, SymbolKind.CLASS, Optional.empty(), Optional.of("module:demo"),
                SourceLocation.ofFile(file), Optional.empty());
    }

    private static Symbol method(String id, String name, String parent, String file) {
        return new Symbol(
                id, name, SymbolKind.METHOD, Optional.of(parent), Optional.of("module:demo"),
                SourceLocation.ofFile(file), Optional.empty());
    }
}

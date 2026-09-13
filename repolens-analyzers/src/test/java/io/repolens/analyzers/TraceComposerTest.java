package io.repolens.analyzers;

import io.repolens.core.model.Endpoint;
import io.repolens.core.model.Evidence;
import io.repolens.core.model.InferenceMethod;
import io.repolens.core.model.Module;
import io.repolens.core.model.Relationship;
import io.repolens.core.model.RelationshipType;
import io.repolens.core.model.Repository;
import io.repolens.core.model.RepositoryModel;
import io.repolens.core.model.SourceFile;
import io.repolens.core.model.SourceLocation;
import io.repolens.core.model.StructuralFact;
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

class TraceComposerTest {

    @org.junit.jupiter.api.Test
    void tracesEndpointToHandler() {
        RepositoryModel model = base()
                .addEndpoint(endpoint("ep-1", "sym:controller", "sym:handler"))
                .build();
        Trace trace = TraceComposer.compose(model).getFirst();
        assertEquals("ep-1", trace.endpointId());
        assertEquals(Trace.STATIC, trace.inferenceKind());
        assertEquals(TraceHop.ROLE_ENDPOINT, trace.hops().getFirst().role());
        assertEquals("sym:handler", trace.hops().get(1).entityId());
        assertEquals(TraceHop.ROLE_HANDLER, trace.hops().get(1).role());
        assertTrue(trace.hops().getFirst().evidence() != null);
    }

    @org.junit.jupiter.api.Test
    void tracesHandlerToServiceThroughCalls() {
        RepositoryModel model = base()
                .addEndpoint(endpoint("ep-1", "sym:controller", "sym:handler"))
                .addRelationship(call("call-1", "sym:controller", "sym:service", 0.90, "via=field"))
                .build();
        Trace trace = resolved(TraceComposer.compose(model));
        assertEquals(List.of("ep-1", "sym:handler", "sym:service"), entityIds(trace));
        assertEquals("call-1", trace.hops().get(2).relationshipId().orElseThrow());
        assertEquals(0.90, trace.hops().get(2).confidence());
    }

    @org.junit.jupiter.api.Test
    void tracesServiceToRepositoryThroughCalls() {
        RepositoryModel model = base()
                .addEndpoint(endpoint("ep-1", "sym:controller", "sym:handler"))
                .addRelationship(call("call-1", "sym:controller", "sym:service", 0.90, "via=field"))
                .addRelationship(call("call-2", "sym:service", "sym:repository", 0.90, "via=field"))
                .build();
        Trace trace = resolved(TraceComposer.compose(model));
        assertEquals(List.of("ep-1", "sym:handler", "sym:service", "sym:repository"), entityIds(trace));
    }

    @org.junit.jupiter.api.Test
    void composesMultiHopTrace() {
        RepositoryModel model = base()
                .addEndpoint(endpoint("ep-1", "sym:controller", "sym:handler"))
                .addRelationship(call("call-1", "sym:handler", "sym:service", 0.90, "via=field"))
                .addRelationship(call("call-2", "sym:service", "sym:repository", 0.90, "via=field"))
                .build();
        Trace trace = resolved(TraceComposer.compose(model));
        assertTrue(trace.hops().size() >= 4);
        assertEquals("sym:repository", trace.hops().getLast().entityId());
        assertFalse(trace.unresolved());
    }

    @org.junit.jupiter.api.Test
    void confidenceIsWeakestResolvedHop() {
        RepositoryModel model = base()
                .addEndpoint(endpoint("ep-1", "sym:controller", "sym:handler"))
                .addRelationship(call("call-1", "sym:controller", "sym:service", 0.90, "via=field"))
                .addRelationship(call("call-2", "sym:service", "sym:repository", 0.55, "via=name-heuristic"))
                .build();
        Trace trace = resolved(TraceComposer.compose(model));
        assertEquals(0.55, trace.confidence());
        assertEquals(InferenceMethod.NAME_HEURISTIC, trace.hops().getLast().evidence().inferenceMethod());
    }

    @org.junit.jupiter.api.Test
    void missingCallsMarksTraceUnresolved() {
        RepositoryModel model = base()
                .addEndpoint(endpoint("ep-1", "sym:controller", "sym:handler"))
                .build();
        Trace trace = TraceComposer.compose(model).getFirst();
        assertTrue(trace.unresolved());
        assertEquals(TraceHop.ROLE_UNRESOLVED, trace.hops().getLast().role());
        assertFalse(trace.hops().getLast().resolved());
    }

    @org.junit.jupiter.api.Test
    void conventionOnlyStructuralFactsDoNotCreateHops() {
        RepositoryModel model = base()
                .addEndpoint(endpoint("ep-1", "sym:controller", "sym:handler"))
                .addStructuralFact(new StructuralFact(
                        "fact:1",
                        "sequence",
                        "service",
                        "UserService",
                        Optional.of("sym:service"),
                        Optional.empty(),
                        Optional.of("role=service"),
                        Optional.of("UserService.java"),
                        Optional.of(1)
                ))
                .build();
        Trace trace = TraceComposer.compose(model).getFirst();
        assertTrue(entityIds(trace).stream().noneMatch(id -> id.equals("sym:service")));
        assertTrue(trace.unresolved());
    }

    @org.junit.jupiter.api.Test
    void excludesTestSymbols() {
        RepositoryModel model = base()
                .addFile(new SourceFile("UserServiceTest.java", "java", "h", 10))
                .addSymbol(type("sym:testClass", "UserServiceTest", "UserServiceTest.java"))
                .addTest(new Test(
                        "test:1",
                        "sym:testClass",
                        Optional.of("junit5"),
                        SourceLocation.ofFile("UserServiceTest.java"),
                        Evidence.of(InferenceMethod.ANNOTATION)
                ))
                .addEndpoint(endpoint("ep-1", "sym:controller", "sym:handler"))
                .addRelationship(call("call-1", "sym:controller", "sym:service", 0.90, "via=field"))
                .addRelationship(call("call-2", "sym:controller", "sym:testClass", 0.90, "via=field"))
                .build();
        Trace trace = resolved(TraceComposer.compose(model));
        assertTrue(entityIds(trace).stream().noneMatch(id -> id.equals("sym:testClass")));
        assertTrue(entityIds(trace).contains("sym:service"));
    }

    @org.junit.jupiter.api.Test
    void capsFanOutAndDepth() {
        RepositoryModel.Builder builder = base()
                .addEndpoint(endpoint("ep-1", "sym:controller", "sym:handler"));
        for (int i = 0; i < TraceComposer.MAX_FANOUT + 3; i++) {
            builder.addFile(new SourceFile("S" + i + ".java", "java", "h", 10));
            builder.addSymbol(type("sym:s" + i, "Svc" + i, "S" + i + ".java"));
            builder.addRelationship(call("call-f" + i, "sym:controller", "sym:s" + i, 0.90, "via=field"));
        }
        String prev = "sym:controller";
        for (int i = 0; i < TraceComposer.MAX_DEPTH + 3; i++) {
            builder.addFile(new SourceFile("D" + i + ".java", "java", "h", 10));
            builder.addSymbol(type("sym:d" + i, "Deep" + i, "D" + i + ".java"));
            builder.addRelationship(call("call-d" + i, prev, "sym:d" + i, 0.90, "via=field"));
            prev = "sym:d" + i;
        }
        List<Trace> traces = TraceComposer.compose(builder.build());
        assertTrue(traces.size() <= TraceComposer.MAX_TRACES_PER_ENDPOINT);
        int firstHopsFromController = 0;
        for (Trace trace : traces) {
            long fan = trace.hops().stream().filter(hop -> hop.entityId().startsWith("sym:s")).count();
            firstHopsFromController += (int) fan;
            assertTrue(trace.hops().size() <= TraceComposer.MAX_DEPTH + 4);
        }
        assertTrue(firstHopsFromController <= TraceComposer.MAX_FANOUT * traces.size());
        assertTrue(traces.stream().noneMatch(trace ->
                entityIds(trace).contains("sym:d" + (TraceComposer.MAX_DEPTH + 2))));
    }

    @org.junit.jupiter.api.Test
    void ordersTracesDeterministically() {
        RepositoryModel model = base()
                .addEndpoint(endpoint("ep-1", "sym:controller", "sym:handler"))
                .addRelationship(call("call-b", "sym:controller", "sym:repository", 0.90, "via=field"))
                .addRelationship(call("call-a", "sym:controller", "sym:service", 0.90, "via=field"))
                .build();
        List<Trace> first = TraceComposer.compose(model);
        List<Trace> second = TraceComposer.compose(model);
        assertEquals(entityIds(first.get(0)), entityIds(second.get(0)));
        assertEquals(entityIds(first.get(1)), entityIds(second.get(1)));
        assertTrue(entityIds(first.get(0)).getLast().compareTo(entityIds(first.get(1)).getLast()) <= 0
                || first.get(0).hops().size() >= first.get(1).hops().size());
        String left = hopSignature(first.get(0));
        String right = hopSignature(first.get(1));
        assertTrue(left.compareTo(right) <= 0);
    }

    @org.junit.jupiter.api.Test
    void belowEmitThresholdCallsAreIgnored() {
        RepositoryModel model = base()
                .addEndpoint(endpoint("ep-1", "sym:controller", "sym:handler"))
                .addRelationship(call("call-low", "sym:controller", "sym:service", 0.35, "via=role"))
                .build();
        Trace trace = TraceComposer.compose(model).getFirst();
        assertTrue(entityIds(trace).stream().noneMatch(id -> id.equals("sym:service")));
        assertTrue(trace.unresolved());
    }

    @org.junit.jupiter.api.Test
    void analyzerDoesNotWriteTracesOntoTheModel() {
        RepositoryModel model = base()
                .addEndpoint(endpoint("ep-1", "sym:controller", "sym:handler"))
                .addRelationship(call("call-1", "sym:controller", "sym:service", 0.90, "via=field"))
                .build();
        new TraceComposer().analyze(model);
        assertTrue(model.relationships().stream().noneMatch(r -> r.id().startsWith("trace")));
        assertEquals(1, model.relationships().size());
    }

    private static Trace resolved(List<Trace> traces) {
        return traces.stream()
                .filter(trace -> !trace.unresolved())
                .findFirst()
                .orElseThrow(() -> new AssertionError("traces=" + traces));
    }

    private static List<String> entityIds(Trace trace) {
        return trace.hops().stream().map(TraceHop::entityId).toList();
    }

    private static String hopSignature(Trace trace) {
        return entityIds(trace).toString();
    }

    private static RepositoryModel.Builder base() {
        return RepositoryModel.builder(Repository.local("r1", "demo", "/tmp/demo"))
                .addFile(new SourceFile("UserController.java", "java", "h", 10))
                .addFile(new SourceFile("UserService.java", "java", "h", 10))
                .addFile(new SourceFile("UserRepository.java", "java", "h", 10))
                .addModule(Module.of("module:demo", "demo", "java"))
                .addSymbol(type("sym:controller", "UserController", "UserController.java"))
                .addSymbol(method("sym:handler", "list", "sym:controller", "UserController.java"))
                .addSymbol(type("sym:service", "UserService", "UserService.java"))
                .addSymbol(type("sym:repository", "UserRepository", "UserRepository.java"));
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

    private static Relationship call(String id, String from, String to, double confidence, String via) {
        return new Relationship(
                id,
                RelationshipType.CALLS,
                from,
                to,
                confidence,
                Optional.of("call:x;confidence=x;" + via)
        );
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

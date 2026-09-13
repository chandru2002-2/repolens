package io.repolens.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.repolens.core.model.AnalysisResult;
import io.repolens.core.model.Endpoint;
import io.repolens.core.model.Evidence;
import io.repolens.core.model.GraphView;
import io.repolens.core.model.Impact;
import io.repolens.core.model.ImpactItem;
import io.repolens.core.model.InferenceMethod;
import io.repolens.core.model.Repository;
import io.repolens.core.model.RepositoryModel;
import io.repolens.core.model.SourceFile;
import io.repolens.core.model.SourceLocation;
import io.repolens.core.model.Test;
import io.repolens.core.model.Trace;
import io.repolens.core.model.TraceHop;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntelligenceApiMappingTest {

    private final ObjectMapper jackson = new ObjectMapper();

    @org.junit.jupiter.api.Test
    void serializesEndpointsFromModelFacts() throws Exception {
        RepositoryModel model = emptyModel().addEndpoint(endpoint()).build();
        AnalysisResponseDto dto = AnalysisResponseMapper.from(model, List.of(), emptyGraph());
        assertEquals(1, dto.endpoints().size());
        AnalysisResponseDto.EndpointDto endpoint = dto.endpoints().getFirst();
        assertEquals("ep-1", endpoint.id());
        assertEquals("GET", endpoint.httpMethod());
        assertEquals("/users", endpoint.path());
        assertEquals("sym:controller", endpoint.ownerTypeId());
        assertEquals("ANNOTATION", endpoint.evidence().inferenceMethod());
        assertEquals("UserController.java", endpoint.location().filePath());

        String json = jackson.writeValueAsString(dto);
        JsonNode node = jackson.readTree(json).get("endpoints").get(0);
        assertEquals("ep-1", node.get("id").asText());
        assertEquals("ANNOTATION", node.get("evidence").get("inferenceMethod").asText());
        assertFalse(json.contains("tree-sitter"));
        assertFalse(json.contains("TreeSitter"));
    }

    @org.junit.jupiter.api.Test
    void serializesTestsFromModelFacts() throws Exception {
        RepositoryModel model = emptyModel().addTest(testFact()).build();
        AnalysisResponseDto dto = AnalysisResponseMapper.from(model, List.of(), emptyGraph());
        AnalysisResponseDto.TestDto mapped = dto.tests().getFirst();
        assertEquals("test:1", mapped.id());
        assertEquals("sym:testMethod", mapped.symbolId());
        assertEquals("junit5", mapped.frameworkHint());
        assertEquals("ANNOTATION", mapped.evidence().inferenceMethod());

        String json = jackson.writeValueAsString(dto.tests());
        assertTrue(json.contains("\"id\":\"test:1\""));
        assertTrue(json.contains("\"symbolId\":\"sym:testMethod\""));
    }

    @org.junit.jupiter.api.Test
    void serializesStaticTracesWithHopEvidence() throws Exception {
        Trace trace = new Trace(
                "trace-1",
                "ep-1",
                List.of(
                        new TraceHop(
                                "ep-1",
                                TraceHop.ROLE_ENDPOINT,
                                Optional.empty(),
                                Evidence.of(InferenceMethod.ANNOTATION),
                                1.0,
                                true
                        ),
                        new TraceHop(
                                "sym:service",
                                TraceHop.ROLE_SYMBOL,
                                Optional.of("call-1"),
                                new Evidence(
                                        InferenceMethod.TYPE_RESOLUTION,
                                        Optional.of(SourceLocation.ofFile("UserService.java")),
                                        Optional.of("call-1"),
                                        Optional.of("via=field")
                                ),
                                0.90,
                                true
                        )
                ),
                0.90,
                false,
                Trace.STATIC
        );
        AnalysisResponseDto dto = AnalysisResponseMapper.from(
                emptyModel().build(), List.of(), emptyGraph(), List.of(), List.of(trace));
        AnalysisResponseDto.TraceDto mapped = dto.traces().getFirst();
        assertEquals("static", mapped.inferenceKind());
        assertEquals(0.90, mapped.confidence());
        assertFalse(mapped.unresolved());
        assertEquals("call-1", mapped.hops().get(1).relationshipId());
        assertEquals("TYPE_RESOLUTION", mapped.hops().get(1).evidence().inferenceMethod());

        String json = jackson.writeValueAsString(mapped);
        assertTrue(json.contains("\"inferenceKind\":\"static\""));
        assertTrue(json.contains("\"confidence\":0.9"));
        assertFalse(json.contains("runtime"));
    }

    @org.junit.jupiter.api.Test
    void serializesImpactDirectAndInferred() throws Exception {
        Impact impact = new Impact(
                "sym:service",
                List.of(item("sym:controller", ImpactItem.CATEGORY_CALLER, false, 0.91, "call-1")),
                List.of(),
                List.of(),
                List.of(),
                List.of(item("sym:other", ImpactItem.CATEGORY_INFERRED, true, 0.80, "call-2"))
        );
        AnalysisResponseDto.ImpactDto dto = AnalysisResponseMapper.mapImpact(impact);
        assertEquals("sym:service", dto.entityId());
        assertFalse(dto.callers().getFirst().inferred());
        assertEquals(0.91, dto.callers().getFirst().confidence());
        assertEquals("preserved", dto.callers().getFirst().evidence().summary());
        assertTrue(dto.inferred().getFirst().inferred());
        assertEquals("inferred", dto.inferred().getFirst().category());

        String json = jackson.writeValueAsString(dto);
        assertTrue(json.contains("\"inferred\":false"));
        assertTrue(json.contains("\"inferred\":true"));
        assertTrue(json.contains("\"confidence\":0.91"));
    }

    @org.junit.jupiter.api.Test
    void emptyIntelligenceCollectionsAreValid() throws Exception {
        AnalysisResponseDto dto = AnalysisResponseMapper.from(emptyModel().build(), List.of(), emptyGraph());
        assertTrue(dto.endpoints().isEmpty());
        assertTrue(dto.tests().isEmpty());
        assertTrue(dto.traces().isEmpty());
        String json = jackson.writeValueAsString(dto);
        assertTrue(json.contains("\"endpoints\":[]"));
        assertTrue(json.contains("\"tests\":[]"));
        assertTrue(json.contains("\"traces\":[]"));
        AnalysisResponseDto restored = jackson.readValue(json, AnalysisResponseDto.class);
        assertTrue(restored.endpoints().isEmpty());
        assertTrue(restored.tests().isEmpty());
        assertTrue(restored.traces().isEmpty());
    }

    @org.junit.jupiter.api.Test
    void v1ResultRemainsCompatibleWhenIntelligenceFieldsAreAbsent() throws Exception {
        AnalysisResult result = new AnalysisResult("noop", "ok", List.of(), List.of(), List.of());
        AnalysisResponseDto dto = AnalysisResponseDto.of(
                AnalysisResponseDto.SCHEMA_VERSION,
                new AnalysisResponseDto.RepositoryDto("r1", "demo", "LOCAL", "/tmp/demo"),
                new AnalysisResponseDto.ModelStatsDto(1, 0, 0, 0, 0),
                List.of(new AnalysisResponseDto.AnalysisResultDto("noop", "ok", List.of(), List.of())),
                new AnalysisResponseDto.GraphViewDto("g1", List.of(), List.of())
        );
        assertEquals("v1", dto.schemaVersion());
        assertTrue(dto.endpoints().isEmpty());
        assertEquals(1, dto.results().size());

        String legacyJson = """
                {"schemaVersion":"v1","repository":{"id":"r1","name":"demo","origin":"LOCAL","source":"/tmp"},\
                "modelStats":{"fileCount":1,"moduleCount":0,"symbolCount":0,"importCount":0,"relationshipCount":0},\
                "results":[{"analyzerId":"noop","summary":"ok","metrics":[],"findings":[]}],\
                "graph":{"id":"g1","nodes":[],"edges":[]}}
                """;
        AnalysisResponseDto restored = jackson.readValue(legacyJson, AnalysisResponseDto.class);
        assertEquals("v1", restored.schemaVersion());
        assertEquals("r1", restored.repository().id());
        assertTrue(restored.endpoints().isEmpty());
        assertTrue(restored.tests().isEmpty());
        assertTrue(restored.traces().isEmpty());
        assertTrue(restored.diagrams().isEmpty());

        AnalysisResponseDto mapped = AnalysisResponseMapper.from(emptyModel().build(), List.of(result), emptyGraph());
        assertEquals("v1", mapped.schemaVersion());
        assertTrue(mapped.modelStats().fileCount() >= 1);
        assertTrue(mapped.endpoints().isEmpty());
        assertTrue(mapped.tests().isEmpty());
        assertTrue(mapped.traces().isEmpty());
    }

    private static RepositoryModel.Builder emptyModel() {
        return RepositoryModel.builder(Repository.local("r1", "demo", "/tmp/demo"))
                .addFile(new SourceFile("UserController.java", "java", "h", 10))
                .addFile(new SourceFile("UserServiceTest.java", "java", "h", 10))
                .addSymbol(new io.repolens.core.model.Symbol(
                        "sym:controller",
                        "UserController",
                        io.repolens.core.model.SymbolKind.CLASS,
                        Optional.empty(),
                        Optional.empty(),
                        SourceLocation.ofFile("UserController.java"),
                        Optional.empty()
                ))
                .addSymbol(new io.repolens.core.model.Symbol(
                        "sym:handler",
                        "list",
                        io.repolens.core.model.SymbolKind.METHOD,
                        Optional.of("sym:controller"),
                        Optional.empty(),
                        SourceLocation.ofFile("UserController.java"),
                        Optional.empty()
                ))
                .addSymbol(new io.repolens.core.model.Symbol(
                        "sym:testMethod",
                        "loads",
                        io.repolens.core.model.SymbolKind.METHOD,
                        Optional.empty(),
                        Optional.empty(),
                        SourceLocation.ofFile("UserServiceTest.java"),
                        Optional.empty()
                ));
    }

    private static GraphView emptyGraph() {
        return new GraphView("g1", List.of(), List.of());
    }

    private static Endpoint endpoint() {
        return new Endpoint(
                "ep-1",
                "GET",
                "/users",
                Optional.of("sym:controller"),
                Optional.of("sym:handler"),
                SourceLocation.ofFile("UserController.java"),
                Evidence.of(InferenceMethod.ANNOTATION)
        );
    }

    private static Test testFact() {
        return new Test(
                "test:1",
                "sym:testMethod",
                Optional.of("junit5"),
                SourceLocation.ofFile("UserServiceTest.java"),
                Evidence.of(InferenceMethod.ANNOTATION)
        );
    }

    private static ImpactItem item(
            String entityId,
            String category,
            boolean inferred,
            double confidence,
            String relationshipId
    ) {
        return new ImpactItem(
                entityId,
                category,
                inferred,
                confidence,
                new Evidence(
                        InferenceMethod.TYPE_RESOLUTION,
                        Optional.empty(),
                        Optional.of(relationshipId),
                        Optional.of("preserved")
                ),
                Optional.of(relationshipId),
                Optional.empty()
        );
    }
}

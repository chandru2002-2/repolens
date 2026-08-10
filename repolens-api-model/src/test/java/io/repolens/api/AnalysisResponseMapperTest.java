package io.repolens.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.repolens.core.model.AnalysisResult;
import io.repolens.core.model.GraphView;
import io.repolens.core.model.Repository;
import io.repolens.core.model.RepositoryModel;
import io.repolens.core.model.SourceFile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisResponseMapperTest {

    @Test
    void mapsAndRoundTripsThroughJackson() throws Exception {
        RepositoryModel model = RepositoryModel.builder(Repository.local("r1", "demo", "/tmp/demo"))
                .addFile(new SourceFile("a.java", "java", "h", 10))
                .build();

        AnalysisResult result = new AnalysisResult("noop", "ok", List.of(), List.of(), List.of());
        GraphView graph = new GraphView(
                "g1",
                List.of(new GraphView.Node("n1", "demo", "repository", Optional.of("r1"))),
                List.of()
        );

        AnalysisResponseDto dto = AnalysisResponseMapper.from(model, List.of(result), graph);
        assertEquals(AnalysisResponseDto.SCHEMA_VERSION, dto.schemaVersion());
        assertEquals(1, dto.modelStats().fileCount());

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(dto);
        AnalysisResponseDto restored = mapper.readValue(json, AnalysisResponseDto.class);

        assertEquals(dto.repository().id(), restored.repository().id());
        assertEquals(dto.results().getFirst().analyzerId(), restored.results().getFirst().analyzerId());
        assertTrue(json.contains("\"schemaVersion\":\"v1\""));
    }
}

package io.repolens.api;

import io.repolens.core.model.AnalysisResult;
import io.repolens.core.model.GraphView;
import io.repolens.core.model.Metric;
import io.repolens.core.model.Repository;
import io.repolens.core.model.RepositoryModel;
import io.repolens.core.model.RepositoryOrigin;

import java.util.List;

/**
 * Maps domain aggregates to the stable v1 API contract.
 */
public final class AnalysisResponseMapper {

    private AnalysisResponseMapper() {
    }

    public static AnalysisResponseDto from(
            RepositoryModel model,
            List<AnalysisResult> results,
            GraphView graph
    ) {
        Repository repository = model.repository();
        String source = repository.origin() == RepositoryOrigin.REMOTE
                ? repository.remoteUrl().orElse("")
                : repository.localPath().orElse("");

        return new AnalysisResponseDto(
                AnalysisResponseDto.SCHEMA_VERSION,
                new AnalysisResponseDto.RepositoryDto(
                        repository.id(),
                        repository.name(),
                        repository.origin().name(),
                        source
                ),
                new AnalysisResponseDto.ModelStatsDto(
                        model.fileCount(),
                        model.modules().size(),
                        model.symbolCount(),
                        model.imports().size(),
                        model.relationships().size()
                ),
                results.stream().map(AnalysisResponseMapper::mapResult).toList(),
                mapGraph(graph)
        );
    }

    private static AnalysisResponseDto.AnalysisResultDto mapResult(AnalysisResult result) {
        return new AnalysisResponseDto.AnalysisResultDto(
                result.analyzerId(),
                result.summary(),
                result.metrics().stream().map(AnalysisResponseMapper::mapMetric).toList(),
                result.findings().stream()
                        .map(f -> new AnalysisResponseDto.FindingDto(
                                f.id(),
                                f.severity(),
                                f.message(),
                                f.subjectId().orElse(null)
                        ))
                        .toList()
        );
    }

    private static AnalysisResponseDto.MetricDto mapMetric(Metric metric) {
        return new AnalysisResponseDto.MetricDto(
                metric.name(),
                metric.value(),
                metric.unit().orElse(null),
                metric.scopeId().orElse(null)
        );
    }

    private static AnalysisResponseDto.GraphViewDto mapGraph(GraphView graph) {
        return new AnalysisResponseDto.GraphViewDto(
                graph.id(),
                graph.nodes().stream()
                        .map(n -> new AnalysisResponseDto.GraphNodeDto(
                                n.id(),
                                n.label(),
                                n.kind(),
                                n.sourceEntityId().orElse(null)
                        ))
                        .toList(),
                graph.edges().stream()
                        .map(e -> new AnalysisResponseDto.GraphEdgeDto(
                                e.id(),
                                e.fromNodeId(),
                                e.toNodeId(),
                                e.type()
                        ))
                        .toList()
        );
    }
}

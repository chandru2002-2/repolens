package io.repolens.api;

import java.util.List;
import java.util.Objects;

/**
 * Stable v1 JSON-oriented analysis contract shared by CLI and Web.
 * Adapters map domain types to these DTOs; they do not parse source.
 */
public record AnalysisResponseDto(
        String schemaVersion,
        RepositoryDto repository,
        ModelStatsDto modelStats,
        List<AnalysisResultDto> results,
        GraphViewDto graph
) {
    public static final String SCHEMA_VERSION = "v1";

    public AnalysisResponseDto {
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(modelStats, "modelStats");
        Objects.requireNonNull(results, "results");
        Objects.requireNonNull(graph, "graph");
        results = List.copyOf(results);
    }

    public record RepositoryDto(
            String id,
            String name,
            String origin,
            String source
    ) {
        public RepositoryDto {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(origin, "origin");
            Objects.requireNonNull(source, "source");
        }
    }

    public record ModelStatsDto(
            int fileCount,
            int moduleCount,
            int symbolCount,
            int importCount,
            int relationshipCount
    ) {
    }

    public record AnalysisResultDto(
            String analyzerId,
            String summary,
            List<MetricDto> metrics,
            List<FindingDto> findings
    ) {
        public AnalysisResultDto {
            Objects.requireNonNull(analyzerId, "analyzerId");
            Objects.requireNonNull(summary, "summary");
            Objects.requireNonNull(metrics, "metrics");
            Objects.requireNonNull(findings, "findings");
            metrics = List.copyOf(metrics);
            findings = List.copyOf(findings);
        }
    }

    public record MetricDto(String name, double value, String unit, String scopeId) {
        public MetricDto {
            Objects.requireNonNull(name, "name");
        }
    }

    public record FindingDto(String id, String severity, String message, String subjectId) {
        public FindingDto {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(severity, "severity");
            Objects.requireNonNull(message, "message");
        }
    }

    public record GraphViewDto(
            String id,
            List<GraphNodeDto> nodes,
            List<GraphEdgeDto> edges
    ) {
        public GraphViewDto {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(nodes, "nodes");
            Objects.requireNonNull(edges, "edges");
            nodes = List.copyOf(nodes);
            edges = List.copyOf(edges);
        }
    }

    public record GraphNodeDto(String id, String label, String kind, String sourceEntityId) {
        public GraphNodeDto {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(kind, "kind");
        }
    }

    public record GraphEdgeDto(String id, String fromNodeId, String toNodeId, String type) {
        public GraphEdgeDto {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(fromNodeId, "fromNodeId");
            Objects.requireNonNull(toNodeId, "toNodeId");
            Objects.requireNonNull(type, "type");
        }
    }
}

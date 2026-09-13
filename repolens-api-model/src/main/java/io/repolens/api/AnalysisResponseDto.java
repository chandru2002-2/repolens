package io.repolens.api;

import java.util.List;
import java.util.Objects;

/**
 * Stable v1 JSON-oriented analysis contract shared by CLI and Web.
 * Adapters map domain types to these DTOs; they do not parse source.
 *
 * <p>Optional {@code documentation}, {@code symbols}, {@code diagrams},
 * {@code endpoints}, {@code tests}, and {@code traces} fields default to empty lists.
 * Impact is per selected entity and is not stored on this result.
 */
public record AnalysisResponseDto(
        String schemaVersion,
        RepositoryDto repository,
        ModelStatsDto modelStats,
        List<AnalysisResultDto> results,
        GraphViewDto graph,
        List<DocumentationDto> documentation,
        List<SymbolDetailDto> symbols,
        RepositoryMetadataDto metadata,
        List<DiagramDto> diagrams,
        List<EndpointDto> endpoints,
        List<TestDto> tests,
        List<TraceDto> traces
) {
    public static final String SCHEMA_VERSION = "v1";

    public AnalysisResponseDto {
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(modelStats, "modelStats");
        Objects.requireNonNull(results, "results");
        Objects.requireNonNull(graph, "graph");
        if (documentation == null) {
            documentation = List.of();
        }
        if (symbols == null) {
            symbols = List.of();
        }
        if (diagrams == null) {
            diagrams = List.of();
        }
        if (endpoints == null) {
            endpoints = List.of();
        }
        if (tests == null) {
            tests = List.of();
        }
        if (traces == null) {
            traces = List.of();
        }
        results = List.copyOf(results);
        documentation = List.copyOf(documentation);
        symbols = List.copyOf(symbols);
        diagrams = List.copyOf(diagrams);
        endpoints = List.copyOf(endpoints);
        tests = List.copyOf(tests);
        traces = List.copyOf(traces);
    }

    /** Backward-compatible factory used by older call sites / tests. */
    public static AnalysisResponseDto of(
            String schemaVersion,
            RepositoryDto repository,
            ModelStatsDto modelStats,
            List<AnalysisResultDto> results,
            GraphViewDto graph
    ) {
        return new AnalysisResponseDto(
                schemaVersion,
                repository,
                modelStats,
                results,
                graph,
                List.of(),
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
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

    public record DocumentationDto(
            String id,
            String path,
            String title,
            List<DocumentationSectionDto> sections,
            List<DocumentationRefDto> references
    ) {
        public DocumentationDto {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(title, "title");
            if (sections == null) {
                sections = List.of();
            }
            if (references == null) {
                references = List.of();
            }
            sections = List.copyOf(sections);
            references = List.copyOf(references);
        }
    }

    public record DocumentationSectionDto(
            String id,
            String heading,
            String text,
            int startLine
    ) {
        public DocumentationSectionDto {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(heading, "heading");
            Objects.requireNonNull(text, "text");
        }
    }

    public record DocumentationRefDto(
            String id,
            String sectionId,
            String entityId,
            String matchedText
    ) {
        public DocumentationRefDto {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(sectionId, "sectionId");
            Objects.requireNonNull(entityId, "entityId");
            Objects.requireNonNull(matchedText, "matchedText");
        }
    }

    public record SymbolDetailDto(
            String id,
            String name,
            String kind,
            String moduleId,
            String moduleName,
            String filePath,
            String parentSymbolId,
            List<String> fieldNames,
            List<String> methodNames
    ) {
        public SymbolDetailDto {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(kind, "kind");
            if (fieldNames == null) {
                fieldNames = List.of();
            }
            if (methodNames == null) {
                methodNames = List.of();
            }
            fieldNames = List.copyOf(fieldNames);
            methodNames = List.copyOf(methodNames);
        }
    }

    public record RepositoryMetadataDto(
            String name,
            String owner,
            Long sizeBytes,
            String createdAt,
            String firstCommitAt,
            String defaultBranch,
            CommitInfoDto lastCommit,
            Integer commitCount
    ) {
    }

    public record CommitInfoDto(
            String sha,
            String message,
            String author,
            String authoredAt,
            String committedAt
    ) {
    }

    public record DiagramDto(
            String type,
            String title,
            GraphViewDto graph,
            String emptyMessage,
            String advisoryMessage,
            int totalNodeCount,
            boolean truncated
    ) {
        public DiagramDto {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(graph, "graph");
        }
    }

    public record SourceLocationDto(
            String filePath,
            int startLine,
            int startColumn,
            int endLine,
            int endColumn
    ) {
        public SourceLocationDto {
            Objects.requireNonNull(filePath, "filePath");
        }
    }

    public record EvidenceDto(
            String inferenceMethod,
            SourceLocationDto location,
            String referencedId,
            String summary
    ) {
        public EvidenceDto {
            Objects.requireNonNull(inferenceMethod, "inferenceMethod");
        }
    }

    public record EndpointDto(
            String id,
            String httpMethod,
            String path,
            String ownerTypeId,
            String handlerMethodId,
            SourceLocationDto location,
            EvidenceDto evidence
    ) {
        public EndpointDto {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(httpMethod, "httpMethod");
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(location, "location");
            Objects.requireNonNull(evidence, "evidence");
        }
    }

    public record TestDto(
            String id,
            String symbolId,
            String frameworkHint,
            SourceLocationDto location,
            EvidenceDto evidence
    ) {
        public TestDto {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(symbolId, "symbolId");
            Objects.requireNonNull(location, "location");
            Objects.requireNonNull(evidence, "evidence");
        }
    }

    public record TraceHopDto(
            String entityId,
            String role,
            String relationshipId,
            EvidenceDto evidence,
            double confidence,
            boolean resolved
    ) {
        public TraceHopDto {
            Objects.requireNonNull(entityId, "entityId");
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(evidence, "evidence");
        }
    }

    public record TraceDto(
            String id,
            String endpointId,
            List<TraceHopDto> hops,
            double confidence,
            boolean unresolved,
            String inferenceKind
    ) {
        public TraceDto {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(endpointId, "endpointId");
            Objects.requireNonNull(hops, "hops");
            Objects.requireNonNull(inferenceKind, "inferenceKind");
            hops = List.copyOf(hops);
        }
    }

    public record ImpactItemDto(
            String entityId,
            String category,
            boolean inferred,
            double confidence,
            EvidenceDto evidence,
            String relationshipId,
            String traceId
    ) {
        public ImpactItemDto {
            Objects.requireNonNull(entityId, "entityId");
            Objects.requireNonNull(category, "category");
            Objects.requireNonNull(evidence, "evidence");
        }
    }

    public record ImpactDto(
            String entityId,
            List<ImpactItemDto> callers,
            List<ImpactItemDto> dependents,
            List<ImpactItemDto> tests,
            List<ImpactItemDto> endpoints,
            List<ImpactItemDto> inferred
    ) {
        public ImpactDto {
            Objects.requireNonNull(entityId, "entityId");
            if (callers == null) {
                callers = List.of();
            }
            if (dependents == null) {
                dependents = List.of();
            }
            if (tests == null) {
                tests = List.of();
            }
            if (endpoints == null) {
                endpoints = List.of();
            }
            if (inferred == null) {
                inferred = List.of();
            }
            callers = List.copyOf(callers);
            dependents = List.copyOf(dependents);
            tests = List.copyOf(tests);
            endpoints = List.copyOf(endpoints);
            inferred = List.copyOf(inferred);
        }
    }
}

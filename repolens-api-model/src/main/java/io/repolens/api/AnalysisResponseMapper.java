package io.repolens.api;

import io.repolens.core.model.AnalysisResult;
import io.repolens.core.model.CommitInfo;
import io.repolens.core.model.DocumentationDocument;
import io.repolens.core.model.DocumentationReference;
import io.repolens.core.model.Endpoint;
import io.repolens.core.model.Evidence;
import io.repolens.core.model.GraphView;
import io.repolens.core.model.Impact;
import io.repolens.core.model.ImpactItem;
import io.repolens.core.model.Metric;
import io.repolens.core.model.NamedDiagram;
import io.repolens.core.model.Repository;
import io.repolens.core.model.RepositoryMetadata;
import io.repolens.core.model.RepositoryModel;
import io.repolens.core.model.RepositoryOrigin;
import io.repolens.core.model.SourceLocation;
import io.repolens.core.model.Symbol;
import io.repolens.core.model.SymbolKind;
import io.repolens.core.model.Test;
import io.repolens.core.model.Trace;
import io.repolens.core.model.TraceHop;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

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
        return from(model, results, graph, List.of());
    }

    public static AnalysisResponseDto from(
            RepositoryModel model,
            List<AnalysisResult> results,
            GraphView graph,
            List<NamedDiagram> diagrams
    ) {
        return from(model, results, graph, diagrams, List.of());
    }

    public static AnalysisResponseDto from(
            RepositoryModel model,
            List<AnalysisResult> results,
            GraphView graph,
            List<NamedDiagram> diagrams,
            List<Trace> traces
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
                mapGraph(graph),
                mapDocumentation(model),
                mapSymbols(model),
                mapMetadata(model.metadata()),
                mapDiagrams(diagrams),
                mapEndpoints(model),
                mapTests(model),
                mapTraces(traces)
        );
    }

    public static AnalysisResponseDto.ImpactDto mapImpact(Impact impact) {
        return new AnalysisResponseDto.ImpactDto(
                impact.entityId(),
                impact.callers().stream().map(AnalysisResponseMapper::mapImpactItem).toList(),
                impact.dependents().stream().map(AnalysisResponseMapper::mapImpactItem).toList(),
                impact.tests().stream().map(AnalysisResponseMapper::mapImpactItem).toList(),
                impact.endpoints().stream().map(AnalysisResponseMapper::mapImpactItem).toList(),
                impact.inferred().stream().map(AnalysisResponseMapper::mapImpactItem).toList()
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

    private static List<AnalysisResponseDto.DocumentationDto> mapDocumentation(RepositoryModel model) {
        var refsByDoc = model.documentationReferences().stream()
                .collect(Collectors.groupingBy(DocumentationReference::documentId));
        List<AnalysisResponseDto.DocumentationDto> docs = new ArrayList<>();
        for (DocumentationDocument document : model.documentation()) {
            docs.add(new AnalysisResponseDto.DocumentationDto(
                    document.id(),
                    document.path(),
                    document.title(),
                    document.sections().stream()
                            .map(section -> new AnalysisResponseDto.DocumentationSectionDto(
                                    section.id(),
                                    section.heading(),
                                    section.text(),
                                    section.startLine()
                            ))
                            .toList(),
                    refsByDoc.getOrDefault(document.id(), List.of()).stream()
                            .map(ref -> new AnalysisResponseDto.DocumentationRefDto(
                                    ref.id(),
                                    ref.sectionId(),
                                    ref.entityId(),
                                    ref.matchedText()
                            ))
                            .toList()
            ));
        }
        docs.sort(Comparator.comparing(AnalysisResponseDto.DocumentationDto::path));
        return docs;
    }

    private static List<AnalysisResponseDto.SymbolDetailDto> mapSymbols(RepositoryModel model) {
        List<AnalysisResponseDto.SymbolDetailDto> details = new ArrayList<>();
        for (Symbol symbol : model.symbols()) {
            if (symbol.kind() == SymbolKind.METHOD || symbol.kind() == SymbolKind.FIELD) {
                continue;
            }
            List<String> fields = model.symbols().stream()
                    .filter(s -> s.parentSymbolId().orElse("").equals(symbol.id()))
                    .filter(s -> s.kind() == SymbolKind.FIELD)
                    .map(Symbol::name)
                    .filter(name -> name != null && !name.isBlank() && !"null".equals(name))
                    .distinct()
                    .sorted()
                    .toList();
            List<String> methods = model.symbols().stream()
                    .filter(s -> s.parentSymbolId().orElse("").equals(symbol.id()))
                    .filter(s -> s.kind() == SymbolKind.METHOD)
                    .map(Symbol::name)
                    .sorted()
                    .toList();
            String moduleName = symbol.moduleId()
                    .flatMap(model::findModule)
                    .map(m -> m.name())
                    .orElse(null);
            details.add(new AnalysisResponseDto.SymbolDetailDto(
                    symbol.id(),
                    symbol.name(),
                    symbol.kind().name().toLowerCase(Locale.ROOT),
                    symbol.moduleId().orElse(null),
                    moduleName,
                    symbol.location().filePath(),
                    symbol.parentSymbolId().orElse(null),
                    fields,
                    methods
            ));
        }
        details.sort(Comparator
                .comparing((AnalysisResponseDto.SymbolDetailDto s) -> s.moduleName() == null ? "" : s.moduleName())
                .thenComparing(AnalysisResponseDto.SymbolDetailDto::name));
        return details;
    }

    private static AnalysisResponseDto.RepositoryMetadataDto mapMetadata(RepositoryMetadata metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        return new AnalysisResponseDto.RepositoryMetadataDto(
                metadata.name().orElse(null),
                metadata.owner().orElse(null),
                metadata.sizeBytes().orElse(null),
                metadata.createdAt().map(Instant::toString).orElse(null),
                metadata.firstCommitAt().map(Instant::toString).orElse(null),
                metadata.defaultBranch().orElse(null),
                metadata.lastCommit().map(AnalysisResponseMapper::mapCommit).orElse(null),
                metadata.commitCount().orElse(null)
        );
    }

    private static AnalysisResponseDto.CommitInfoDto mapCommit(CommitInfo commit) {
        return new AnalysisResponseDto.CommitInfoDto(
                commit.sha().orElse(null),
                commit.message().orElse(null),
                commit.author().orElse(null),
                commit.authoredAt().map(Instant::toString).orElse(null),
                commit.committedAt().map(Instant::toString).orElse(null)
        );
    }

    private static List<AnalysisResponseDto.DiagramDto> mapDiagrams(List<NamedDiagram> diagrams) {
        if (diagrams == null || diagrams.isEmpty()) {
            return List.of();
        }
        return diagrams.stream()
                .map(diagram -> new AnalysisResponseDto.DiagramDto(
                        diagram.type(),
                        diagram.title(),
                        mapGraph(diagram.graph()),
                        diagram.emptyMessage().orElse(null),
                        diagram.advisoryMessage().orElse(null),
                        diagram.totalNodeCount(),
                        diagram.truncated()
                ))
                .toList();
    }

    private static List<AnalysisResponseDto.EndpointDto> mapEndpoints(RepositoryModel model) {
        return model.endpoints().stream()
                .sorted(Comparator
                        .comparing(Endpoint::path)
                        .thenComparing(Endpoint::httpMethod)
                        .thenComparing(Endpoint::id))
                .map(endpoint -> new AnalysisResponseDto.EndpointDto(
                        endpoint.id(),
                        endpoint.httpMethod(),
                        endpoint.path(),
                        endpoint.ownerTypeId().orElse(null),
                        endpoint.handlerMethodId().orElse(null),
                        mapLocation(endpoint.location()),
                        mapEvidence(endpoint.evidence())
                ))
                .toList();
    }

    private static List<AnalysisResponseDto.TestDto> mapTests(RepositoryModel model) {
        return model.tests().stream()
                .sorted(Comparator.comparing(Test::id))
                .map(test -> new AnalysisResponseDto.TestDto(
                        test.id(),
                        test.symbolId(),
                        test.frameworkHint().orElse(null),
                        mapLocation(test.location()),
                        mapEvidence(test.evidence())
                ))
                .toList();
    }

    private static List<AnalysisResponseDto.TraceDto> mapTraces(List<Trace> traces) {
        if (traces == null || traces.isEmpty()) {
            return List.of();
        }
        return traces.stream()
                .map(trace -> new AnalysisResponseDto.TraceDto(
                        trace.id(),
                        trace.endpointId(),
                        trace.hops().stream().map(AnalysisResponseMapper::mapHop).toList(),
                        trace.confidence(),
                        trace.unresolved(),
                        trace.inferenceKind()
                ))
                .toList();
    }

    private static AnalysisResponseDto.TraceHopDto mapHop(TraceHop hop) {
        return new AnalysisResponseDto.TraceHopDto(
                hop.entityId(),
                hop.role(),
                hop.relationshipId().orElse(null),
                mapEvidence(hop.evidence()),
                hop.confidence(),
                hop.resolved()
        );
    }

    private static AnalysisResponseDto.ImpactItemDto mapImpactItem(ImpactItem item) {
        return new AnalysisResponseDto.ImpactItemDto(
                item.entityId(),
                item.category(),
                item.inferred(),
                item.confidence(),
                mapEvidence(item.evidence()),
                item.relationshipId().orElse(null),
                item.traceId().orElse(null)
        );
    }

    private static AnalysisResponseDto.EvidenceDto mapEvidence(Evidence evidence) {
        return new AnalysisResponseDto.EvidenceDto(
                evidence.inferenceMethod().name(),
                evidence.location().map(AnalysisResponseMapper::mapLocation).orElse(null),
                evidence.referencedId().orElse(null),
                evidence.summary().orElse(null)
        );
    }

    private static AnalysisResponseDto.SourceLocationDto mapLocation(SourceLocation location) {
        return new AnalysisResponseDto.SourceLocationDto(
                location.filePath(),
                location.startLine(),
                location.startColumn(),
                location.endLine(),
                location.endColumn()
        );
    }
}

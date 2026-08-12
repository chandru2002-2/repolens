package io.repolens.analyzers;

import io.repolens.core.model.AnalysisResult;
import io.repolens.core.model.GraphView;
import io.repolens.core.model.Module;
import io.repolens.core.model.Relationship;
import io.repolens.core.model.RepositoryModel;
import io.repolens.core.model.Symbol;
import io.repolens.core.model.SymbolKind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Projects RepositoryModel (+ analyzer relationships) into a GraphView for API/UI.
 * Does not parse source.
 *
 * <p>Node policy:
 * <ul>
 *   <li>repository + all modules</li>
 *   <li>all CLASS / INTERFACE / ENUM / TYPE</li>
 *   <li>METHOD / FIELD members capped per parent type</li>
 *   <li>top-level FUNCTION only (no parent), capped at {@link #MAX_TOP_LEVEL_FUNCTIONS}</li>
 * </ul>
 */
public final class GraphViewProjector {

    static final int MAX_TOP_LEVEL_FUNCTIONS = 40;
    static final int MAX_MEMBERS_PER_TYPE = 24;

    private GraphViewProjector() {
    }

    public static GraphView project(RepositoryModel model, List<AnalysisResult> analysisResults) {
        List<GraphView.Node> nodes = new ArrayList<>();
        List<GraphView.Edge> edges = new ArrayList<>();
        Set<String> nodeIds = new HashSet<>();

        String repoNodeId = "node:repo:" + model.repository().id();
        addNode(nodes, nodeIds, repoNodeId, model.repository().name(), "repository", model.repository().id());

        for (Module module : model.modules()) {
            String moduleNodeId = "node:" + module.id();
            addNode(nodes, nodeIds, moduleNodeId, module.name(), "module", module.id());
            edges.add(new GraphView.Edge(
                    "edge:contains:" + module.id(),
                    repoNodeId,
                    moduleNodeId,
                    "CONTAINS"
            ));
        }

        for (Symbol symbol : selectedSymbols(model)) {
            String symbolNodeId = "node:" + symbol.id();
            addNode(
                    nodes,
                    nodeIds,
                    symbolNodeId,
                    symbol.name(),
                    symbol.kind().name().toLowerCase(Locale.ROOT),
                    symbol.id()
            );
            if (symbol.parentSymbolId().isPresent()) {
                String parentNode = "node:" + symbol.parentSymbolId().get();
                if (nodeIds.contains(parentNode)) {
                    edges.add(new GraphView.Edge(
                            "edge:contains:" + symbol.id(),
                            parentNode,
                            symbolNodeId,
                            "CONTAINS"
                    ));
                }
            } else {
                symbol.moduleId().ifPresent(moduleId -> edges.add(new GraphView.Edge(
                        "edge:contains:" + symbol.id(),
                        "node:" + moduleId,
                        symbolNodeId,
                        "CONTAINS"
                )));
            }
        }

        int edgeSeq = 0;
        for (Relationship relationship : model.relationships()) {
            if (relationship.type().name().equals("CONTAINS")) {
                continue;
            }
            if (relationship.type().name().equals("IMPORTS")) {
                continue;
            }
            edgeSeq = maybeAddEdge(edges, nodeIds, relationship, "edge:model-", edgeSeq);
        }

        for (AnalysisResult result : analysisResults) {
            for (Relationship relationship : result.relationships()) {
                edgeSeq = maybeAddEdge(edges, nodeIds, relationship, "edge:analysis-", edgeSeq);
            }
        }

        return new GraphView("graph:" + model.repository().id(), nodes, edges);
    }

    private static List<Symbol> selectedSymbols(RepositoryModel model) {
        List<Symbol> selected = new ArrayList<>();
        for (Symbol symbol : model.symbols()) {
            if (isTypeSymbol(symbol.kind())) {
                selected.add(symbol);
            }
        }

        for (Symbol type : List.copyOf(selected)) {
            List<Symbol> members = model.symbols().stream()
                    .filter(symbol -> symbol.parentSymbolId().orElse("").equals(type.id()))
                    .filter(symbol -> symbol.kind() == SymbolKind.METHOD || symbol.kind() == SymbolKind.FIELD)
                    .sorted(Comparator
                            .comparing((Symbol s) -> s.kind() == SymbolKind.FIELD ? 0 : 1)
                            .thenComparing(Symbol::name)
                            .thenComparing(s -> s.location().filePath()))
                    .limit(MAX_MEMBERS_PER_TYPE)
                    .toList();
            selected.addAll(members);
        }

        List<Symbol> topLevelFunctions = model.symbols().stream()
                .filter(symbol -> symbol.kind() == SymbolKind.FUNCTION)
                .filter(symbol -> symbol.parentSymbolId().isEmpty())
                .sorted(Comparator.comparing(Symbol::name).thenComparing(s -> s.location().filePath()))
                .limit(MAX_TOP_LEVEL_FUNCTIONS)
                .toList();
        selected.addAll(topLevelFunctions);
        return selected;
    }

    private static boolean isTypeSymbol(SymbolKind kind) {
        return kind == SymbolKind.CLASS
                || kind == SymbolKind.INTERFACE
                || kind == SymbolKind.ENUM
                || kind == SymbolKind.TYPE;
    }

    private static int maybeAddEdge(
            List<GraphView.Edge> edges,
            Set<String> nodeIds,
            Relationship relationship,
            String idPrefix,
            int edgeSeq
    ) {
        String from = "node:" + relationship.fromId();
        String to = "node:" + relationship.toId();
        if (nodeIds.contains(from) && nodeIds.contains(to)) {
            edges.add(new GraphView.Edge(
                    idPrefix + (++edgeSeq),
                    from,
                    to,
                    relationship.type().name()
            ));
        }
        return edgeSeq;
    }

    private static void addNode(
            List<GraphView.Node> nodes,
            Set<String> nodeIds,
            String id,
            String label,
            String kind,
            String sourceEntityId
    ) {
        if (nodeIds.add(id)) {
            nodes.add(new GraphView.Node(id, label, kind, Optional.ofNullable(sourceEntityId)));
        }
    }
}

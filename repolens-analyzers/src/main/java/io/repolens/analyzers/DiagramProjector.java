package io.repolens.analyzers;

import io.repolens.core.model.GraphView;
import io.repolens.core.model.NamedDiagram;
import io.repolens.core.model.RelationshipType;
import io.repolens.core.model.RepositoryModel;
import io.repolens.core.model.StructuralFact;
import io.repolens.core.model.Symbol;
import io.repolens.core.model.SymbolKind;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Projects specialized interactive diagrams from RepositoryModel structural facts.
 * Does not parse source. Applies hard node/edge caps for large repositories.
 */
public final class DiagramProjector {

    public static final int MAX_NODES = 80;
    public static final int MAX_EDGES = 120;

    private DiagramProjector() {
    }

    public static List<NamedDiagram> projectAll(RepositoryModel model) {
        List<NamedDiagram> diagrams = new ArrayList<>();
        diagrams.add(projectSequence(model));
        diagrams.add(projectEr(model));
        diagrams.add(projectDfd(model));
        diagrams.add(projectActivity(model));
        diagrams.add(projectDeployment(model));
        diagrams.add(projectUseCase(model));
        diagrams.add(projectStateMachine(model));
        return diagrams;
    }

    static NamedDiagram projectSequence(RepositoryModel model) {
        List<StructuralFact> facts = model.structuralFacts("sequence");
        if (facts.isEmpty() && model.relationships().stream().noneMatch(r -> r.type() == RelationshipType.CALLS)) {
            return NamedDiagram.empty(
                    "sequence",
                    "Sequence",
                    "No statically resolvable interaction chain was found (controller/service/repository calls)."
            );
        }
        Builder graph = new Builder("sequence");
        graph.add("node:actor:user", "User", "actor", Optional.empty());
        boolean hasDb = facts.stream().anyMatch(f -> "database".equals(f.kind()));
        if (hasDb) {
            graph.add("node:db:database", "Database", "database", Optional.empty());
        }

        // Roles before calls so node kinds stay controller/service/repository.
        for (StructuralFact fact : facts) {
            if (Set.of("controller", "service", "repository").contains(fact.kind())) {
                String id = nodeId(fact);
                graph.add(id, fact.label(), fact.kind(), fact.sourceEntityId());
                if ("controller".equals(fact.kind())) {
                    graph.edge("seq:user->" + id, "node:actor:user", id, "CALLS");
                }
            }
        }

        for (StructuralFact fact : facts) {
            if (!"call".equals(fact.kind())) {
                continue;
            }
            if (fact.sourceEntityId().isEmpty() || fact.targetEntityId().isEmpty()) {
                continue;
            }
            String from = "node:" + fact.sourceEntityId().get();
            String to = "node:" + fact.targetEntityId().get();
            ensureSymbolNode(graph, model, fact.sourceEntityId().get());
            ensureSymbolNode(graph, model, fact.targetEntityId().get());
            String label = fact.detail().orElse("call");
            graph.edge("seq-call:" + fact.id(), from, to, "CALLS:" + label);
        }

        // Link controller->service->repository by role when calls missing
        linkRoles(graph, facts, "controller", "service");
        linkRoles(graph, facts, "service", "repository");
        if (hasDb) {
            for (StructuralFact fact : facts) {
                if ("repository".equals(fact.kind())) {
                    graph.edge("seq:repo-db:" + fact.id(), nodeId(fact), "node:db:database", "CALLS");
                }
            }
        }

        return graph.toDiagram("sequence", "Sequence",
                "No statically resolvable interaction chain was found.");
    }

    static NamedDiagram projectEr(RepositoryModel model) {
        List<StructuralFact> facts = model.structuralFacts("er");
        if (facts.stream().noneMatch(f -> f.kind().equals("entity"))) {
            return NamedDiagram.empty(
                    "er",
                    "ER",
                    "No JPA/persistence entities were detected (@Entity)."
            );
        }
        Builder graph = new Builder("er");
        // Two passes: Map.copyOf does not preserve insertion order, so entity nodes
        // must be created before relationships call ensureSymbolNode (kind=class).
        for (StructuralFact fact : facts) {
            if ("entity".equals(fact.kind())) {
                graph.add(nodeId(fact), fact.label(), "entity", fact.sourceEntityId());
            }
        }
        for (StructuralFact fact : facts) {
            if ("primary_key".equals(fact.kind()) && fact.sourceEntityId().isPresent()) {
                String parent = "node:" + fact.sourceEntityId().get();
                String id = "node:pk:" + fact.id();
                graph.add(id, "PK " + fact.label(), "primary_key", fact.sourceEntityId());
                graph.edge("er-pk:" + fact.id(), parent, id, "CONTAINS");
            }
            if (isErRelationshipKind(fact.kind())) {
                if (fact.sourceEntityId().isEmpty()) {
                    continue;
                }
                String from = "node:" + fact.sourceEntityId().get();
                ensureEntityNode(graph, model, fact.sourceEntityId().get());
                String to;
                if (fact.targetEntityId().isPresent()) {
                    to = "node:" + fact.targetEntityId().get();
                    ensureEntityNode(graph, model, fact.targetEntityId().get());
                } else {
                    to = "node:er-target:" + fact.id();
                    String targetLabel = fact.label().contains("->")
                            ? fact.label().substring(fact.label().indexOf("->") + 2)
                            : fact.label();
                    graph.add(to, targetLabel, "entity", Optional.empty());
                }
                graph.edge("er-rel:" + fact.id(), from, to, fact.kind().toUpperCase(Locale.ROOT));
            }
        }
        return graph.toDiagram("er", "ER", "No entity relationships were detected.");
    }

    private static boolean isErRelationshipKind(String kind) {
        return "one_to_one".equals(kind)
                || "one_to_many".equals(kind)
                || "many_to_one".equals(kind)
                || "many_to_many".equals(kind)
                || "association".equals(kind);
    }

    private static void ensureEntityNode(Builder graph, RepositoryModel model, String symbolId) {
        String nodeId = "node:" + symbolId;
        if (graph.contains(nodeId)) {
            return;
        }
        Optional<Symbol> symbol = model.findSymbol(symbolId);
        if (symbol.isPresent()) {
            graph.add(nodeId, symbol.get().name(), "entity", Optional.of(symbolId));
        } else {
            graph.add(nodeId, symbolId, "entity", Optional.of(symbolId));
        }
    }

    static NamedDiagram projectDfd(RepositoryModel model) {
        List<StructuralFact> facts = model.structuralFacts("dfd");
        if (facts.isEmpty()) {
            return NamedDiagram.empty(
                    "dfd",
                    "DFD",
                    "No data-flow boundaries (API processes / data stores) were detected."
            );
        }
        Builder graph = new Builder("dfd");
        graph.add("node:ext:user", "User", "external_entity", Optional.empty());
        for (StructuralFact fact : facts) {
            String id = nodeId(fact);
            String kind = switch (fact.kind()) {
                case "data_store" -> "data_store";
                case "data_flow" -> "data_flow";
                default -> "process";
            };
            if ("data_flow".equals(kind)) {
                if (fact.sourceEntityId().isPresent() && fact.targetEntityId().isPresent()) {
                    ensureSymbolNode(graph, model, fact.sourceEntityId().get());
                    ensureSymbolNode(graph, model, fact.targetEntityId().get());
                    graph.edge("dfd:" + fact.id(),
                            "node:" + fact.sourceEntityId().get(),
                            "node:" + fact.targetEntityId().get(),
                            "DATA_FLOW");
                }
                continue;
            }
            graph.add(id, fact.label(), kind, fact.sourceEntityId());
            if ("process".equals(kind) && fact.detail().orElse("").contains("api")) {
                graph.edge("dfd:user->" + id, "node:ext:user", id, "DATA_FLOW");
            }
        }
        return graph.toDiagram("dfd", "DFD", "No data-flow edges were detected.");
    }

    static NamedDiagram projectActivity(RepositoryModel model) {
        List<StructuralFact> facts = model.structuralFacts("activity");
        if (facts.isEmpty()) {
            return NamedDiagram.empty(
                    "activity",
                    "Activity",
                    "No method control-flow activity could be summarized from analyzed sources."
            );
        }
        Builder graph = new Builder("activity");
        // Scope to first few methods
        Map<String, List<StructuralFact>> byMethod = new LinkedHashMap<>();
        for (StructuralFact fact : facts) {
            String scope = fact.sourceEntityId().orElse("unknown");
            byMethod.computeIfAbsent(scope, ignored -> new ArrayList<>()).add(fact);
        }
        int methods = 0;
        for (Map.Entry<String, List<StructuralFact>> entry : byMethod.entrySet()) {
            if (methods >= 4) {
                break;
            }
            methods++;
            String prev = null;
            for (StructuralFact fact : entry.getValue()) {
                String id = "node:act:" + fact.id();
                graph.add(id, fact.label(), fact.kind(), fact.sourceEntityId());
                if (prev != null) {
                    graph.edge("act:" + prev + "->" + id, prev, id, "NEXT");
                }
                prev = id;
            }
        }
        return graph.toDiagram("activity", "Activity", "No activity steps were detected.");
    }

    static NamedDiagram projectDeployment(RepositoryModel model) {
        List<StructuralFact> facts = model.structuralFacts("deployment");
        if (facts.isEmpty()) {
            return NamedDiagram.empty(
                    "deployment",
                    "Deployment",
                    "No deployment configuration was detected (Docker Compose, Dockerfile, Kubernetes, datasource)."
            );
        }
        Builder graph = new Builder("deployment");
        graph.add("node:client:browser", "Browser", "client", Optional.empty());
        String firstService = null;
        for (StructuralFact fact : facts) {
            if ("links".equals(fact.kind()) || "image".equals(fact.kind())) {
                continue;
            }
            String id = "node:deploy:" + fact.id();
            graph.add(id, fact.label(), normalizeDeployKind(fact.kind()), Optional.empty());
            if (firstService == null && Set.of("service", "container", "application").contains(normalizeDeployKind(fact.kind()))) {
                firstService = id;
                graph.edge("deploy:browser->" + id, "node:client:browser", id, "CONNECTS");
            }
        }
        for (StructuralFact fact : facts) {
            if (!"links".equals(fact.kind())) {
                continue;
            }
            String[] parts = fact.label().split("->", 2);
            if (parts.length != 2) {
                continue;
            }
            String from = findDeployNode(graph, parts[0].trim());
            String to = findDeployNode(graph, parts[1].trim());
            if (from != null && to != null) {
                graph.edge("deploy-link:" + fact.id(), from, to, "DEPENDS_ON");
            }
        }
        // Connect app services to databases/caches when both exist
        List<String> apps = graph.nodesOfKinds(Set.of("service", "container", "application"));
        List<String> stores = graph.nodesOfKinds(Set.of("database", "cache", "message_broker"));
        for (String app : apps) {
            for (String store : stores) {
                graph.edge("deploy:" + app + "->" + store, app, store, "DEPENDS_ON");
            }
        }
        return graph.toDiagram("deployment", "Deployment", "No deployment nodes were detected.");
    }

    static NamedDiagram projectUseCase(RepositoryModel model) {
        List<StructuralFact> facts = model.structuralFacts("usecase");
        List<StructuralFact> useCases = facts.stream().filter(f -> f.kind().equals("use_case")).toList();
        if (useCases.isEmpty()) {
            // Fall back to public controller-like type names
            List<Symbol> controllers = model.symbols().stream()
                    .filter(s -> s.kind() == SymbolKind.CLASS && s.name().endsWith("Controller"))
                    .limit(20)
                    .toList();
            if (controllers.isEmpty()) {
                return NamedDiagram.empty(
                        "usecase",
                        "Use Case",
                        "No user-facing use cases were inferred from controllers or API mappings."
                );
            }
            Builder graph = new Builder("usecase");
            graph.add("node:actor:user", "User", "actor", Optional.empty());
            for (Symbol controller : controllers) {
                String uc = controller.name().replace("Controller", "");
                String id = "node:uc:" + controller.id();
                graph.add(id, uc.isBlank() ? controller.name() : uc, "use_case", Optional.of(controller.id()));
                graph.edge("uc:user->" + id, "node:actor:user", id, "ASSOCIATES");
            }
            return graph.toDiagram("usecase", "Use Case", "No use cases were detected.");
        }
        Builder graph = new Builder("usecase");
        graph.add("node:actor:user", "User", "actor", Optional.empty());
        for (StructuralFact fact : useCases) {
            String id = "node:uc:" + fact.id();
            graph.add(id, fact.label(), "use_case", fact.sourceEntityId());
            graph.edge("uc:user->" + id, "node:actor:user", id, "ASSOCIATES");
        }
        return graph.toDiagram("usecase", "Use Case", "No use cases were detected.");
    }

    static NamedDiagram projectStateMachine(RepositoryModel model) {
        List<StructuralFact> facts = model.structuralFacts("state");
        List<StructuralFact> states = facts.stream().filter(f -> f.kind().equals("state")).toList();
        if (states.isEmpty()) {
            // Enum constants as states without transitions
            List<Symbol> enums = model.symbols().stream()
                    .filter(s -> s.kind() == SymbolKind.ENUM)
                    .limit(10)
                    .toList();
            if (enums.isEmpty()) {
                return NamedDiagram.empty(
                        "state",
                        "State Machine",
                        "No state model was detected (enums / status transitions)."
                );
            }
            Builder graph = new Builder("state");
            for (Symbol symbol : enums) {
                graph.add("node:" + symbol.id(), symbol.name(), "state", Optional.of(symbol.id()));
            }
            return graph.toDiagram("state", "State Machine",
                    "States detected as enums; no transitions could be established confidently.");
        }
        Builder graph = new Builder("state");
        for (StructuralFact fact : states) {
            graph.add("node:state:" + fact.label(), fact.label(), "state", fact.sourceEntityId());
        }
        for (StructuralFact fact : facts) {
            if (!"transition".equals(fact.kind())) {
                continue;
            }
            String[] parts = fact.label().split("->", 2);
            if (parts.length != 2) {
                continue;
            }
            String from = "node:state:" + parts[0].trim();
            String to = "node:state:" + parts[1].trim();
            graph.add(from, parts[0].trim(), "state", fact.sourceEntityId());
            graph.add(to, parts[1].trim(), "state", fact.sourceEntityId());
            graph.edge("state:" + fact.id(), from, to, "TRANSITION");
        }
        return graph.toDiagram("state", "State Machine", "No state transitions were detected.");
    }

    private static void linkRoles(Builder graph, List<StructuralFact> facts, String fromRole, String toRole) {
        List<StructuralFact> from = facts.stream().filter(f -> fromRole.equals(f.kind())).toList();
        List<StructuralFact> to = facts.stream().filter(f -> toRole.equals(f.kind())).toList();
        for (StructuralFact a : from) {
            for (StructuralFact b : to) {
                graph.edge("seq-role:" + a.id() + "->" + b.id(), nodeId(a), nodeId(b), "CALLS");
            }
        }
    }

    private static void ensureSymbolNode(Builder graph, RepositoryModel model, String symbolId) {
        String nodeId = "node:" + symbolId;
        if (graph.contains(nodeId)) {
            return;
        }
        Optional<Symbol> symbol = model.findSymbol(symbolId);
        if (symbol.isPresent()) {
            graph.add(nodeId, symbol.get().name(), symbol.get().kind().name().toLowerCase(Locale.ROOT),
                    Optional.of(symbolId));
        } else {
            graph.add(nodeId, symbolId, "type", Optional.of(symbolId));
        }
    }

    private static String nodeId(StructuralFact fact) {
        return fact.sourceEntityId().map(id -> "node:" + id).orElse("node:fact:" + fact.id());
    }

    private static String normalizeDeployKind(String kind) {
        if (kind.startsWith("k8s_")) {
            return "service";
        }
        return switch (kind) {
            case "database", "cache", "message_broker", "container", "client", "service" -> kind;
            default -> "service";
        };
    }

    private static String findDeployNode(Builder graph, String label) {
        return graph.findByLabel(label);
    }

    private static final class Builder {
        private final String type;
        private final Map<String, GraphView.Node> nodes = new LinkedHashMap<>();
        private final List<GraphView.Edge> edges = new ArrayList<>();
        private final Set<String> edgeKeys = new HashSet<>();
        private int totalNodes = 0;
        private boolean truncated = false;

        Builder(String type) {
            this.type = type;
        }

        void add(String id, String label, String kind, Optional<String> sourceEntityId) {
            if (nodes.containsKey(id)) {
                return;
            }
            totalNodes++;
            if (nodes.size() >= MAX_NODES) {
                truncated = true;
                return;
            }
            nodes.put(id, new GraphView.Node(id, label, kind, sourceEntityId));
        }

        boolean contains(String id) {
            return nodes.containsKey(id);
        }

        void edge(String id, String from, String to, String type) {
            if (!nodes.containsKey(from) || !nodes.containsKey(to)) {
                return;
            }
            String key = from + "|" + to + "|" + type;
            if (!edgeKeys.add(key)) {
                return;
            }
            if (edges.size() >= MAX_EDGES) {
                truncated = true;
                return;
            }
            edges.add(new GraphView.Edge(id, from, to, type));
        }

        List<String> nodesOfKinds(Set<String> kinds) {
            return nodes.values().stream()
                    .filter(n -> kinds.contains(n.kind()))
                    .map(GraphView.Node::id)
                    .toList();
        }

        String findByLabel(String label) {
            return nodes.values().stream()
                    .filter(n -> n.label().equals(label))
                    .map(GraphView.Node::id)
                    .findFirst()
                    .orElse(null);
        }

        NamedDiagram toDiagram(String type, String title, String emptyMessage) {
            if (nodes.isEmpty()) {
                return NamedDiagram.empty(type, title, emptyMessage);
            }
            GraphView graph = new GraphView("diagram:" + type, List.copyOf(nodes.values()), List.copyOf(edges));
            Optional<String> note = truncated
                    ? Optional.of("Showing " + nodes.size() + " of " + totalNodes + " nodes (large-repository limit).")
                    : Optional.empty();
            return new NamedDiagram(type, title, graph, note, totalNodes, truncated);
        }
    }
}

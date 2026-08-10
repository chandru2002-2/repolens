package io.repolens.core.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * UI/API-facing graph projection derived from RepositoryModel / AnalysisResult.
 * Renderers consume this; they do not parse source.
 */
public record GraphView(
        String id,
        List<Node> nodes,
        List<Edge> edges
) {
    public GraphView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(nodes, "nodes");
        Objects.requireNonNull(edges, "edges");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
    }

    public record Node(
            String id,
            String label,
            String kind,
            Optional<String> sourceEntityId
    ) {
        public Node {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(sourceEntityId, "sourceEntityId");
            if (id.isBlank() || label.isBlank() || kind.isBlank()) {
                throw new IllegalArgumentException("id, label, and kind must not be blank");
            }
        }
    }

    public record Edge(
            String id,
            String fromNodeId,
            String toNodeId,
            String type
    ) {
        public Edge {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(fromNodeId, "fromNodeId");
            Objects.requireNonNull(toNodeId, "toNodeId");
            Objects.requireNonNull(type, "type");
            if (id.isBlank() || fromNodeId.isBlank() || toNodeId.isBlank() || type.isBlank()) {
                throw new IllegalArgumentException("edge fields must not be blank");
            }
        }
    }
}

package io.repolens.core.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Named diagram projection for API/UI. {@link #graph()} may be empty with
 * {@link #emptyMessage()} explaining why.
 */
public record NamedDiagram(
        String type,
        String title,
        GraphView graph,
        Optional<String> emptyMessage,
        int totalNodeCount,
        boolean truncated
) {
    public NamedDiagram {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(emptyMessage, "emptyMessage");
        if (type.isBlank() || title.isBlank()) {
            throw new IllegalArgumentException("type and title must not be blank");
        }
        if (totalNodeCount < 0) {
            throw new IllegalArgumentException("totalNodeCount must be >= 0");
        }
    }

    public static NamedDiagram of(String type, String title, GraphView graph) {
        return new NamedDiagram(type, title, graph, Optional.empty(), graph.nodes().size(), false);
    }

    public static NamedDiagram empty(String type, String title, String message) {
        return new NamedDiagram(
                type,
                title,
                new GraphView("diagram:" + type + ":empty", List.of(), List.of()),
                Optional.of(message),
                0,
                false
        );
    }
}

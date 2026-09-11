package io.repolens.core.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Named diagram projection for API/UI.
 *
 * <p>{@link #emptyMessage()} is only for diagrams with no nodes. Truncation,
 * missing relationships, and similar notes belong in {@link #advisoryMessage()}
 * so the graph can still be rendered.
 */
public record NamedDiagram(
        String type,
        String title,
        GraphView graph,
        Optional<String> emptyMessage,
        Optional<String> advisoryMessage,
        int totalNodeCount,
        boolean truncated
) {
    public NamedDiagram {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(emptyMessage, "emptyMessage");
        Objects.requireNonNull(advisoryMessage, "advisoryMessage");
        if (type.isBlank() || title.isBlank()) {
            throw new IllegalArgumentException("type and title must not be blank");
        }
        if (totalNodeCount < 0) {
            throw new IllegalArgumentException("totalNodeCount must be >= 0");
        }
        boolean hasNodes = !graph.nodes().isEmpty();
        if (hasNodes) {
            emptyMessage = Optional.empty();
        } else {
            advisoryMessage = Optional.empty();
            truncated = false;
            totalNodeCount = 0;
        }
    }

    public static NamedDiagram of(String type, String title, GraphView graph) {
        return new NamedDiagram(
                type,
                title,
                graph,
                Optional.empty(),
                Optional.empty(),
                graph.nodes().size(),
                false
        );
    }

    public static NamedDiagram empty(String type, String title, String message) {
        return new NamedDiagram(
                type,
                title,
                new GraphView("diagram:" + type + ":empty", List.of(), List.of()),
                Optional.of(message),
                Optional.empty(),
                0,
                false
        );
    }
}

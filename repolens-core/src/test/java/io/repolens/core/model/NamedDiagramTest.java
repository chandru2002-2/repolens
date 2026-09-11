package io.repolens.core.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NamedDiagramTest {

    @Test
    void nodesPlusLegacyEmptyMessageStillRenderAsNonEmpty() {
        GraphView graph = new GraphView(
                "g",
                List.of(new GraphView.Node("n1", "Owner", "entity", Optional.empty())),
                List.of()
        );
        NamedDiagram diagram = new NamedDiagram(
                "er",
                "ER",
                graph,
                Optional.of("should not hide nodes"),
                Optional.of("no relationships"),
                1,
                false
        );
        assertEquals(1, diagram.graph().nodes().size());
        assertTrue(diagram.emptyMessage().isEmpty());
        assertEquals("no relationships", diagram.advisoryMessage().orElse(""));
    }

    @Test
    void truncatedNonEmptyKeepsGraph() {
        GraphView graph = new GraphView(
                "g",
                List.of(new GraphView.Node("n1", "A", "service", Optional.empty())),
                List.of()
        );
        NamedDiagram diagram = new NamedDiagram(
                "sequence",
                "Sequence",
                graph,
                Optional.empty(),
                Optional.of("Showing 1 of 90 nodes (large-repository limit)."),
                90,
                true
        );
        assertFalse(diagram.graph().nodes().isEmpty());
        assertTrue(diagram.truncated());
        assertTrue(diagram.emptyMessage().isEmpty());
        assertTrue(diagram.advisoryMessage().isPresent());
    }

    @Test
    void emptyDiagramUsesEmptyMessageOnly() {
        NamedDiagram empty = NamedDiagram.empty("er", "ER", "No entities");
        assertTrue(empty.graph().nodes().isEmpty());
        assertEquals("No entities", empty.emptyMessage().orElse(""));
        assertTrue(empty.advisoryMessage().isEmpty());
        assertFalse(empty.truncated());
    }
}

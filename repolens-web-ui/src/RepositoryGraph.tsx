import { useEffect, useMemo, useRef } from "react";
import cytoscape, { type Core, type StylesheetStyle } from "cytoscape";
import type { GraphEdge, GraphNode } from "./api";
import {
  filterGraphForView,
  kindMeta,
  neighborIds,
  type GraphViewMode,
  type Selection,
} from "./graphModel";
import type { ResolvedTheme } from "./theme";

type Props = {
  nodes: GraphNode[];
  edges: GraphEdge[];
  view: GraphViewMode;
  selection: Selection;
  focusId: string | null;
  onSelect: (selection: Selection) => void;
  theme: ResolvedTheme;
};

type GraphPalette = {
  ink: string;
  panel: string;
  paper2: string;
  accent: string;
  accentSoft: string;
  line: string;
  lineStrong: string;
  muted: string;
  select: string;
};

function paletteFor(theme: ResolvedTheme): GraphPalette {
  if (theme === "dark") {
    return {
      ink: "#ebe6dc",
      panel: "#1a1815",
      paper2: "#1e1c19",
      accent: "#8fad9a",
      accentSoft: "#a8c4b2",
      line: "#3a3630",
      lineStrong: "#5a544a",
      muted: "#9a9388",
      select: "#2a332c",
    };
  }
  return {
    ink: "#1c1b19",
    panel: "#f7f5ef",
    paper2: "#e8e4db",
    accent: "#3f5c4c",
    accentSoft: "#5a7a68",
    line: "#b8b2a6",
    lineStrong: "#8a8478",
    muted: "#6a6660",
    select: "#d9e2db",
  };
}

function graphStyles(palette: GraphPalette): StylesheetStyle[] {
  return [
    {
      selector: "node",
      style: {
        label: "data(label)",
        color: palette.ink,
        "font-family": "IBM Plex Mono, monospace",
        "font-size": 10,
        "text-valign": "center",
        "text-halign": "center",
        "text-wrap": "wrap",
        "text-max-width": 108,
        "background-color": palette.panel,
        "border-width": 1.25,
        "border-color": palette.ink,
        width: 118,
        height: 46,
        shape: "rectangle",
        opacity: 1,
        "transition-property": "background-color, border-color, opacity",
        "transition-duration": 120,
      },
    },
    {
      selector: 'node[kind = "repository"]',
      style: {
        "background-color": palette.paper2,
        "border-width": 1.75,
        width: 132,
        height: 48,
        "font-weight": 600,
      },
    },
    {
      selector: 'node[kind = "module"]',
      style: {
        "background-color": palette.panel,
        "border-color": palette.accent,
        width: 124,
        height: 42,
      },
    },
    {
      selector: 'node[kind = "class"]',
      style: {
        "background-color": palette.panel,
      },
    },
    {
      selector: 'node[kind = "interface"]',
      style: {
        "border-style": "dashed",
        "border-color": palette.accent,
      },
    },
    {
      selector: 'node[kind = "function"]',
      style: {
        width: 110,
        height: 40,
        "font-size": 9,
      },
    },
    {
      selector: "edge",
      style: {
        width: 1.1,
        "line-color": palette.lineStrong,
        "target-arrow-color": palette.lineStrong,
        "target-arrow-shape": "triangle",
        "curve-style": "bezier",
        "arrow-scale": 0.8,
        opacity: 0.9,
        label: "data(label)",
        "font-family": "IBM Plex Mono, monospace",
        "font-size": 8,
        color: palette.muted,
        "text-rotation": "autorotate",
        "text-margin-y": -8,
        "transition-property": "opacity, line-color, width",
        "transition-duration": 120,
      },
    },
    {
      selector: 'edge[type = "DEPENDS_ON"]',
      style: {
        "line-color": palette.accent,
        "target-arrow-color": palette.accent,
        width: 1.4,
      },
    },
    {
      selector: 'edge[type = "CONTAINS"]',
      style: {
        "line-color": palette.line,
        "target-arrow-color": palette.line,
        width: 1,
        "line-style": "dashed",
        opacity: 0.65,
      },
    },
    {
      selector: "node.selected",
      style: {
        "border-width": 2.25,
        "border-color": palette.accent,
        "background-color": palette.select,
        "z-index": 10,
      },
    },
    {
      selector: "node.neighbor",
      style: {
        "border-color": palette.accentSoft,
        "border-width": 1.75,
      },
    },
    {
      selector: "node.dimmed",
      style: {
        opacity: 0.16,
      },
    },
    {
      selector: "edge.related",
      style: {
        opacity: 1,
        width: 2,
        "line-color": palette.accent,
        "target-arrow-color": palette.accent,
        color: palette.accent,
        "z-index": 9,
      },
    },
    {
      selector: "edge.dimmed",
      style: {
        opacity: 0.08,
      },
    },
    {
      selector: "edge.selected",
      style: {
        width: 2.2,
        "line-color": palette.ink,
        "target-arrow-color": palette.ink,
        opacity: 1,
      },
    },
  ];
}

function shapeForKind(kind: string): string {
  switch (kind) {
    case "repository":
      return "rectangle";
    case "module":
      return "round-rectangle";
    case "interface":
      return "round-rectangle";
    case "function":
      return "round-rectangle";
    default:
      return "rectangle";
  }
}

function nodeLabel(node: GraphNode): string {
  const meta = kindMeta(node.kind);
  return `${meta.short}\n${node.label}`;
}

export function RepositoryGraph({
  nodes,
  edges,
  view,
  selection,
  focusId,
  onSelect,
  theme,
}: Props) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const cyRef = useRef<Core | null>(null);
  const onSelectRef = useRef(onSelect);
  onSelectRef.current = onSelect;

  const filtered = useMemo(
    () => filterGraphForView(nodes, edges, view),
    [nodes, edges, view],
  );

  useEffect(() => {
    if (!containerRef.current) {
      return;
    }

    const palette = paletteFor(theme);
    const cy = cytoscape({
      container: containerRef.current,
      elements: [
        ...filtered.nodes.map((node) => ({
          data: {
            id: node.id,
            label: nodeLabel(node),
            plainLabel: node.label,
            kind: node.kind,
          },
        })),
        ...filtered.edges.map((edge) => ({
          data: {
            id: edge.id,
            source: edge.fromNodeId,
            target: edge.toNodeId,
            type: edge.type,
            label: edge.type === "DEPENDS_ON" ? "USES" : edge.type === "CONTAINS" ? "" : edge.type,
          },
        })),
      ],
      style: graphStyles(palette),
      layout: layoutOptions(view, filtered.nodes.length),
      wheelSensitivity: 0.22,
      minZoom: 0.15,
      maxZoom: 2.8,
    });

    cy.nodes().forEach((ele) => {
      ele.style("shape", shapeForKind(String(ele.data("kind"))));
    });

    cy.on("tap", "node", (event) => {
      onSelectRef.current({ type: "node", id: event.target.id() });
    });
    cy.on("tap", "edge", (event) => {
      onSelectRef.current({ type: "edge", id: event.target.id() });
    });
    cy.on("tap", (event) => {
      if (event.target === cy) {
        onSelectRef.current(null);
      }
    });

    cyRef.current = cy;
    return () => {
      cy.destroy();
      cyRef.current = null;
    };
  }, [filtered, view, theme]);

  useEffect(() => {
    const cy = cyRef.current;
    if (!cy) {
      return;
    }

    cy.batch(() => {
      cy.elements().removeClass("selected neighbor related dimmed");

      if (selection?.type === "node") {
        const selected = cy.$id(selection.id);
        if (selected.nonempty()) {
          selected.addClass("selected");
          const hop = neighborIds(edges, selection.id);
          cy.nodes().forEach((node) => {
            if (node.id() !== selection.id && hop.has(node.id())) {
              node.addClass("neighbor");
            }
          });
          cy.edges().forEach((edge) => {
            const related =
              edge.data("source") === selection.id ||
              edge.data("target") === selection.id;
            edge.addClass(related ? "related" : "dimmed");
          });
        }
      } else if (selection?.type === "edge") {
        const edge = cy.$id(selection.id);
        if (edge.nonempty()) {
          edge.addClass("selected related");
          cy.$id(edge.data("source")).addClass("neighbor");
          cy.$id(edge.data("target")).addClass("neighbor");
          cy.nodes()
            .difference(`#${edge.data("source")}, #${edge.data("target")}`)
            .addClass("dimmed");
          cy.edges().difference(edge).addClass("dimmed");
        }
      }

      if (focusId) {
        const keep = neighborIds(edges, focusId);
        cy.nodes().forEach((node) => {
          if (!keep.has(node.id())) {
            node.addClass("dimmed");
          }
        });
        cy.edges().forEach((edge) => {
          const keepEdge =
            keep.has(edge.data("source")) && keep.has(edge.data("target"));
          if (!keepEdge) {
            edge.addClass("dimmed");
          } else {
            edge.removeClass("dimmed");
            edge.addClass("related");
          }
        });
        cy.$id(focusId).removeClass("dimmed").addClass("selected");
      }
    });
  }, [selection, focusId, edges]);

  function zoomBy(factor: number) {
    const cy = cyRef.current;
    if (!cy) {
      return;
    }
    cy.zoom({
      level: cy.zoom() * factor,
      renderedPosition: { x: cy.width() / 2, y: cy.height() / 2 },
    });
  }

  function fitGraph() {
    cyRef.current?.fit(undefined, 48);
  }

  function resetView() {
    const cy = cyRef.current;
    if (!cy) {
      return;
    }
    cy.layout(layoutOptions(view, filtered.nodes.length)).run();
    onSelect(null);
  }

  function focusSelected() {
    const cy = cyRef.current;
    if (!cy || selection?.type !== "node") {
      return;
    }
    const node = cy.$id(selection.id);
    if (node.nonempty()) {
      cy.animate({
        center: { eles: node },
        zoom: Math.max(cy.zoom(), 1.05),
        duration: 200,
      });
    }
  }

  return (
    <div className="graph-shell">
      <div className="graph-toolbar" role="toolbar" aria-label="Graph controls">
        <button type="button" onClick={() => zoomBy(1.2)} title="Zoom in">
          +
        </button>
        <button type="button" onClick={() => zoomBy(1 / 1.2)} title="Zoom out">
          -
        </button>
        <button type="button" onClick={fitGraph} title="Fit graph">
          Fit
        </button>
        <button type="button" onClick={resetView} title="Reset layout">
          Reset
        </button>
        <button
          type="button"
          onClick={focusSelected}
          title="Center selected node"
          disabled={selection?.type !== "node"}
        >
          Center
        </button>
      </div>
      <div
        className="graph-canvas"
        ref={containerRef}
        aria-label="Repository architecture graph"
      />
    </div>
  );
}

function layoutOptions(view: GraphViewMode, nodeCount: number) {
  const repulsion = view === "architecture" ? 16000 : view === "packages" ? 14000 : 12000;
  const ideal = view === "architecture" ? 140 : 110;
  return {
    name: "cose" as const,
    animate: false,
    nodeRepulsion: () => repulsion + Math.min(nodeCount, 80) * 50,
    idealEdgeLength: () => ideal,
    gravity: 0.45,
    nestingFactor: 1.1,
    numIter: Math.min(1400, 700 + nodeCount * 5),
    padding: 48,
  };
}

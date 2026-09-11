import { memo, useEffect, useMemo, useRef } from "react";
import cytoscape, { type Core, type StylesheetStyle } from "cytoscape";
import type { GraphEdge, GraphNode } from "./api";
import {
  filterGraphForView,
  kindMeta,
  neighborIds,
  type GraphFilterState,
  type GraphViewMode,
  type Selection,
  DEFAULT_GRAPH_FILTERS,
} from "./graphModel";
import {
  VIEWPORT_BUSY_CLASS,
  attachViewportBusyHandlers,
  createDebouncedResize,
  graphContentSignature,
  shouldRunLayout,
} from "./graphPerf";
import type { ResolvedTheme } from "./theme";

type Props = {
  nodes: GraphNode[];
  edges: GraphEdge[];
  view: GraphViewMode;
  filters?: GraphFilterState;
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
        "min-zoomed-font-size": 0,
        "background-color": palette.panel,
        "border-width": 1.25,
        "border-color": palette.ink,
        width: 118,
        height: 46,
        shape: "rectangle",
        opacity: 1,
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
        shape: "rectangle",
      },
    },
    {
      selector: 'node[kind = "module"]',
      style: {
        "background-color": palette.panel,
        "border-color": palette.accent,
        width: 124,
        height: 42,
        shape: "round-rectangle",
      },
    },
    {
      selector: 'node[kind = "class"]',
      style: {
        "background-color": palette.panel,
        shape: "rectangle",
      },
    },
    {
      selector: 'node[kind = "interface"]',
      style: {
        "border-style": "dashed",
        "border-color": palette.accent,
        shape: "round-rectangle",
      },
    },
    {
      selector: 'node[kind = "function"]',
      style: {
        width: 110,
        height: 40,
        "font-size": 9,
        shape: "round-rectangle",
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
        "min-zoomed-font-size": 8,
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
      selector: 'edge[type = "EXTENDS"]',
      style: {
        "line-color": palette.ink,
        "target-arrow-color": palette.ink,
        "target-arrow-shape": "triangle",
        "line-style": "solid",
        width: 1.6,
        label: "extends",
      },
    },
    {
      selector: 'edge[type = "IMPLEMENTS"]',
      style: {
        "line-color": palette.accentSoft,
        "target-arrow-color": palette.accentSoft,
        "target-arrow-shape": "triangle",
        "line-style": "dashed",
        width: 1.4,
        label: "implements",
      },
    },
    {
      // CONTAINS edges dominate large Architecture graphs — haystack is much cheaper than bezier.
      selector: 'edge[type = "CONTAINS"]',
      style: {
        "line-color": palette.line,
        "target-arrow-color": palette.line,
        width: 1,
        "line-style": "dashed",
        opacity: 0.65,
        "curve-style": "haystack",
        "haystack-radius": 0,
        "target-arrow-shape": "none",
      },
    },
    {
      selector: 'node[kind = "method"], node[kind = "field"]',
      style: {
        width: 96,
        height: 34,
        "font-size": 8,
        "border-width": 1,
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
    // Active zoom/pan: drop labels (and cheapen remaining edge paint). No React involved.
    {
      selector: `node.${VIEWPORT_BUSY_CLASS}`,
      style: {
        "text-opacity": 0,
      },
    },
    {
      selector: `edge.${VIEWPORT_BUSY_CLASS}`,
      style: {
        "text-opacity": 0,
        label: "",
        opacity: 0.2,
        "curve-style": "haystack",
        "haystack-radius": 0,
        "target-arrow-shape": "none",
      },
    },
  ];
}

function nodeLabel(node: GraphNode): string {
  const meta = kindMeta(node.kind);
  return `${meta.short}\n${node.label}`;
}

function toElements(nodes: GraphNode[], edges: GraphEdge[]) {
  return [
    ...nodes.map((node) => ({
      data: {
        id: node.id,
        label: nodeLabel(node),
        plainLabel: node.label,
        kind: node.kind,
      },
    })),
    ...edges.map((edge) => ({
      data: {
        id: edge.id,
        source: edge.fromNodeId,
        target: edge.toNodeId,
        type: edge.type,
        label:
          edge.type === "DEPENDS_ON"
            ? "USES"
            : edge.type === "CONTAINS"
              ? ""
              : edge.type === "EXTENDS"
                ? "extends"
                : edge.type === "IMPLEMENTS"
                  ? "implements"
                  : edge.type,
      },
    })),
  ];
}

function RepositoryGraphComponent({
  nodes,
  edges,
  view,
  filters = DEFAULT_GRAPH_FILTERS,
  selection,
  focusId,
  onSelect,
  theme,
}: Props) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const cyRef = useRef<Core | null>(null);
  const onSelectRef = useRef(onSelect);
  const viewRef = useRef(view);
  const layoutSignatureRef = useRef<string | null>(null);
  const filteredEdgesRef = useRef<GraphEdge[]>([]);
  onSelectRef.current = onSelect;
  viewRef.current = view;

  const filtered = useMemo(
    () => filterGraphForView(nodes, edges, view, filters),
    [nodes, edges, view, filters],
  );
  filteredEdgesRef.current = filtered.edges;

  // Create Cytoscape once — zoom/pan must never destroy or re-layout the instance.
  useEffect(() => {
    if (!containerRef.current || cyRef.current) {
      return;
    }

    const cy = cytoscape({
      container: containerRef.current,
      elements: [],
      style: graphStyles(paletteFor(theme)),
      layout: { name: "null" },
      wheelSensitivity: 0.4,
      minZoom: 0.15,
      maxZoom: 2.8,
      // Still effective in Cytoscape 3.34 for wheel/pan (not for programmatic zoom alone):
      textureOnViewport: true,
      hideEdgesOnViewport: true,
      pixelRatio: "auto",
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

    const detachViewport = attachViewportBusyHandlers(cy);
    const debouncedResize = createDebouncedResize(() => {
      cy.resize();
    });
    const resizeObserver = new ResizeObserver(() => {
      debouncedResize.schedule();
    });
    resizeObserver.observe(containerRef.current);

    cyRef.current = cy;

    return () => {
      detachViewport();
      debouncedResize.cancel();
      resizeObserver.disconnect();
      cy.destroy();
      cyRef.current = null;
      layoutSignatureRef.current = null;
    };
    // theme applied separately; mount once for stable zoom.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Theme stylesheet only — do not rebuild elements or run layout.
  useEffect(() => {
    const cy = cyRef.current;
    if (!cy) {
      return;
    }
    cy.style(graphStyles(paletteFor(theme)));
  }, [theme]);

  // Data / diagram changes: replace elements + layout only when content actually changes.
  useEffect(() => {
    const cy = cyRef.current;
    if (!cy) {
      return;
    }

    const signature = graphContentSignature(filtered.nodes, filtered.edges, view);
    if (!shouldRunLayout(layoutSignatureRef.current, signature)) {
      return;
    }
    layoutSignatureRef.current = signature;

    cy.batch(() => {
      cy.elements().remove();
      cy.add(toElements(filtered.nodes, filtered.edges));
    });

    if (cy.container()) {
      cy.container()!.dataset.graphSize =
        filtered.nodes.length >= 80 ? "large" : "normal";
    }

    const layout = cy.layout({
      ...layoutOptions(view, filtered.nodes.length),
      stop: () => {
        cy.fit(undefined, 48);
      },
    });
    layout.run();
  }, [filtered, view]);

  useEffect(() => {
    const cy = cyRef.current;
    if (!cy) {
      return;
    }

    const graphEdges = filteredEdgesRef.current;

    cy.batch(() => {
      cy.elements().removeClass("selected neighbor related dimmed");

      if (selection?.type === "node") {
        const selected = cy.$id(selection.id);
        if (selected.nonempty()) {
          selected.addClass("selected");
          const hop = neighborIds(graphEdges, selection.id);
          const neighborSelector = [...hop]
            .filter((id) => id !== selection.id)
            .map((id) => `#${CSS.escape(id)}`)
            .join(", ");
          if (neighborSelector) {
            cy.$(neighborSelector).addClass("neighbor");
          }
          cy.edges().forEach((edge) => {
            const related =
              edge.data("source") === selection.id ||
              edge.data("target") === selection.id;
            edge.addClass(related ? "related" : "dimmed");
          });
          cy.nodes().forEach((node) => {
            if (node.id() !== selection.id && !hop.has(node.id())) {
              node.addClass("dimmed");
            }
          });
        }
      } else if (selection?.type === "edge") {
        const edge = cy.$id(selection.id);
        if (edge.nonempty()) {
          edge.addClass("selected related");
          cy.$id(edge.data("source")).addClass("neighbor");
          cy.$id(edge.data("target")).addClass("neighbor");
          cy.nodes()
            .difference(`#${CSS.escape(edge.data("source"))}, #${CSS.escape(edge.data("target"))}`)
            .addClass("dimmed");
          cy.edges().difference(edge).addClass("dimmed");
        }
      }

      if (focusId) {
        const keep = neighborIds(graphEdges, focusId);
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
  }, [selection, focusId, filtered.edges]);

  function zoomBy(factor: number) {
    const cy = cyRef.current;
    if (!cy) {
      return;
    }
    // Imperative zoom only — fires cy 'zoom' → viewport busy handlers; no React state.
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
    layoutSignatureRef.current = null;
    cy.layout(layoutOptions(viewRef.current, cy.nodes().length)).run();
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
        duration: 160,
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
  const repulsion = view === "architecture" ? 16000 : view === "package" ? 14000 : 12000;
  const ideal = view === "architecture" ? 140 : 110;
  return {
    name: "cose" as const,
    animate: false,
    randomize: false,
    nodeRepulsion: () => repulsion + Math.min(nodeCount, 80) * 50,
    idealEdgeLength: () => ideal,
    gravity: 0.45,
    nestingFactor: 1.1,
    // Cap iterations harder on large graphs — layout is not on the zoom path.
    numIter: Math.min(nodeCount >= 80 ? 900 : 1400, 600 + nodeCount * 4),
    padding: 48,
  };
}

export const RepositoryGraph = memo(RepositoryGraphComponent);

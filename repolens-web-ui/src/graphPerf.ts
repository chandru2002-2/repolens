/**
 * Graph interaction performance helpers.
 *
 * Measurement notes (Cytoscape 3.34 in this repo):
 * - `textureOnViewport` and `hideEdgesOnViewport` are real and help during wheel/pan.
 * - `hideLabelsOnViewport` is NOT implemented — labels (especially wrapped node text
 *   and autorotated edge labels) still cost during / after viewport manipulation.
 * - Programmatic `cy.zoom()` (toolbar) does not set `wheelZooming`, so built-in
 *   viewport shortcuts do not activate for +/- buttons unless we handle zoom/pan events.
 * - Per-element `ele.style(...)` bypasses are expensive vs stylesheet selectors.
 * - Re-running `cose` when the visible element id set is unchanged wastes the main thread.
 */

export const VIEWPORT_BUSY_CLASS = "ux-busy";
export const VIEWPORT_IDLE_MS = 140;
export const RESIZE_DEBOUNCE_MS = 48;
export const LARGE_GRAPH_NODE_THRESHOLD = 80;

export type GraphElementRef = { id: string };

/** Stable signature of the visible graph — used to skip redundant layouts. */
export function graphContentSignature(
  nodes: GraphElementRef[],
  edges: Array<GraphElementRef & { fromNodeId?: string; toNodeId?: string; type?: string }>,
  view: string,
): string {
  const nodePart = nodes.map((node) => node.id).join("\n");
  const edgePart = edges
    .map((edge) => `${edge.id}:${edge.fromNodeId ?? ""}>${edge.toNodeId ?? ""}:${edge.type ?? ""}`)
    .join("\n");
  return `${view}|n:${nodes.length}|e:${edges.length}|${nodePart}|${edgePart}`;
}

export function shouldRunLayout(previousSignature: string | null, nextSignature: string): boolean {
  return previousSignature !== nextSignature;
}

export function pixelRatioForGraph(nodeCount: number): number | "auto" {
  return nodeCount >= LARGE_GRAPH_NODE_THRESHOLD ? 1 : "auto";
}

/**
 * Attach zoom/pan listeners that mark elements busy (hide labels / cheapen edges)
 * without touching React state. Returns a disposer.
 */
export function attachViewportBusyHandlers(
  cy: {
    on: (events: string, handler: () => void) => void;
    off: (events: string, handler: () => void) => void;
    batch: (fn: () => void) => void;
    nodes: () => { addClass: (c: string) => void; removeClass: (c: string) => void };
    edges: () => { addClass: (c: string) => void; removeClass: (c: string) => void };
    container: () => HTMLElement | null | undefined;
  },
  options: {
    idleMs?: number;
    now?: () => number;
    setTimeoutFn?: (fn: () => void, ms: number) => number;
    clearTimeoutFn?: (id: number) => void;
  } = {},
): () => void {
  const idleMs = options.idleMs ?? VIEWPORT_IDLE_MS;
  const setTimeoutFn = options.setTimeoutFn ?? ((fn, ms) => window.setTimeout(fn, ms));
  const clearTimeoutFn = options.clearTimeoutFn ?? ((id) => window.clearTimeout(id));

  let busy = false;
  let timer: number | null = null;

  const end = () => {
    timer = null;
    if (!busy) {
      return;
    }
    busy = false;
    cy.batch(() => {
      cy.nodes().removeClass(VIEWPORT_BUSY_CLASS);
      cy.edges().removeClass(VIEWPORT_BUSY_CLASS);
    });
    cy.container()?.classList.remove("graph-viewport-busy");
  };

  const onViewport = () => {
    if (!busy) {
      busy = true;
      cy.batch(() => {
        cy.nodes().addClass(VIEWPORT_BUSY_CLASS);
        cy.edges().addClass(VIEWPORT_BUSY_CLASS);
      });
      cy.container()?.classList.add("graph-viewport-busy");
    }
    if (timer != null) {
      clearTimeoutFn(timer);
    }
    timer = setTimeoutFn(end, idleMs);
  };

  // Include programmatic zoom/pan (toolbar) and wheel/gesture paths.
  const events = "zoom pan dragpan pinchzoom scrollzoom";
  cy.on(events, onViewport);

  return () => {
    cy.off(events, onViewport);
    if (timer != null) {
      clearTimeoutFn(timer);
    }
    if (busy) {
      end();
    }
  };
}

export function createDebouncedResize(
  resize: () => void,
  options: {
    debounceMs?: number;
    setTimeoutFn?: (fn: () => void, ms: number) => number;
    clearTimeoutFn?: (id: number) => void;
  } = {},
): { schedule: () => void; cancel: () => void } {
  const debounceMs = options.debounceMs ?? RESIZE_DEBOUNCE_MS;
  const setTimeoutFn = options.setTimeoutFn ?? ((fn, ms) => window.setTimeout(fn, ms));
  const clearTimeoutFn = options.clearTimeoutFn ?? ((id) => window.clearTimeout(id));
  let timer: number | null = null;

  return {
    schedule: () => {
      if (timer != null) {
        clearTimeoutFn(timer);
      }
      timer = setTimeoutFn(() => {
        timer = null;
        resize();
      }, debounceMs);
    },
    cancel: () => {
      if (timer != null) {
        clearTimeoutFn(timer);
        timer = null;
      }
    },
  };
}

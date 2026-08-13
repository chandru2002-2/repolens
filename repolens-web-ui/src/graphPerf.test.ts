import { describe, expect, it, vi } from "vitest";
import {
  LARGE_GRAPH_NODE_THRESHOLD,
  VIEWPORT_BUSY_CLASS,
  attachViewportBusyHandlers,
  createDebouncedResize,
  graphContentSignature,
  pixelRatioForGraph,
  shouldRunLayout,
} from "./graphPerf";

describe("graphPerf", () => {
  it("skips layout when the visible graph signature is unchanged", () => {
    const nodes = [{ id: "a" }, { id: "b" }];
    const edges = [{ id: "e1", fromNodeId: "a", toNodeId: "b", type: "DEPENDS_ON" }];
    const sig = graphContentSignature(nodes, edges, "architecture");
    expect(shouldRunLayout(null, sig)).toBe(true);
    expect(shouldRunLayout(sig, sig)).toBe(false);
    expect(
      shouldRunLayout(sig, graphContentSignature(nodes, edges, "class")),
    ).toBe(true);
  });

  it("lowers pixel ratio for large graphs", () => {
    expect(pixelRatioForGraph(10)).toBe("auto");
    expect(pixelRatioForGraph(LARGE_GRAPH_NODE_THRESHOLD)).toBe(1);
  });

  it("marks elements busy during viewport events without requiring React", () => {
    const nodes = { addClass: vi.fn(), removeClass: vi.fn() };
    const edges = { addClass: vi.fn(), removeClass: vi.fn() };
    const classSet = new Set<string>();
    const container = {
      classList: {
        add: (name: string) => classSet.add(name),
        remove: (name: string) => classSet.delete(name),
        contains: (name: string) => classSet.has(name),
      },
    };
    let handler: (() => void) | null = null;
    const timers: Array<() => void> = [];

    const cy = {
      on: (_events: string, fn: () => void) => {
        handler = fn;
      },
      off: vi.fn(),
      batch: (fn: () => void) => fn(),
      nodes: () => nodes,
      edges: () => edges,
      container: () => container as unknown as HTMLElement,
    };

    const dispose = attachViewportBusyHandlers(cy, {
      idleMs: 20,
      setTimeoutFn: (fn) => {
        timers.push(fn);
        return timers.length;
      },
      clearTimeoutFn: () => undefined,
    });

    expect(handler).toBeTypeOf("function");
    handler?.();
    expect(nodes.addClass).toHaveBeenCalledWith(VIEWPORT_BUSY_CLASS);
    expect(edges.addClass).toHaveBeenCalledWith(VIEWPORT_BUSY_CLASS);
    expect(container.classList.contains("graph-viewport-busy")).toBe(true);

    // Second event should not re-add while already busy
    nodes.addClass.mockClear();
    handler?.();
    expect(nodes.addClass).not.toHaveBeenCalled();

    // Idle timer restores labels/edges
    timers[timers.length - 1]?.();
    expect(nodes.removeClass).toHaveBeenCalledWith(VIEWPORT_BUSY_CLASS);
    expect(container.classList.contains("graph-viewport-busy")).toBe(false);

    dispose();
  });

  it("debounces resize callbacks", () => {
    const resize = vi.fn();
    let queued: (() => void) | null = null;
    const debounced = createDebouncedResize(resize, {
      debounceMs: 10,
      setTimeoutFn: (fn) => {
        queued = fn;
        return 1;
      },
      clearTimeoutFn: () => {
        queued = null;
      },
    });

    debounced.schedule();
    debounced.schedule();
    expect(resize).not.toHaveBeenCalled();
    queued?.();
    expect(resize).toHaveBeenCalledTimes(1);
  });
});

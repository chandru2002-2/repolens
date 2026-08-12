import type { GraphEdge, GraphNode } from "./api";

export type GraphViewMode = "architecture" | "package" | "class";

export type Selection =
  | { type: "node"; id: string }
  | { type: "edge"; id: string }
  | null;

export type GraphFilterState = {
  query: string;
  showPackages: boolean;
  showClasses: boolean;
  showInterfaces: boolean;
  showEnums: boolean;
  showMethods: boolean;
  showFields: boolean;
  showExternal: boolean;
  edgeDependsOn: boolean;
  edgeExtends: boolean;
  edgeImplements: boolean;
  edgeContains: boolean;
  internalOnly: boolean;
};

export const DEFAULT_GRAPH_FILTERS: GraphFilterState = {
  query: "",
  showPackages: false,
  showClasses: true,
  showInterfaces: true,
  showEnums: true,
  showMethods: false,
  showFields: false,
  showExternal: false,
  edgeDependsOn: true,
  edgeExtends: true,
  edgeImplements: true,
  edgeContains: false,
  internalOnly: false,
};

export const SYMBOL_KINDS = new Set([
  "class",
  "interface",
  "enum",
  "type",
  "function",
]);

export const MEMBER_KINDS = new Set(["method", "field"]);

export const KIND_META: Record<
  string,
  { title: string; icon: string; short: string }
> = {
  repository: { title: "Repository", icon: "#", short: "REPO" },
  module: { title: "Package / Module", icon: "+", short: "PKG" },
  class: { title: "Class", icon: "C", short: "CLASS" },
  interface: { title: "Interface", icon: "I", short: "IFACE" },
  enum: { title: "Enum", icon: "E", short: "ENUM" },
  type: { title: "Type", icon: "T", short: "TYPE" },
  function: { title: "Function", icon: "f", short: "FN" },
  method: { title: "Method", icon: "m", short: "METH" },
  field: { title: "Field", icon: ".", short: "FIELD" },
  external: { title: "External", icon: "x", short: "EXT" },
};

export function kindMeta(kind: string) {
  return (
    KIND_META[kind] ?? {
      title: kind.toUpperCase(),
      icon: "○",
      short: kind.toUpperCase(),
    }
  );
}

export function nodeById(
  nodes: GraphNode[],
  id: string | null | undefined,
): GraphNode | null {
  if (!id) {
    return null;
  }
  return nodes.find((node) => node.id === id) ?? null;
}

export function edgesForNode(edges: GraphEdge[], nodeId: string): GraphEdge[] {
  return edges.filter(
    (edge) => edge.fromNodeId === nodeId || edge.toNodeId === nodeId,
  );
}

export function neighborIds(edges: GraphEdge[], nodeId: string): Set<string> {
  const ids = new Set<string>([nodeId]);
  for (const edge of edges) {
    if (edge.fromNodeId === nodeId) {
      ids.add(edge.toNodeId);
    }
    if (edge.toNodeId === nodeId) {
      ids.add(edge.fromNodeId);
    }
  }
  return ids;
}

/** Base node kinds for each diagram mode (before user filters). */
export function kindsForView(view: GraphViewMode): Set<string> {
  switch (view) {
    case "architecture":
      return new Set(["repository", "module"]);
    case "package":
      return new Set(["module"]);
    case "class":
      return new Set(["module", "class", "interface", "enum", "type"]);
    default:
      return new Set(["repository", "module"]);
  }
}

export function allowedEdgeTypes(
  view: GraphViewMode,
  filters: GraphFilterState,
): Set<string> {
  if (view === "architecture") {
    const types = new Set<string>(["CONTAINS"]);
    if (filters.edgeDependsOn) {
      types.add("DEPENDS_ON");
    }
    return types;
  }
  if (view === "package") {
    const types = new Set<string>();
    if (filters.edgeDependsOn) {
      types.add("DEPENDS_ON");
    }
    if (filters.edgeContains) {
      types.add("CONTAINS");
    }
    return types;
  }
  const types = new Set<string>();
  if (filters.edgeDependsOn) {
    types.add("DEPENDS_ON");
  }
  if (filters.edgeExtends) {
    types.add("EXTENDS");
  }
  if (filters.edgeImplements) {
    types.add("IMPLEMENTS");
  }
  if (filters.edgeContains) {
    types.add("CONTAINS");
  }
  return types;
}

export function filterGraphForView(
  nodes: GraphNode[],
  edges: GraphEdge[],
  view: GraphViewMode,
  filters: GraphFilterState = DEFAULT_GRAPH_FILTERS,
): { nodes: GraphNode[]; edges: GraphEdge[] } {
  const baseKinds = kindsForView(view);
  const kindAllowed = new Set<string>();
  for (const kind of baseKinds) {
    if (kind === "module" && !filters.showPackages && view === "class") {
      continue;
    }
    if (kind === "class" && !filters.showClasses) {
      continue;
    }
    if (kind === "interface" && !filters.showInterfaces) {
      continue;
    }
    if (kind === "enum" && !filters.showEnums) {
      continue;
    }
    kindAllowed.add(kind);
  }
  if (view === "architecture") {
    kindAllowed.add("repository");
    kindAllowed.add("module");
  }
  if (view === "class") {
    if (filters.showMethods) {
      kindAllowed.add("method");
    }
    if (filters.showFields) {
      kindAllowed.add("field");
    }
    if (filters.showExternal) {
      kindAllowed.add("external");
    }
  }

  let visible = nodes.filter((node) => kindAllowed.has(node.kind));
  const q = filters.query.trim().toLowerCase();
  if (q) {
    const matched = new Set(
      visible
        .filter(
          (node) =>
            node.label.toLowerCase().includes(q) ||
            node.kind.toLowerCase().includes(q) ||
            (node.sourceEntityId ?? "").toLowerCase().includes(q),
        )
        .map((node) => node.id),
    );
    // Keep neighbors of matches so relationships remain visible.
    for (const edge of edges) {
      if (matched.has(edge.fromNodeId) || matched.has(edge.toNodeId)) {
        matched.add(edge.fromNodeId);
        matched.add(edge.toNodeId);
      }
    }
    visible = visible.filter((node) => matched.has(node.id));
  }

  const visibleIds = new Set(visible.map((node) => node.id));
  const edgeTypes = allowedEdgeTypes(view, filters);
  let visibleEdges = edges.filter(
    (edge) =>
      edgeTypes.has(edge.type) &&
      visibleIds.has(edge.fromNodeId) &&
      visibleIds.has(edge.toNodeId),
  );

  if (filters.internalOnly) {
    visibleEdges = visibleEdges.filter((edge) => {
      const from = nodeById(nodes, edge.fromNodeId);
      const to = nodeById(nodes, edge.toNodeId);
      return from?.kind !== "external" && to?.kind !== "external";
    });
  }

  // Drop isolate modules in class view when they have no visible type children / edges.
  if (view === "class" && !filters.showPackages) {
    visible = visible.filter((node) => node.kind !== "module");
    const ids = new Set(visible.map((node) => node.id));
    visibleEdges = visibleEdges.filter(
      (edge) => ids.has(edge.fromNodeId) && ids.has(edge.toNodeId),
    );
  }

  return { nodes: visible, edges: visibleEdges };
}

export type RelationGroup = {
  type: string;
  direction: "out" | "in";
  nodes: GraphNode[];
};

export function relationGroups(
  nodes: GraphNode[],
  edges: GraphEdge[],
  nodeId: string,
): RelationGroup[] {
  const byKey = new Map<string, RelationGroup>();
  for (const edge of edges) {
    if (edge.fromNodeId === nodeId) {
      const key = `${edge.type}:out`;
      const group =
        byKey.get(key) ??
        ({ type: edge.type, direction: "out", nodes: [] } as RelationGroup);
      const target = nodeById(nodes, edge.toNodeId);
      if (target && !group.nodes.some((n) => n.id === target.id)) {
        group.nodes.push(target);
      }
      byKey.set(key, group);
    }
    if (edge.toNodeId === nodeId) {
      const key = `${edge.type}:in`;
      const group =
        byKey.get(key) ??
        ({ type: edge.type, direction: "in", nodes: [] } as RelationGroup);
      const source = nodeById(nodes, edge.fromNodeId);
      if (source && !group.nodes.some((n) => n.id === source.id)) {
        group.nodes.push(source);
      }
      byKey.set(key, group);
    }
  }
  return [...byKey.values()].sort((a, b) =>
    `${a.type}${a.direction}`.localeCompare(`${b.type}${b.direction}`),
  );
}

/** Description derived only from graph edges — no invented semantics. */
export function structuredRole(
  node: GraphNode,
  nodes: GraphNode[],
  edges: GraphEdge[],
): string | null {
  const groups = relationGroups(nodes, edges, node.id);
  const dependsOn = groups.find(
    (g) => g.type === "DEPENDS_ON" && g.direction === "out",
  );
  const usedBy = groups.find(
    (g) => g.type === "DEPENDS_ON" && g.direction === "in",
  );
  const contains = groups.find(
    (g) => g.type === "CONTAINS" && g.direction === "out",
  );
  const containedIn = groups.find(
    (g) => g.type === "CONTAINS" && g.direction === "in",
  );
  const extendsOut = groups.find(
    (g) => g.type === "EXTENDS" && g.direction === "out",
  );
  const implementsOut = groups.find(
    (g) => g.type === "IMPLEMENTS" && g.direction === "out",
  );

  const parts: string[] = [];
  if (extendsOut && extendsOut.nodes.length > 0) {
    parts.push(`extends ${extendsOut.nodes.map((n) => n.label).join(", ")}`);
  }
  if (implementsOut && implementsOut.nodes.length > 0) {
    parts.push(`implements ${implementsOut.nodes.map((n) => n.label).join(", ")}`);
  }
  if (usedBy && usedBy.nodes.length > 0) {
    parts.push(
      `referenced by ${usedBy.nodes.map((n) => n.label).slice(0, 4).join(", ")}${
        usedBy.nodes.length > 4 ? ` (+${usedBy.nodes.length - 4})` : ""
      }`,
    );
  }
  if (dependsOn && dependsOn.nodes.length > 0) {
    parts.push(
      `depends on ${dependsOn.nodes.map((n) => n.label).slice(0, 4).join(", ")}${
        dependsOn.nodes.length > 4 ? ` (+${dependsOn.nodes.length - 4})` : ""
      }`,
    );
  }
  if (contains && contains.nodes.length > 0 && node.kind === "module") {
    parts.push(`contains ${contains.nodes.length} graph symbol(s)`);
  }
  if (containedIn && containedIn.nodes.length > 0 && SYMBOL_KINDS.has(node.kind)) {
    parts.push(`in ${containedIn.nodes.map((n) => n.label).join(", ")}`);
  }

  if (parts.length === 0) {
    return null;
  }
  return `${node.label} is ${parts.join(" and ")}.`;
}

export type SearchHit = {
  id: string;
  label: string;
  kind: string;
};

export function searchNodes(nodes: GraphNode[], query: string): SearchHit[] {
  const q = query.trim().toLowerCase();
  if (!q) {
    return [];
  }
  return nodes
    .filter(
      (node) =>
        node.label.toLowerCase().includes(q) ||
        node.kind.toLowerCase().includes(q) ||
        (node.sourceEntityId ?? "").toLowerCase().includes(q),
    )
    .slice(0, 40)
    .map((node) => ({ id: node.id, label: node.label, kind: node.kind }));
}

export type ExplorerItem = {
  id: string;
  label: string;
  kind: string;
  children: ExplorerItem[];
};

export function buildExplorerTree(
  nodes: GraphNode[],
  edges: GraphEdge[],
): ExplorerItem[] {
  const contains = edges.filter((edge) => edge.type === "CONTAINS");
  const childrenOf = new Map<string, string[]>();
  for (const edge of contains) {
    const list = childrenOf.get(edge.fromNodeId) ?? [];
    list.push(edge.toNodeId);
    childrenOf.set(edge.fromNodeId, list);
  }

  const build = (id: string, depth: number): ExplorerItem | null => {
    const node = nodeById(nodes, id);
    if (!node) {
      return null;
    }
    // Keep explorer readable: stop expanding at type symbols (members stay in inspector).
    const childIds =
      depth >= 2 || SYMBOL_KINDS.has(node.kind) || MEMBER_KINDS.has(node.kind)
        ? []
        : (childrenOf.get(id) ?? []).filter((childId) => {
            const child = nodeById(nodes, childId);
            return child && !MEMBER_KINDS.has(child.kind);
          });
    const children = childIds
      .map((childId) => build(childId, depth + 1))
      .filter((item): item is ExplorerItem => item !== null)
      .sort((a, b) => {
        const rank = (kind: string) =>
          kind === "module" ? 0 : SYMBOL_KINDS.has(kind) ? 1 : 2;
        return rank(a.kind) - rank(b.kind) || a.label.localeCompare(b.label);
      });
    return {
      id: node.id,
      label: node.label,
      kind: node.kind,
      children,
    };
  };

  const repos = nodes.filter((node) => node.kind === "repository");
  if (repos.length > 0) {
    return repos
      .map((repo) => build(repo.id, 0))
      .filter((item): item is ExplorerItem => item !== null);
  }

  const modules = nodes.filter((node) => node.kind === "module");
  return modules
    .map((module) => build(module.id, 1))
    .filter((item): item is ExplorerItem => item !== null)
    .sort((a, b) => a.label.localeCompare(b.label));
}

export function parentModuleNode(
  nodes: GraphNode[],
  edges: GraphEdge[],
  nodeId: string,
): GraphNode | null {
  for (const edge of edges) {
    if (edge.type === "CONTAINS" && edge.toNodeId === nodeId) {
      const parent = nodeById(nodes, edge.fromNodeId);
      if (parent?.kind === "module") {
        return parent;
      }
    }
  }
  return null;
}

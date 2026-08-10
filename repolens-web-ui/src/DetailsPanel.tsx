import type { GraphEdge, GraphNode } from "./api";
import {
  kindMeta,
  nodeById,
  relationGroups,
  structuredRole,
  type Selection,
} from "./graphModel";

type Props = {
  nodes: GraphNode[];
  edges: GraphEdge[];
  selection: Selection;
  onSelectNode: (id: string) => void;
  focused: boolean;
  onFocus: () => void;
  onResetFocus: () => void;
};

function relationHeading(type: string, direction: "in" | "out"): string {
  if (type === "DEPENDS_ON" && direction === "out") {
    return "Depends On";
  }
  if (type === "DEPENDS_ON" && direction === "in") {
    return "Used By";
  }
  if (type === "CONTAINS" && direction === "out") {
    return "Contains";
  }
  if (type === "CONTAINS" && direction === "in") {
    return "Package / Module";
  }
  if (type === "IMPORTS" && direction === "out") {
    return "Imports";
  }
  return direction === "out" ? `${type} →` : `← ${type}`;
}

export function DetailsPanel({
  nodes,
  edges,
  selection,
  onSelectNode,
  focused,
  onFocus,
  onResetFocus,
}: Props) {
  if (!selection) {
    return (
      <aside className="panel details-panel">
        <div className="panel-head">Inspector</div>
        <div className="panel-body">
          <p className="panel-hint">
            Select a node or relationship to inspect structured repository data.
          </p>
        </div>
      </aside>
    );
  }

  if (selection.type === "edge") {
    const edge = edges.find((item) => item.id === selection.id);
    if (!edge) {
      return (
        <aside className="panel details-panel">
          <div className="panel-head">Inspector</div>
          <div className="panel-body">
            <p className="panel-hint">Relationship not found in graph data.</p>
          </div>
        </aside>
      );
    }
    const source = nodeById(nodes, edge.fromNodeId);
    const target = nodeById(nodes, edge.toNodeId);
    return (
      <aside className="panel details-panel">
        <div className="panel-head">Inspector</div>
        <div className="panel-body">
          <p className="detail-kicker">Relationship</p>
          <div className="detail-header">
            <h2>{edge.type}</h2>
          </div>
          <dl className="detail-list">
            <div>
              <dt>Source</dt>
              <dd>
                {source ? (
                  <button type="button" className="linkish" onClick={() => onSelectNode(source.id)}>
                    {source.label}
                  </button>
                ) : (
                  edge.fromNodeId
                )}
              </dd>
            </div>
            <div>
              <dt>Target</dt>
              <dd>
                {target ? (
                  <button type="button" className="linkish" onClick={() => onSelectNode(target.id)}>
                    {target.label}
                  </button>
                ) : (
                  edge.toNodeId
                )}
              </dd>
            </div>
          </dl>
        </div>
      </aside>
    );
  }

  const node = nodeById(nodes, selection.id);
  if (!node) {
    return (
      <aside className="panel details-panel">
        <div className="panel-head">Inspector</div>
        <div className="panel-body">
          <p className="panel-hint">Node not found in graph data.</p>
        </div>
      </aside>
    );
  }

  const meta = kindMeta(node.kind);
  const groups = relationGroups(nodes, edges, node.id);
  const role = structuredRole(node, nodes, edges);
  const parentModules = groups
    .filter((g) => g.type === "CONTAINS" && g.direction === "in")
    .flatMap((g) => g.nodes);
  const contained = groups
    .filter((g) => g.type === "CONTAINS" && g.direction === "out")
    .flatMap((g) => g.nodes);

  return (
    <aside className="panel details-panel">
      <div className="panel-head">Inspector</div>
      <div className="panel-body">
        <div className="detail-header">
          <p className="detail-kicker">{meta.short}</p>
          <h2>{node.label}</h2>
          <p className="selection-meta">{meta.title}</p>
        </div>

        <div className="focus-actions">
          <button
            type="button"
            className={focused ? "tool-button active" : "tool-button"}
            onClick={onFocus}
          >
            Focus
          </button>
          <button type="button" className="tool-button" onClick={onResetFocus}>
            Reset
          </button>
        </div>

        {role ? (
          <section className="panel-section">
            <h3>From graph</h3>
            <p className="role-text">{role}</p>
          </section>
        ) : null}

        <dl className="detail-list">
          <div>
            <dt>Type</dt>
            <dd>{meta.title}</dd>
          </div>
          {parentModules.length > 0 ? (
            <div>
              <dt>Package / Module</dt>
              <dd>
                {parentModules.map((parent) => (
                  <button
                    key={parent.id}
                    type="button"
                    className="linkish"
                    onClick={() => onSelectNode(parent.id)}
                  >
                    {parent.label}
                  </button>
                ))}
              </dd>
            </div>
          ) : null}
          {node.sourceEntityId ? (
            <div>
              <dt>Entity id</dt>
              <dd className="mono">{node.sourceEntityId}</dd>
            </div>
          ) : null}
        </dl>

        {contained.length > 0 ? (
          <section className="panel-section">
            <h3>Contained symbols</h3>
            <ul className="chip-list">
              {contained.map((child) => (
                <li key={child.id}>
                  <button type="button" className="chip" onClick={() => onSelectNode(child.id)}>
                    <span className="chip-kind">{kindMeta(child.kind).short}</span>
                    {child.label}
                  </button>
                </li>
              ))}
            </ul>
          </section>
        ) : null}

        {groups
          .filter((g) => g.type !== "CONTAINS")
          .map((group) => (
            <section className="panel-section" key={`${group.type}-${group.direction}`}>
              <h3>{relationHeading(group.type, group.direction)}</h3>
              <ul className="rel-list">
                {group.nodes.map((related) => (
                  <li key={related.id}>
                    <span className="rel-arrow" aria-hidden="true">
                      {group.direction === "out" ? "->" : "<-"}
                    </span>
                    <button type="button" className="linkish" onClick={() => onSelectNode(related.id)}>
                      {related.label}
                    </button>
                    <span className="rel-kind">{kindMeta(related.kind).short}</span>
                  </li>
                ))}
              </ul>
            </section>
          ))}

        {groups.length === 0 ? (
          <p className="panel-hint">No graph relationships for this node.</p>
        ) : null}

        <p className="panel-footnote">
          File paths and methods appear only when present in API graph data.
        </p>
      </div>
    </aside>
  );
}

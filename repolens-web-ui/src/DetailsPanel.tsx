import type { ReactNode } from "react";
import type { AnalysisResponse, GraphEdge, GraphNode } from "./api";
import { docsForEntity, symbolDetailForNode } from "./api";
import {
  kindMeta,
  nodeById,
  parentModuleNode,
  relationGroups,
  structuredRole,
  type GraphViewMode,
  type Selection,
} from "./graphModel";
import { PanelCollapseToggle } from "./PanelCollapseToggle";

type Props = {
  result: AnalysisResponse;
  nodes: GraphNode[];
  edges: GraphEdge[];
  selection: Selection;
  onSelectNode: (id: string) => void;
  onChangeView: (view: GraphViewMode) => void;
  focused: boolean;
  onFocus: () => void;
  onResetFocus: () => void;
  collapsed: boolean;
  onToggleCollapsed: () => void;
};

function relationHeading(type: string, direction: "in" | "out"): string {
  if (type === "DEPENDS_ON" && direction === "out") {
    return "Depends On";
  }
  if (type === "DEPENDS_ON" && direction === "in") {
    return "Used By";
  }
  if (type === "EXTENDS" && direction === "out") {
    return "Extends";
  }
  if (type === "EXTENDS" && direction === "in") {
    return "Extended By";
  }
  if (type === "IMPLEMENTS" && direction === "out") {
    return "Implements";
  }
  if (type === "IMPLEMENTS" && direction === "in") {
    return "Implemented By";
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

function InspectorShell({
  collapsed,
  onToggleCollapsed,
  children,
}: {
  collapsed: boolean;
  onToggleCollapsed: () => void;
  children: ReactNode;
}) {
  return (
    <aside
      className={collapsed ? "panel details-panel collapsed" : "panel details-panel"}
      aria-label="Inspector"
    >
      <div className="panel-head">
        <span className="panel-head-title">Inspector</span>
        <PanelCollapseToggle
          collapsed={collapsed}
          side="end"
          labelExpand="Expand inspector"
          labelCollapse="Collapse inspector"
          controlsId="inspector-body"
          onToggle={onToggleCollapsed}
        />
      </div>
      <div
        id="inspector-body"
        className="panel-body"
        hidden={collapsed}
        aria-hidden={collapsed}
      >
        {children}
      </div>
    </aside>
  );
}

export function DetailsPanel({
  result,
  nodes,
  edges,
  selection,
  onSelectNode,
  onChangeView,
  focused,
  onFocus,
  onResetFocus,
  collapsed,
  onToggleCollapsed,
}: Props) {
  if (!selection) {
    return (
      <InspectorShell collapsed={collapsed} onToggleCollapsed={onToggleCollapsed}>
        <p className="panel-hint">
          Select a node or relationship to inspect structured repository data.
        </p>
      </InspectorShell>
    );
  }

  if (selection.type === "edge") {
    const edge = edges.find((item) => item.id === selection.id);
    if (!edge) {
      return (
        <InspectorShell collapsed={collapsed} onToggleCollapsed={onToggleCollapsed}>
          <p className="panel-hint">Relationship not found in graph data.</p>
        </InspectorShell>
      );
    }
    const source = nodeById(nodes, edge.fromNodeId);
    const target = nodeById(nodes, edge.toNodeId);
    return (
      <InspectorShell collapsed={collapsed} onToggleCollapsed={onToggleCollapsed}>
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
      </InspectorShell>
    );
  }

  const node = nodeById(nodes, selection.id);
  if (!node) {
    return (
      <InspectorShell collapsed={collapsed} onToggleCollapsed={onToggleCollapsed}>
        <p className="panel-hint">Node not found in graph data.</p>
      </InspectorShell>
    );
  }

  const meta = kindMeta(node.kind);
  const groups = relationGroups(nodes, edges, node.id);
  const role = structuredRole(node, nodes, edges);
  const detail = symbolDetailForNode(result, node);
  const docs = docsForEntity(result, node.sourceEntityId);
  const moduleNode = parentModuleNode(nodes, edges, node.id);
  const parentModules = groups
    .filter((g) => g.type === "CONTAINS" && g.direction === "in")
    .flatMap((g) => g.nodes)
    .filter((item) => item.kind === "module");

  return (
    <InspectorShell collapsed={collapsed} onToggleCollapsed={onToggleCollapsed}>
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

      <div className="inspector-nav">
        <button type="button" className="tool-button" onClick={() => onChangeView("architecture")}>
          Architecture
        </button>
        {(moduleNode || parentModules[0]) && (
          <button
            type="button"
            className="tool-button"
            onClick={() => {
              onChangeView("package");
              onSelectNode((moduleNode ?? parentModules[0]).id);
            }}
          >
            View package
          </button>
        )}
        {["class", "interface", "enum", "type"].includes(node.kind) ? (
          <button type="button" className="tool-button" onClick={() => onChangeView("class")}>
            Class diagram
          </button>
        ) : null}
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
        {detail?.moduleName || parentModules.length > 0 ? (
          <div>
            <dt>Package</dt>
            <dd>
              {detail?.moduleName ? (
                <span>{detail.moduleName}</span>
              ) : (
                parentModules.map((parent) => (
                  <button
                    key={parent.id}
                    type="button"
                    className="linkish"
                    onClick={() => onSelectNode(parent.id)}
                  >
                    {parent.label}
                  </button>
                ))
              )}
            </dd>
          </div>
        ) : null}
        {detail?.filePath ? (
          <div>
            <dt>Path</dt>
            <dd className="mono">{detail.filePath}</dd>
          </div>
        ) : null}
        {node.sourceEntityId ? (
          <div>
            <dt>Entity id</dt>
            <dd className="mono">{node.sourceEntityId}</dd>
          </div>
        ) : null}
      </dl>

      {detail && detail.fieldNames.length > 0 ? (
        <section className="panel-section">
          <h3>Fields</h3>
          <ul className="member-list">
            {detail.fieldNames.map((name) => (
              <li key={name}>{name}</li>
            ))}
          </ul>
        </section>
      ) : null}

      {detail && detail.methodNames.length > 0 ? (
        <section className="panel-section">
          <h3>Methods</h3>
          <ul className="member-list">
            {detail.methodNames.map((name) => (
              <li key={name}>{name}()</li>
            ))}
          </ul>
        </section>
      ) : null}

      {groups
        .filter((g) => g.type !== "CONTAINS" || node.kind === "module")
        .map((group) => (
          <section className="panel-section" key={`${group.type}-${group.direction}`}>
            <h3>{relationHeading(group.type, group.direction)}</h3>
            <ul className="rel-list">
              {group.nodes
                .filter((related) => related.kind !== "method" && related.kind !== "field")
                .map((related) => (
                  <li key={related.id}>
                    <span className="rel-arrow" aria-hidden="true">
                      {group.direction === "out" ? "->" : "<-"}
                    </span>{" "}
                    <button type="button" className="linkish" onClick={() => onSelectNode(related.id)}>
                      {related.label}
                    </button>{" "}
                    <span className="rel-kind">{kindMeta(related.kind).short}</span>
                  </li>
                ))}
            </ul>
          </section>
        ))}

      {docs.length > 0 ? (
        <section className="panel-section">
          <h3>Documentation</h3>
          {docs.slice(0, 4).map((doc, index) => (
            <div className="doc-card" key={`${doc.path}-${doc.heading}-${index}`}>
              <p className="doc-excerpt">{excerpt(doc.text)}</p>
              <p className="doc-meta">
                Source: <span className="mono">{doc.path}</span>
              </p>
              <p className="doc-meta">Relevant section: {doc.heading}</p>
            </div>
          ))}
          {docs.length > 1 ? (
            <p className="panel-hint">
              Related docs: {[...new Set(docs.map((item) => item.path))].join(", ")}
            </p>
          ) : null}
        </section>
      ) : null}

      {groups.length === 0 && docs.length === 0 ? (
        <p className="panel-hint">No graph relationships for this node.</p>
      ) : null}

      <p className="panel-footnote">
        Documentation is supplemental context matched deterministically from README/docs.
      </p>
    </InspectorShell>
  );
}

function excerpt(text: string): string {
  const cleaned = text.replace(/\s+/g, " ").trim();
  if (cleaned.length <= 220) {
    return cleaned;
  }
  return `${cleaned.slice(0, 220).trim()}…`;
}

import { useState } from "react";
import type { GraphEdge, GraphNode } from "./api";
import {
  buildExplorerTree,
  kindMeta,
  type ExplorerItem,
} from "./graphModel";

type Props = {
  nodes: GraphNode[];
  edges: GraphEdge[];
  selectedId: string | null;
  onSelectNode: (id: string) => void;
  repoName: string;
  repoSource: string;
  skipWarning?: string | null;
};

function TreeNode({
  item,
  depth,
  selectedId,
  onSelectNode,
}: {
  item: ExplorerItem;
  depth: number;
  selectedId: string | null;
  onSelectNode: (id: string) => void;
}) {
  const [open, setOpen] = useState(depth < 2);
  const meta = kindMeta(item.kind);
  const hasChildren = item.children.length > 0;

  return (
    <li>
      <div
        className={selectedId === item.id ? "tree-row selected" : "tree-row"}
        style={{ paddingLeft: `${0.15 + depth * 0.75}rem` }}
      >
        {hasChildren ? (
          <button
            type="button"
            className="tree-twist"
            aria-label={open ? "Collapse" : "Expand"}
            onClick={() => setOpen((value) => !value)}
          >
            {open ? "v" : ">"}
          </button>
        ) : (
          <span className="tree-twist spacer" />
        )}
        <button type="button" className="tree-label" onClick={() => onSelectNode(item.id)}>
          <span className="tree-icon" aria-hidden="true">
            {meta.icon}
          </span>
          <span>{item.label}</span>
          <span className="tree-kind">{meta.short}</span>
        </button>
      </div>
      {hasChildren && open ? (
        <ul className="tree-children">
          {item.children.map((child) => (
            <TreeNode
              key={child.id}
              item={child}
              depth={depth + 1}
              selectedId={selectedId}
              onSelectNode={onSelectNode}
            />
          ))}
        </ul>
      ) : null}
    </li>
  );
}

export function ExplorerPanel({
  nodes,
  edges,
  selectedId,
  onSelectNode,
  repoName,
  repoSource,
  skipWarning = null,
}: Props) {
  const tree = buildExplorerTree(nodes, edges);

  return (
    <aside className="panel explorer-panel">
      <div className="panel-head">Repository</div>
      <div className="panel-body">
        <p className="panel-source">{repoName}</p>
        <p className="panel-source">{repoSource}</p>
        {skipWarning ? (
          <p className="panel-warning" role="status">
            ⚠ {compactSkipWarning(skipWarning)}
          </p>
        ) : null}
        <div className="panel-section tree-section" style={{ borderTop: "1px solid var(--line)", marginTop: "0.55rem", paddingTop: "0.45rem" }}>
          <h3>Tree</h3>
          {tree.length === 0 ? (
            <p className="panel-hint">No hierarchical CONTAINS edges in graph.</p>
          ) : (
            <ul className="tree-root">
              {tree.map((item) => (
                <TreeNode
                  key={item.id}
                  item={item}
                  depth={0}
                  selectedId={selectedId}
                  onSelectNode={onSelectNode}
                />
              ))}
            </ul>
          )}
        </div>
      </div>
    </aside>
  );
}

function compactSkipWarning(message: string): string {
  const match = message.match(/Skipped (\d+) files? exceeding the ([^.]+)\./i);
  if (match) {
    const count = match[1];
    const limit = match[2].replace(/\s+file-size limit$/i, "").trim();
    return `${count} file${count === "1" ? "" : "s"} skipped · exceeds ${limit} limit`;
  }
  return message;
}

import { describe, expect, it } from "vitest";
import { docsForEntity, oversizedSkipWarning, type AnalysisResponse } from "./api";
import {
  DEFAULT_GRAPH_FILTERS,
  filterGraphForView,
  type GraphFilterState,
} from "./graphModel";

const sample: AnalysisResponse = {
  schemaVersion: "v1",
  repository: { id: "r1", name: "demo", origin: "LOCAL", source: "/tmp/demo" },
  modelStats: {
    fileCount: 2,
    moduleCount: 1,
    symbolCount: 2,
    importCount: 0,
    relationshipCount: 1,
  },
  results: [
    {
      analyzerId: "ingest",
      summary: "Skipped 1 file exceeding the 5 MB file-size limit.",
      metrics: [],
      findings: [
        {
          id: "ingest-oversized-files",
          severity: "warning",
          message: "Skipped 1 file exceeding the 5 MB file-size limit.",
          subjectId: null,
        },
      ],
    },
  ],
  graph: {
    id: "g1",
    nodes: [
      { id: "n-repo", label: "demo", kind: "repository", sourceEntityId: "r1" },
      { id: "n-pkg", label: "com.example", kind: "module", sourceEntityId: "module:com.example" },
      { id: "n-class", label: "UserService", kind: "class", sourceEntityId: "sym:UserService" },
      { id: "n-iface", label: "Job", kind: "interface", sourceEntityId: "sym:Job" },
      { id: "n-method", label: "createUser", kind: "method", sourceEntityId: "sym:m1" },
    ],
    edges: [
      { id: "e1", fromNodeId: "n-repo", toNodeId: "n-pkg", type: "CONTAINS" },
      { id: "e2", fromNodeId: "n-pkg", toNodeId: "n-class", type: "CONTAINS" },
      { id: "e3", fromNodeId: "n-class", toNodeId: "n-iface", type: "IMPLEMENTS" },
      { id: "e4", fromNodeId: "n-class", toNodeId: "n-method", type: "CONTAINS" },
    ],
  },
  documentation: [
    {
      id: "doc:1",
      path: "README.md",
      title: "Demo",
      sections: [
        {
          id: "doc:1:section:1",
          heading: "Authentication",
          text: "`UserService` handles user-related business logic.",
          startLine: 1,
        },
      ],
      references: [
        {
          id: "docref:1",
          sectionId: "doc:1:section:1",
          entityId: "sym:UserService",
          matchedText: "UserService",
        },
      ],
    },
  ],
  symbols: [],
};

describe("diagram selector filters", () => {
  it("renders package diagram nodes without classes by default", () => {
    const filtered = filterGraphForView(
      sample.graph.nodes,
      sample.graph.edges,
      "package",
      DEFAULT_GRAPH_FILTERS,
    );
    expect(filtered.nodes.every((node) => node.kind === "module")).toBe(true);
  });

  it("renders class diagram with inheritance edges and hides methods by default", () => {
    const filtered = filterGraphForView(
      sample.graph.nodes,
      sample.graph.edges,
      "class",
      DEFAULT_GRAPH_FILTERS,
    );
    expect(filtered.nodes.some((node) => node.kind === "class")).toBe(true);
    expect(filtered.nodes.some((node) => node.kind === "method")).toBe(false);
    expect(filtered.edges.some((edge) => edge.type === "IMPLEMENTS")).toBe(true);
  });

  it("applies kind and search filters without re-analysis", () => {
    const filters: GraphFilterState = {
      ...DEFAULT_GRAPH_FILTERS,
      showInterfaces: false,
      query: "User",
    };
    const filtered = filterGraphForView(
      sample.graph.nodes,
      sample.graph.edges,
      "class",
      filters,
    );
    expect(filtered.nodes.some((node) => node.label === "UserService")).toBe(true);
    expect(filtered.nodes.some((node) => node.kind === "interface")).toBe(false);
  });
});

describe("inspector documentation", () => {
  it("surfaces matched README documentation for an entity", () => {
    const docs = docsForEntity(sample, "sym:UserService");
    expect(docs).toHaveLength(1);
    expect(docs[0]?.path).toBe("README.md");
    expect(docs[0]?.heading).toBe("Authentication");
  });

  it("keeps oversized-file warnings available", () => {
    expect(oversizedSkipWarning(sample)).toContain("5 MB");
  });
});

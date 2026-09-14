import { describe, expect, it } from "vitest";
import type { AnalysisResponse } from "./api";
import { analysisEndpoints, analysisTests, analysisTraces } from "./api";
import {
  EMPTY_ENDPOINTS,
  EMPTY_TESTS,
  EMPTY_TRACES,
  formatLocation,
  intelligenceLists,
  testSubjectsFromGraph,
  traceKindLabel,
} from "./intelligence";

const base: AnalysisResponse = {
  schemaVersion: "v1",
  repository: { id: "r1", name: "demo", origin: "LOCAL", source: "/tmp/demo" },
  modelStats: {
    fileCount: 1,
    moduleCount: 0,
    symbolCount: 0,
    importCount: 0,
    relationshipCount: 0,
  },
  results: [],
  graph: { id: "g1", nodes: [], edges: [] },
};

describe("intelligence API fields", () => {
  it("treats missing collections as empty rather than an error", () => {
    expect(analysisEndpoints(base)).toEqual([]);
    expect(analysisTests(base)).toEqual([]);
    expect(analysisTraces(base)).toEqual([]);
    const lists = intelligenceLists(base);
    expect(lists.endpoints).toEqual([]);
    expect(lists.tests).toEqual([]);
    expect(lists.traces).toEqual([]);
    expect(EMPTY_ENDPOINTS).toContain("No endpoints");
    expect(EMPTY_TESTS).toContain("No tests");
    expect(EMPTY_TRACES).toContain("not runtime");
  });

  it("formats source location from API location fields", () => {
    expect(formatLocation({ filePath: "UserController.java", startLine: 12 })).toBe(
      "UserController.java:12",
    );
    expect(formatLocation({ filePath: "UserController.java", startLine: 0 })).toBe(
      "UserController.java",
    );
  });

  it("shows TESTS subjects only when the graph already has TESTS edges", () => {
    const result: AnalysisResponse = {
      ...base,
      tests: [
        {
          id: "test:1",
          symbolId: "sym:testMethod",
          frameworkHint: "junit5",
          location: {
            filePath: "UserServiceTest.java",
            startLine: 8,
            startColumn: 0,
            endLine: 8,
            endColumn: 0,
          },
          evidence: { inferenceMethod: "ANNOTATION" },
        },
      ],
      graph: {
        id: "g1",
        nodes: [
          { id: "n-test", label: "loads", kind: "method", sourceEntityId: "sym:testMethod" },
          { id: "n-svc", label: "UserService", kind: "class", sourceEntityId: "sym:service" },
        ],
        edges: [
          { id: "e-tests", fromNodeId: "n-test", toNodeId: "n-svc", type: "TESTS" },
        ],
      },
    };
    const subjects = testSubjectsFromGraph(result, result.tests![0]);
    expect(subjects.map((node) => node.label)).toEqual(["UserService"]);
    expect(testSubjectsFromGraph(base, result.tests![0])).toEqual([]);
  });

  it("labels traces as static and not runtime", () => {
    expect(
      traceKindLabel({
        id: "t1",
        endpointId: "ep-1",
        hops: [],
        confidence: 0.9,
        unresolved: false,
        inferenceKind: "static",
      }),
    ).toBe("static · not runtime");
  });
});

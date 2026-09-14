import type {
  AnalysisResponse,
  EndpointRecord,
  GraphNode,
  TestRecord,
  TraceRecord,
} from "./api";
import { analysisEndpoints, analysisTests, analysisTraces } from "./api";

export const INTELLIGENCE_TABS = [
  { id: "endpoints", label: "Endpoints" },
  { id: "tests", label: "Tests" },
  { id: "traces", label: "Traces" },
] as const;

export type IntelligenceTab = (typeof INTELLIGENCE_TABS)[number]["id"];

export const EMPTY_ENDPOINTS =
  "No endpoints in this analysis result. Endpoints appear only when the API returns endpoint facts.";
export const EMPTY_TESTS =
  "No tests in this analysis result. Tests appear only when the API returns test facts.";
export const EMPTY_TRACES =
  "No traces in this analysis result. Traces are static paths from the API, not runtime execution.";

export function formatLocation(
  location: { filePath: string; startLine: number } | null | undefined,
): string | null {
  if (!location?.filePath) {
    return null;
  }
  if (!location.startLine) {
    return location.filePath;
  }
  return `${location.filePath}:${location.startLine}`;
}

export function formatEvidence(evidence: { inferenceMethod: string; summary?: string | null } | null | undefined): string {
  if (!evidence?.inferenceMethod) {
    return "";
  }
  return evidence.summary
    ? `${evidence.inferenceMethod} · ${evidence.summary}`
    : evidence.inferenceMethod;
}

export function labelForEntity(nodes: GraphNode[], entityId: string | null | undefined): string | null {
  if (!entityId) {
    return null;
  }
  const node = nodes.find((item) => item.sourceEntityId === entityId || item.id === entityId);
  return node?.label ?? null;
}

/** Subject links already present on the analysis graph (TESTS edges). Does not infer new links. */
export function testSubjectsFromGraph(result: AnalysisResponse, test: TestRecord): GraphNode[] {
  const nodes = result.graph.nodes;
  const fromIds = new Set(
    nodes
      .filter((node) => node.sourceEntityId === test.symbolId || node.id === test.symbolId)
      .map((node) => node.id),
  );
  if (fromIds.size === 0) {
    return [];
  }
  const subjects: GraphNode[] = [];
  for (const edge of result.graph.edges) {
    if (edge.type !== "TESTS" || !fromIds.has(edge.fromNodeId)) {
      continue;
    }
    const target = nodes.find((node) => node.id === edge.toNodeId);
    if (target) {
      subjects.push(target);
    }
  }
  return subjects;
}

export function intelligenceLists(result: AnalysisResponse): {
  endpoints: EndpointRecord[];
  tests: TestRecord[];
  traces: TraceRecord[];
} {
  return {
    endpoints: analysisEndpoints(result),
    tests: analysisTests(result),
    traces: analysisTraces(result),
  };
}

export function traceKindLabel(trace: TraceRecord): string {
  const kind = trace.inferenceKind || "static";
  return `${kind} · not runtime`;
}

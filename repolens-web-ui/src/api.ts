export type GraphNode = {
  id: string;
  label: string;
  kind: string;
  sourceEntityId: string | null;
};

export type GraphEdge = {
  id: string;
  fromNodeId: string;
  toNodeId: string;
  type: string;
};

export type AnalysisResponse = {
  schemaVersion: string;
  repository: {
    id: string;
    name: string;
    origin: string;
    source: string;
  };
  modelStats: {
    fileCount: number;
    moduleCount: number;
    symbolCount: number;
    importCount: number;
    relationshipCount: number;
  };
  results: Array<{
    analyzerId: string;
    summary: string;
    metrics: Array<{ name: string; value: number; unit: string | null; scopeId: string | null }>;
    findings: Array<{ id: string; severity: string; message: string; subjectId: string | null }>;
  }>;
  graph: {
    id: string;
    nodes: GraphNode[];
    edges: GraphEdge[];
  };
};

export type JobStatus = {
  id: string;
  status: "QUEUED" | "RUNNING" | "COMPLETED" | "FAILED";
  source: string;
  remote: boolean;
  error: string | null;
};

function looksRemote(source: string): boolean {
  const value = source.trim().toLowerCase();
  return value.startsWith("https://") || value.startsWith("git@");
}

export async function startAnalysis(source: string): Promise<JobStatus> {
  const response = await fetch("/v1/analyze", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ source: source.trim(), remote: looksRemote(source) }),
  });
  const body = await response.json();
  if (!response.ok) {
    throw new Error(body.error ?? "Failed to start analysis");
  }
  return body as JobStatus;
}

export async function getJob(id: string): Promise<JobStatus> {
  const response = await fetch(`/v1/jobs/${id}`);
  const body = await response.json();
  if (!response.ok) {
    throw new Error(body.error ?? "Failed to load job");
  }
  return body as JobStatus;
}

export async function getResult(id: string): Promise<AnalysisResponse> {
  const response = await fetch(`/v1/jobs/${id}/result`);
  const body = await response.json();
  if (!response.ok) {
    throw new Error(body.error ?? body.message ?? "Result not ready");
  }
  return body as AnalysisResponse;
}

export async function waitForResult(
  id: string,
  onStatus?: (status: JobStatus) => void,
): Promise<AnalysisResponse> {
  for (;;) {
    const status = await getJob(id);
    onStatus?.(status);
    if (status.status === "COMPLETED") {
      return getResult(id);
    }
    if (status.status === "FAILED") {
      throw new Error(status.error ?? "Analysis failed");
    }
    await new Promise((resolve) => setTimeout(resolve, 400));
  }
}

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

export type DocumentationSection = {
  id: string;
  heading: string;
  text: string;
  startLine: number;
};

export type DocumentationRef = {
  id: string;
  sectionId: string;
  entityId: string;
  matchedText: string;
};

export type DocumentationEntry = {
  id: string;
  path: string;
  title: string;
  sections: DocumentationSection[];
  references: DocumentationRef[];
};

export type SymbolDetail = {
  id: string;
  name: string;
  kind: string;
  moduleId: string | null;
  moduleName: string | null;
  filePath: string | null;
  parentSymbolId: string | null;
  fieldNames: string[];
  methodNames: string[];
};

export type RepositoryMetadata = {
  name?: string | null;
  owner?: string | null;
  sizeBytes?: number | null;
  createdAt?: string | null;
  firstCommitAt?: string | null;
  defaultBranch?: string | null;
  lastCommit?: {
    sha?: string | null;
    message?: string | null;
    author?: string | null;
    authoredAt?: string | null;
    committedAt?: string | null;
  } | null;
  commitCount?: number | null;
};

export type DiagramView = {
  type: string;
  title: string;
  graph: {
    id: string;
    nodes: GraphNode[];
    edges: GraphEdge[];
  };
  emptyMessage?: string | null;
  totalNodeCount?: number;
  truncated?: boolean;
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
  documentation?: DocumentationEntry[];
  symbols?: SymbolDetail[];
  metadata?: RepositoryMetadata | null;
  diagrams?: DiagramView[];
};

export function oversizedSkipWarning(result: AnalysisResponse): string | null {
  const ingest = result.results.find((item) => item.analyzerId === "ingest");
  const warning = ingest?.findings.find((finding) => finding.severity === "warning");
  return warning?.message ?? ingest?.summary ?? null;
}

export function metadataWarning(result: AnalysisResponse): string | null {
  const meta = result.results.find((item) => item.analyzerId === "metadata");
  const warning = meta?.findings.find((finding) => finding.severity === "warning");
  return warning?.message ?? null;
}

export function docsForEntity(
  result: AnalysisResponse,
  entityId: string | null | undefined,
): Array<{
  path: string;
  heading: string;
  text: string;
  matchedText: string;
}> {
  if (!entityId || !result.documentation) {
    return [];
  }
  const hits: Array<{ path: string; heading: string; text: string; matchedText: string }> = [];
  for (const doc of result.documentation) {
    for (const ref of doc.references) {
      if (ref.entityId !== entityId) {
        continue;
      }
      const section = doc.sections.find((item) => item.id === ref.sectionId);
      if (!section) {
        continue;
      }
      hits.push({
        path: doc.path,
        heading: section.heading,
        text: section.text,
        matchedText: ref.matchedText,
      });
    }
  }
  return hits;
}

export function symbolDetailForNode(
  result: AnalysisResponse,
  node: GraphNode | null,
): SymbolDetail | null {
  if (!node?.sourceEntityId || !result.symbols) {
    return null;
  }
  return result.symbols.find((symbol) => symbol.id === node.sourceEntityId) ?? null;
}

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
    throw new Error(body.error ?? "Failed to load result");
  }
  return body as AnalysisResponse;
}

export async function waitForResult(
  id: string,
  onStatus?: (status: JobStatus) => void,
): Promise<AnalysisResponse> {
  for (;;) {
    const job = await getJob(id);
    onStatus?.(job);
    if (job.status === "COMPLETED") {
      return getResult(id);
    }
    if (job.status === "FAILED") {
      throw new Error(job.error ?? "Analysis failed");
    }
    await new Promise((resolve) => setTimeout(resolve, 400));
  }
}

export type ContextPurpose =
  | "FULL_REPOSITORY"
  | "ARCHITECTURE"
  | "SELECTED_FILES"
  | "SELECTED_SYMBOLS"
  | "API_BACKEND"
  | "DATABASE_JPA"
  | "SECURITY"
  | "CUSTOM";

export type AiTaskPreset =
  | "EXPLAIN_ARCHITECTURE"
  | "EXPLAIN_AUTHENTICATION_SECURITY"
  | "EXPLAIN_SELECTED_CLASS_SYMBOL"
  | "TRACE_API_REQUEST"
  | "EXPLAIN_DATABASE_JPA"
  | "EXPLAIN_DEPENDENCIES"
  | "HELP_DEBUG_SELECTED_CODE"
  | "IDENTIFY_ARCHITECTURAL_PROBLEMS"
  | "GENERATE_ONBOARDING_GUIDANCE"
  | "GENERATE_DOCUMENTATION"
  | "CUSTOM";

export type ContextStrategy =
  | "ARCHITECTURE_OVERVIEW"
  | "SOURCE_AND_SYMBOLS"
  | "COMPACT_ARCHITECTURE"
  | "ALTERNATE_PRIORITIZATION";

export type ContextRequestBody = {
  purpose: ContextPurpose;
  scope: {
    mode: string;
    filePaths: string[];
    symbolIds: string[];
    graphNodeIds: string[];
  };
  tokenBudget: number;
  format: "markdown" | "json";
  title?: string;
  aiTask?: AiTaskPreset;
  customTask?: string;
  strategy?: ContextStrategy;
};

export type ContextResponse = {
  id: string;
  title: string;
  purpose: string;
  scopeMode: string;
  format: string;
  tokenBudget: number;
  estimatedTokens: number;
  tokenEstimateApproximate: boolean;
  content: string;
  included: string[];
  excluded: string[];
  scope: {
    filePaths: string[];
    symbolIds: string[];
    graphNodeIds: string[];
  };
  aiTask?: string | null;
  taskText?: string | null;
  prompt?: string | null;
  strategy?: string | null;
  strategyLabel?: string | null;
};

export async function generateContext(
  jobId: string,
  body: ContextRequestBody,
): Promise<ContextResponse> {
  const response = await fetch(`/v1/jobs/${jobId}/context`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body),
  });
  const payload = await response.json();
  if (!response.ok) {
    throw new Error(payload.error ?? "Failed to generate context");
  }
  return payload as ContextResponse;
}

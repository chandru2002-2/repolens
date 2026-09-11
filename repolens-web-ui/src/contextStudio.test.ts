import { describe, expect, it } from "vitest";
import type { AnalysisResponse, ContextResponse } from "./api";
import {
  AI_TASK_OPTIONS,
  appendHistory,
  buildContextScope,
  copyTarget,
  defaultStudioFormState,
  downloadName,
  previewText,
  resolveTokenBudget,
  suggestedPurposeForAiTask,
  validateAiTask,
  validateTokenBudget,
  type ContextHistoryEntry,
} from "./ContextStudio";

const sampleResult: AnalysisResponse = {
  schemaVersion: "v1",
  repository: { id: "r1", name: "demo", origin: "LOCAL", source: "/tmp/demo" },
  modelStats: { fileCount: 1, moduleCount: 1, symbolCount: 1, importCount: 0, relationshipCount: 0 },
  results: [],
  graph: {
    id: "g1",
    nodes: [{ id: "node:sym:c", label: "UserController", kind: "class", sourceEntityId: "sym:c" }],
    edges: [],
  },
  symbols: [
    {
      id: "sym:c",
      name: "UserController",
      kind: "CLASS",
      moduleId: null,
      moduleName: null,
      filePath: "UserController.java",
      parentSymbolId: null,
      fieldNames: [],
      methodNames: ["run"],
    },
  ],
};

function historyEntry(overrides: Partial<ContextResponse> = {}): ContextHistoryEntry {
  return {
    id: "ctx-1",
    title: "Architecture context",
    purpose: "ARCHITECTURE",
    scopeMode: "ENTIRE_REPOSITORY",
    format: "markdown",
    tokenBudget: 8000,
    estimatedTokens: 1200,
    tokenEstimateApproximate: true,
    content: "# Repository Context\n",
    included: ["repository overview"],
    excluded: [],
    scope: { filePaths: [], symbolIds: [], graphNodeIds: [] },
    aiTask: "EXPLAIN_ARCHITECTURE",
    taskText: "Explain the architecture of this repository using only the provided context.",
    prompt: "TASK:\nExplain the architecture\n\nREPOSITORY CONTEXT:\n# Repository Context\n",
    createdAt: 1,
    ...overrides,
  };
}

describe("buildContextScope", () => {
  it("uses entire repository by default", () => {
    const scope = buildContextScope("ARCHITECTURE", sampleResult, null);
    expect(scope.mode).toBe("ENTIRE_REPOSITORY");
    expect(scope.symbolIds).toEqual([]);
  });

  it("maps selected graph node into symbol scope", () => {
    const scope = buildContextScope("SELECTED_SYMBOLS", sampleResult, {
      type: "node",
      id: "node:sym:c",
    });
    expect(scope.mode).toBe("SELECTED_SYMBOLS");
    expect(scope.symbolIds).toEqual(["sym:c"]);
    expect(scope.graphNodeIds).toEqual(["node:sym:c"]);
  });

  it("maps selected symbol file path for selected files purpose", () => {
    const scope = buildContextScope("SELECTED_FILES", sampleResult, {
      type: "node",
      id: "node:sym:c",
    });
    expect(scope.mode).toBe("SELECTED_FILES");
    expect(scope.filePaths).toEqual(["UserController.java"]);
  });
});

describe("AI task selection", () => {
  it("exposes useful presets including custom", () => {
    const ids = AI_TASK_OPTIONS.map((option) => option.id);
    expect(ids).toContain("EXPLAIN_ARCHITECTURE");
    expect(ids).toContain("EXPLAIN_AUTHENTICATION_SECURITY");
    expect(ids).toContain("TRACE_API_REQUEST");
    expect(ids).toContain("CUSTOM");
  });

  it("maps presets to suggested scopes", () => {
    expect(suggestedPurposeForAiTask("EXPLAIN_ARCHITECTURE")).toBe("ARCHITECTURE");
    expect(suggestedPurposeForAiTask("EXPLAIN_AUTHENTICATION_SECURITY")).toBe("SECURITY");
    expect(suggestedPurposeForAiTask("EXPLAIN_SELECTED_CLASS_SYMBOL")).toBe("SELECTED_SYMBOLS");
    expect(suggestedPurposeForAiTask("TRACE_API_REQUEST")).toBe("API_BACKEND");
    expect(suggestedPurposeForAiTask("EXPLAIN_DATABASE_JPA")).toBe("DATABASE_JPA");
    expect(suggestedPurposeForAiTask("CUSTOM")).toBe("CUSTOM");
  });

  it("requires custom task text for CUSTOM", () => {
    expect(validateAiTask("CUSTOM", "")).toMatch(/custom/i);
    expect(validateAiTask("CUSTOM", "  ")).toMatch(/custom/i);
    expect(validateAiTask("CUSTOM", "Explain auth")).toBeNull();
    expect(validateAiTask("EXPLAIN_ARCHITECTURE", "")).toBeNull();
  });
});

describe("token budget handling", () => {
  it("resolves presets and custom budgets", () => {
    expect(resolveTokenBudget("8000", "1")).toBe(8000);
    expect(resolveTokenBudget("custom", "12000")).toBe(12000);
    expect(resolveTokenBudget("custom", "abc")).toBeNaN();
  });

  it("validates budget bounds", () => {
    expect(validateTokenBudget(8000)).toBeNull();
    expect(validateTokenBudget(499)).toMatch(/500/);
    expect(validateTokenBudget(200001)).toMatch(/200000/);
  });
});

describe("prompt preview and copy", () => {
  it("previews context and prompt as separate representations", () => {
    const entry = historyEntry();
    expect(previewText("context", entry)).toContain("# Repository Context");
    expect(previewText("prompt", entry)).toContain("TASK:");
    expect(previewText("prompt", entry)).not.toEqual(previewText("context", entry));
  });

  it("copy target follows active view", () => {
    const entry = historyEntry();
    expect(copyTarget("prompt", entry)).toBe(entry.prompt);
    expect(copyTarget("context", entry)).toBe(entry.content);
  });

  it("download names distinguish prompt vs context", () => {
    const entry = historyEntry();
    expect(downloadName("prompt", entry)).toContain("ai-prompt");
    expect(downloadName("context", entry)).toContain("context");
  });

  it("treats missing prompt as empty for prompt view", () => {
    const entry = historyEntry({ prompt: null });
    expect(previewText("prompt", entry)).toBe("");
  });
});

describe("history / Try Another / New Context", () => {
  it("keeps multiple generated contexts in history", () => {
    const first = historyEntry({ id: "a", title: "One" });
    const second = historyEntry({ id: "b", title: "Two", aiTask: "TRACE_API_REQUEST" });
    const history = appendHistory(appendHistory([], first), second);
    expect(history.map((item) => item.id)).toEqual(["b", "a"]);
    expect(history).toHaveLength(2);
  });

  it("caps history length", () => {
    let history: ContextHistoryEntry[] = [];
    for (let i = 0; i < 25; i += 1) {
      history = appendHistory(history, historyEntry({ id: `id-${i}` }), 20);
    }
    expect(history).toHaveLength(20);
    expect(history[0].id).toBe("id-24");
  });

  it("Try Another preserves task/scope configuration defaults while clearing active result", () => {
    // Try Another only clears active/view in the component; form state is intentionally preserved.
    const form = defaultStudioFormState();
    form.aiTask = "TRACE_API_REQUEST";
    form.purpose = "API_BACKEND";
    form.budgetPreset = "16000";
    expect(form.aiTask).toBe("TRACE_API_REQUEST");
    expect(form.purpose).toBe("API_BACKEND");
    expect(form.budgetPreset).toBe("16000");
  });

  it("New Context restores a fresh configuration", () => {
    const fresh = defaultStudioFormState();
    expect(fresh.aiTask).toBe("EXPLAIN_ARCHITECTURE");
    expect(fresh.purpose).toBe("ARCHITECTURE");
    expect(fresh.budgetPreset).toBe("8000");
    expect(fresh.customTask).toBe("");
    expect(fresh.format).toBe("markdown");
  });
});

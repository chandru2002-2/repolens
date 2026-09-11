import { FormEvent, useMemo, useState } from "react";
import {
  generateContext,
  type AiTaskPreset,
  type ContextPurpose,
  type ContextResponse,
  type ContextStrategy,
  type AnalysisResponse,
} from "./api";
import type { Selection } from "./graphModel";
import { symbolDetailForNode } from "./api";

const BUDGET_PRESETS = [2000, 4000, 8000, 16000, 32000] as const;

const PURPOSE_OPTIONS: Array<{ id: ContextPurpose; label: string }> = [
  { id: "FULL_REPOSITORY", label: "Full Repository" },
  { id: "ARCHITECTURE", label: "Architecture" },
  { id: "SELECTED_FILES", label: "Selected Files" },
  { id: "SELECTED_SYMBOLS", label: "Selected Symbols" },
  { id: "API_BACKEND", label: "API / Backend" },
  { id: "DATABASE_JPA", label: "Database / JPA" },
  { id: "SECURITY", label: "Security" },
  { id: "CUSTOM", label: "Custom" },
];

export const AI_TASK_OPTIONS: Array<{ id: AiTaskPreset; label: string }> = [
  { id: "EXPLAIN_ARCHITECTURE", label: "Explain the architecture" },
  { id: "EXPLAIN_AUTHENTICATION_SECURITY", label: "Explain authentication/security" },
  { id: "EXPLAIN_SELECTED_CLASS_SYMBOL", label: "Explain a selected class/symbol" },
  { id: "TRACE_API_REQUEST", label: "Trace an API request" },
  { id: "EXPLAIN_DATABASE_JPA", label: "Explain database/JPA relationships" },
  { id: "EXPLAIN_DEPENDENCIES", label: "Explain dependencies" },
  { id: "HELP_DEBUG_SELECTED_CODE", label: "Help debug selected code" },
  { id: "IDENTIFY_ARCHITECTURAL_PROBLEMS", label: "Identify potential architectural problems" },
  { id: "GENERATE_ONBOARDING_GUIDANCE", label: "Generate onboarding guidance" },
  { id: "GENERATE_DOCUMENTATION", label: "Generate documentation" },
  { id: "CUSTOM", label: "Custom" },
];

export const CONTEXT_STRATEGY_OPTIONS: Array<{ id: ContextStrategy; label: string }> = [
  { id: "ARCHITECTURE_OVERVIEW", label: "Architecture Overview" },
  { id: "SOURCE_AND_SYMBOLS", label: "Architecture + Source" },
  { id: "COMPACT_ARCHITECTURE", label: "Compact Architecture" },
  { id: "ALTERNATE_PRIORITIZATION", label: "Reordered Detail" },
];

export type ContextHistoryEntry = ContextResponse & { createdAt: number; version: number };
export type StudioView = "form" | "context" | "prompt";

type Props = {
  jobId: string;
  result: AnalysisResponse;
  selection: Selection;
  open: boolean;
  onClose: () => void;
};

export function suggestedPurposeForAiTask(task: AiTaskPreset): ContextPurpose {
  switch (task) {
    case "EXPLAIN_ARCHITECTURE":
    case "EXPLAIN_DEPENDENCIES":
    case "IDENTIFY_ARCHITECTURAL_PROBLEMS":
      return "ARCHITECTURE";
    case "EXPLAIN_AUTHENTICATION_SECURITY":
      return "SECURITY";
    case "EXPLAIN_SELECTED_CLASS_SYMBOL":
    case "HELP_DEBUG_SELECTED_CODE":
      return "SELECTED_SYMBOLS";
    case "TRACE_API_REQUEST":
      return "API_BACKEND";
    case "EXPLAIN_DATABASE_JPA":
      return "DATABASE_JPA";
    case "GENERATE_ONBOARDING_GUIDANCE":
    case "GENERATE_DOCUMENTATION":
      return "FULL_REPOSITORY";
    case "CUSTOM":
      return "CUSTOM";
  }
}

export function resolveTokenBudget(budgetPreset: string, customBudget: string): number {
  if (budgetPreset === "custom") {
    const parsed = Number(customBudget);
    return Number.isFinite(parsed) ? Math.trunc(parsed) : NaN;
  }
  return Number(budgetPreset);
}

export function validateTokenBudget(tokenBudget: number): string | null {
  if (!Number.isFinite(tokenBudget) || tokenBudget < 500 || tokenBudget > 200000) {
    return "Token budget must be between 500 and 200000.";
  }
  return null;
}

export function validateAiTask(aiTask: AiTaskPreset, customTask: string): string | null {
  if (aiTask === "CUSTOM" && customTask.trim().length === 0) {
    return "Enter a custom AI task description.";
  }
  return null;
}

export function nextContextStrategy(current?: string | null): ContextStrategy {
  const ids = CONTEXT_STRATEGY_OPTIONS.map((option) => option.id);
  const index = ids.indexOf((current as ContextStrategy) ?? "ARCHITECTURE_OVERVIEW");
  return ids[(index + 1) % ids.length];
}

export function strategyLabel(strategy?: string | null, title?: string | null): string {
  return CONTEXT_STRATEGY_OPTIONS.find((option) => option.id === strategy)?.label
    ?? title
    ?? "Architecture Overview";
}

export function contextVersionLabel(
  version: number,
  strategy?: string | null,
  title?: string | null,
): string {
  return `Context ${version} — ${strategyLabel(strategy, title)}`;
}

export function previewText(
  view: Exclude<StudioView, "form">,
  entry: ContextHistoryEntry,
): string {
  if (view === "prompt") {
    return entry.prompt ?? "";
  }
  return entry.content;
}

export function copyTarget(
  view: Exclude<StudioView, "form">,
  entry: ContextHistoryEntry,
): string {
  return previewText(view, entry);
}

export function downloadName(
  view: Exclude<StudioView, "form">,
  entry: ContextHistoryEntry,
): string {
  if (view === "prompt") {
    return `repolens-ai-prompt-${(entry.aiTask ?? "task").toLowerCase()}.txt`;
  }
  const ext = entry.format === "json" ? "json" : "md";
  return `repolens-context-${entry.purpose.toLowerCase()}.${ext}`;
}

export function buildContextScope(
  purpose: ContextPurpose,
  result: AnalysisResponse,
  selection: Selection,
): {
  mode: string;
  filePaths: string[];
  symbolIds: string[];
  graphNodeIds: string[];
} {
  const selectedNode =
    selection?.type === "node"
      ? result.graph.nodes.find((node) => node.id === selection.id) ?? null
      : null;
  const symbol = symbolDetailForNode(result, selectedNode);
  const graphNodeIds = selection?.type === "node" ? [selection.id] : [];
  const symbolIds = symbol ? [symbol.id] : [];
  const filePaths = symbol?.filePath ? [symbol.filePath] : [];

  if (purpose === "SELECTED_FILES") {
    return { mode: "SELECTED_FILES", filePaths, symbolIds: [], graphNodeIds: [] };
  }
  if (purpose === "SELECTED_SYMBOLS") {
    return { mode: "SELECTED_SYMBOLS", filePaths: [], symbolIds, graphNodeIds };
  }
  if (purpose === "CUSTOM" && (symbolIds.length > 0 || filePaths.length > 0)) {
    return { mode: "CUSTOM", filePaths, symbolIds, graphNodeIds };
  }
  return { mode: "ENTIRE_REPOSITORY", filePaths: [], symbolIds: [], graphNodeIds: [] };
}

export function appendHistory(
  history: ContextHistoryEntry[],
  entry: ContextHistoryEntry,
  limit = 20,
): ContextHistoryEntry[] {
  return [entry, ...history].slice(0, limit);
}

export function defaultStudioFormState() {
  return {
    aiTask: "EXPLAIN_ARCHITECTURE" as AiTaskPreset,
    customTask: "",
    purpose: "ARCHITECTURE" as ContextPurpose,
    budgetPreset: "8000",
    customBudget: "8000",
    format: "markdown" as "markdown" | "json",
    strategy: "ARCHITECTURE_OVERVIEW" as ContextStrategy,
  };
}

export function ContextStudio({ jobId, result, selection, open, onClose }: Props) {
  const defaults = defaultStudioFormState();
  const [aiTask, setAiTask] = useState<AiTaskPreset>(defaults.aiTask);
  const [customTask, setCustomTask] = useState(defaults.customTask);
  const [purpose, setPurpose] = useState<ContextPurpose>(defaults.purpose);
  const [budgetPreset, setBudgetPreset] = useState<string>(defaults.budgetPreset);
  const [customBudget, setCustomBudget] = useState(defaults.customBudget);
  const [format, setFormat] = useState<"markdown" | "json">(defaults.format);
  const [strategy, setStrategy] = useState<ContextStrategy>(defaults.strategy);
  const [familyVersion, setFamilyVersion] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [active, setActive] = useState<ContextHistoryEntry | null>(null);
  const [history, setHistory] = useState<ContextHistoryEntry[]>([]);
  const [view, setView] = useState<StudioView>("form");

  const selectedLabel = useMemo(() => {
    if (selection?.type !== "node") {
      return "None";
    }
    const node = result.graph.nodes.find((item) => item.id === selection.id);
    return node?.label ?? selection.id;
  }, [result.graph.nodes, selection]);

  const tokenBudget = useMemo(
    () => resolveTokenBudget(budgetPreset, customBudget),
    [budgetPreset, customBudget],
  );

  if (!open) {
    return null;
  }

  function onAiTaskChange(next: AiTaskPreset) {
    setAiTask(next);
    setPurpose(suggestedPurposeForAiTask(next));
  }

  async function onGenerate(event?: FormEvent) {
    event?.preventDefault();
    setError(null);
    const budgetError = validateTokenBudget(tokenBudget);
    if (budgetError) {
      setError(budgetError);
      return;
    }
    const taskError = validateAiTask(aiTask, customTask);
    if (taskError) {
      setError(taskError);
      return;
    }
    const scope = buildContextScope(purpose, result, selection);
    if (purpose === "SELECTED_SYMBOLS" && scope.symbolIds.length === 0 && scope.graphNodeIds.length === 0) {
      setError("Select a graph node/symbol first for Selected Symbols context.");
      return;
    }
    if (purpose === "SELECTED_FILES" && scope.filePaths.length === 0) {
      setError("Select a symbol with a file path first for Selected Files context.");
      return;
    }
    setBusy(true);
    try {
      const response = await generateContext(jobId, {
        purpose,
        scope,
        tokenBudget,
        format,
        aiTask,
        customTask: customTask.trim() || undefined,
      });
      const entry: ContextHistoryEntry = { ...response, createdAt: Date.now() };
      setActive(entry);
      setHistory((prev) => appendHistory(prev, entry));
      setView("context");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Context generation failed");
    } finally {
      setBusy(false);
    }
  }

  function onGeneratePrompt() {
    if (!active?.prompt) {
      setError("Generate context with an AI task first to produce a prompt.");
      return;
    }
    setError(null);
    setView("prompt");
  }

  function onTryAnother() {
    setActive(null);
    setView("form");
    setError(null);
  }

  function onNewContext() {
    const fresh = defaultStudioFormState();
    setAiTask(fresh.aiTask);
    setCustomTask(fresh.customTask);
    setPurpose(fresh.purpose);
    setBudgetPreset(fresh.budgetPreset);
    setCustomBudget(fresh.customBudget);
    setFormat(fresh.format);
    setActive(null);
    setView("form");
    setError(null);
  }

  async function onCopy() {
    if (!active || view === "form") {
      return;
    }
    await navigator.clipboard.writeText(copyTarget(view, active));
  }

  function onDownload() {
    if (!active || view === "form") {
      return;
    }
    const text = copyTarget(view, active);
    const name = downloadName(view, active);
    const type = view === "prompt"
      ? "text/plain"
      : active.format === "json"
        ? "application/json"
        : "text/markdown";
    const blob = new Blob([text], { type });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = name;
    anchor.click();
    URL.revokeObjectURL(url);
  }

  return (
    <div className="context-studio" role="dialog" aria-label="Context Studio">
      <div className="context-studio-head">
        <h2>Context Studio</h2>
        <button type="button" className="context-close" onClick={onClose}>
          Close
        </button>
      </div>
      <p className="context-sub">
        Package repository context and a ready-to-copy AI prompt. Deterministic — no AI API key required.
      </p>

      {view === "form" ? (
        <form className="context-form" onSubmit={onGenerate}>
          <label>
            AI Task
            <select value={aiTask} onChange={(e) => onAiTaskChange(e.target.value as AiTaskPreset)}>
              {AI_TASK_OPTIONS.map((option) => (
                <option key={option.id} value={option.id}>
                  {option.label}
                </option>
              ))}
            </select>
          </label>

          <label>
            Custom task
            <textarea
              rows={3}
              placeholder='e.g. Explain how authentication works and trace login through JWT validation.'
              value={customTask}
              onChange={(e) => setCustomTask(e.target.value)}
            />
          </label>

          <label>
            Scope
            <select value={purpose} onChange={(e) => setPurpose(e.target.value as ContextPurpose)}>
              {PURPOSE_OPTIONS.map((option) => (
                <option key={option.id} value={option.id}>
                  {option.label}
                </option>
              ))}
            </select>
          </label>

          <div className="context-meta">
            <span>Current selection: {selectedLabel}</span>
          </div>

          <label>
            Token budget
            <select value={budgetPreset} onChange={(e) => setBudgetPreset(e.target.value)}>
              {BUDGET_PRESETS.map((value) => (
                <option key={value} value={String(value)}>
                  {(value / 1000).toFixed(0)}K
                </option>
              ))}
              <option value="custom">Custom</option>
            </select>
          </label>

          {budgetPreset === "custom" ? (
            <label>
              Custom budget
              <input
                type="number"
                min={500}
                max={200000}
                value={customBudget}
                onChange={(e) => setCustomBudget(e.target.value)}
              />
            </label>
          ) : null}

          <label>
            Format
            <select value={format} onChange={(e) => setFormat(e.target.value as "markdown" | "json")}>
              <option value="markdown">Markdown</option>
              <option value="json">JSON</option>
            </select>
          </label>

          {error ? <p className="error">{error}</p> : null}

          <button type="submit" className="primary" disabled={busy}>
            {busy ? "Generating…" : "Generate Context"}
          </button>
        </form>
      ) : active ? (
        <div className="context-result">
          <div className="context-result-head">
            <div>
              <h3>{view === "prompt" ? "AI Prompt" : active.title}</h3>
              <p className="context-tokens">
                {view === "prompt"
                  ? (active.taskText ?? "Ready-to-copy prompt for ChatGPT / Claude / Cursor")
                  : `Estimated ${active.estimatedTokens.toLocaleString()} / ${active.tokenBudget.toLocaleString()} tokens${
                      active.tokenEstimateApproximate ? " (approximate)" : ""
                    }`}
              </p>
            </div>
            <div className="context-actions">
              <button type="button" className={view === "context" ? "primary" : undefined} onClick={() => setView("context")}>
                Context
              </button>
              <button
                type="button"
                className={view === "prompt" ? "primary" : undefined}
                onClick={onGeneratePrompt}
                disabled={!active.prompt}
              >
                {view === "prompt" ? "AI Prompt" : "Generate AI Prompt"}
              </button>
            </div>
          </div>

          {view === "context" && active.included.length > 0 ? (
            <div className="context-lists">
              <div>
                <h4>Included</h4>
                <ul>
                  {active.included.map((item) => (
                    <li key={`in-${item}`}>{item}</li>
                  ))}
                </ul>
              </div>
              {active.excluded.length > 0 ? (
                <div>
                  <h4>Excluded</h4>
                  <ul>
                    {active.excluded.map((item) => (
                      <li key={`ex-${item}`}>{item}</li>
                    ))}
                  </ul>
                </div>
              ) : null}
            </div>
          ) : null}

          {error ? <p className="error">{error}</p> : null}

          <pre className="context-preview">{previewText(view, active)}</pre>

          <div className="context-actions">
            <button type="button" onClick={onCopy}>
              {view === "prompt" ? "Copy Prompt" : "Copy"}
            </button>
            <button type="button" onClick={onDownload}>
              Download
            </button>
            <button type="button" onClick={onTryAnother}>
              Try Another
            </button>
            <button type="button" className="primary" onClick={onNewContext}>
              New Context
            </button>
          </div>
          <p className="context-meta">
            Use with ChatGPT / Claude / Cursor / other AI tools. Prompt generation is local — no provider call.
          </p>
        </div>
      ) : null}

      {history.length > 0 ? (
        <div className="context-history">
          <h3>Context History</h3>
          <ul>
            {history.map((entry) => (
              <li key={entry.id}>
                <button
                  type="button"
                  className="context-history-item"
                  onClick={() => {
                    setActive(entry);
                    setView(entry.prompt ? "prompt" : "context");
                  }}
                >
                  <span>
                    {entry.aiTask ? `${entry.aiTask} · ` : ""}
                    {entry.title}
                  </span>
                  <span>{entry.estimatedTokens.toLocaleString()} tokens</span>
                </button>
              </li>
            ))}
          </ul>
        </div>
      ) : null}
    </div>
  );
}

export function defaultContextPurpose(): ContextPurpose {
  return "ARCHITECTURE";
}

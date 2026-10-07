import { FormEvent, useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import type { CSSProperties } from "react";
import { formatRelativeTime } from "./format";
import { readRecent, rememberRepository, writeRecent, type RecentRepository } from "./recentRepositories";
import { githubBlobUrl } from "./sourceLink";
import {
  TUTORIAL_STEPS,
  TUTORIAL_VERSION,
  readTutorial,
  shouldShowTutorial,
  writeTutorial,
  type TutorialRecord,
} from "./tutorial";
import {
  analysisFacts,
  analysisStatusCopy,
  failureReason,
  formatElapsed,
  isCurrentAnalysis,
  isLongRunning,
  longRunningNotice,
  validateRepositorySource,
} from "./analysisExperience";
import {
  AnalysisFailedError,
  type AnalysisResponse,
  isAbortError,
  type JobStatus,
  metadataWarning,
  oversizedSkipWarning,
  startAnalysis,
  waitForResult,
} from "./api";
import {
  analysisSurface,
  explorerGridClass,
  phaseForNewLens,
  showNewLensControl,
  shouldClearSubmitError,
  type AppPhase,
} from "./appNav";
import { DetailsPanel } from "./DetailsPanel";
import { ExplorerPanel } from "./ExplorerPanel";
import { GraphFilters } from "./GraphFilters";
import { IntelligencePanel } from "./IntelligencePanel";
import {
  DIAGRAM_TABS,
  DEFAULT_GRAPH_FILTERS,
  isCoreDiagram,
  kindMeta,
  searchNodes,
  type GraphFilterState,
  type GraphViewMode,
  type Selection,
} from "./graphModel";
import { INTELLIGENCE_TABS, type IntelligenceTab } from "./intelligence";
import { RepositoryGraph } from "./RepositoryGraph";
import {
  applyResolvedTheme,
  readThemePreference,
  resolveTheme,
  writeThemePreference,
  type ResolvedTheme,
  type ThemePreference,
} from "./theme";

const REPO_URL = "https://github.com/chandru2002-2/repolens";
const AUTHOR_URL = "https://github.com/chandru2002-2";

function Brand({ onClick }: { onClick?: () => void }) {
  if (onClick) {
    return (
      <button type="button" className="brand-mark brand-button" onClick={onClick}>
        REPO<span className="slash">//</span>LENS
      </button>
    );
  }
  return (
    <span className="brand-mark">
      REPO<span className="slash">//</span>LENS
    </span>
  );
}

function SiteFooter() {
  return (
    <footer className="site-footer">
      <a href={REPO_URL} target="_blank" rel="noopener noreferrer">
        RepoLens
      </a>
      <span className="site-footer-sep" aria-hidden="true">
        ·
      </span>
      <span>
        Built by{" "}
        <a href={AUTHOR_URL} target="_blank" rel="noopener noreferrer">
          Chandru M
        </a>
      </span>
      <span className="site-footer-sep" aria-hidden="true">
        ·
      </span>
      <span>© 2026</span>
    </footer>
  );
}

function ThemeToggle({
  preference,
  onChange,
}: {
  preference: ThemePreference;
  onChange: (value: ThemePreference) => void;
}) {
  return (
    <div className="theme-toggle" role="group" aria-label="Color theme">
      {(
        [
          ["light", "Light"],
          ["dark", "Dark"],
          ["auto", "Auto"],
        ] as const
      ).map(([id, label]) => (
        <button
          key={id}
          type="button"
          className={preference === id ? "active" : undefined}
          aria-pressed={preference === id}
          onClick={() => onChange(id)}
          title={
            id === "auto"
              ? "Follow browser light/dark preference"
              : `Use ${label.toLowerCase()} theme`
          }
        >
          {label}
        </button>
      ))}
    </div>
  );
}

function AnalyzeForm({
  source,
  onSourceChange,
  onSubmit,
  error,
}: {
  source: string;
  onSourceChange: (value: string) => void;
  onSubmit: (event: FormEvent) => void;
  error: string | null;
}) {
  return (
    <form className="analyze-form" aria-label="Analyze a repository" onSubmit={onSubmit}>
      <label className="field-label" htmlFor="source">
        GitHub repository URL
      </label>
      <div className="input-row">
        <span className="prompt" aria-hidden="true">
          &gt;
        </span>
        <input
          id="source"
          name="source"
          value={source}
          onChange={(event) => onSourceChange(event.target.value)}
          placeholder="https://github.com/owner/repo"
          autoComplete="off"
          aria-required="true"
          aria-invalid={error ? true : undefined}
          autoFocus
        />
      </div>
      <div className="analyze-actions">
        <button type="submit">Analyze</button>
      </div>
      {error ? <p className="error">{error}</p> : null}
    </form>
  );
}

function RecentMenu({
  entries,
  onFill,
  onAnalyze,
}: {
  entries: RecentRepository[];
  onFill: (url: string) => void;
  onAnalyze: (url: string) => void;
}) {
  if (entries.length === 0) {
    return null;
  }
  return (
    <details className="recent-menu">
      <summary>Recent repositories</summary>
      <ul>
        {entries.map((entry) => (
          <li key={entry.url}>
            <button type="button" className="recent-fill" onClick={() => onFill(entry.url)}>
              <span>{entry.owner ? `${entry.owner}/${entry.name}` : entry.name}</span>
              <span className="recent-when">{formatRelativeTime(entry.lastAnalyzedAt) ?? entry.lastAnalyzedAt}</span>
            </button>
            <button
              type="button"
              aria-label={`Analyze ${entry.owner ? `${entry.owner}/${entry.name}` : entry.name}`}
              onClick={() => onAnalyze(entry.url)}
            >
              Analyze
            </button>
          </li>
        ))}
      </ul>
    </details>
  );
}

function TutorialCard({
  stepIndex,
  onNext,
  onSkip,
}: {
  stepIndex: number;
  onNext: () => void;
  onSkip: () => void;
}) {
  const step = TUTORIAL_STEPS[stepIndex];
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        onSkip();
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onSkip]);
  if (!step) {
    return null;
  }
  return (
    <div className="tutorial-card" role="dialog" aria-labelledby="tutorial-title">
      <h2 id="tutorial-title">{step.title}</h2>
      <p>{step.body}</p>
      <p className="tutorial-progress">{stepIndex + 1} / {TUTORIAL_STEPS.length}</p>
      <div className="analyze-actions">
        <button type="button" className="ghost-button" onClick={onSkip}>Skip tutorial</button>
        <button type="button" onClick={onNext}>{stepIndex === TUTORIAL_STEPS.length - 1 ? "Done" : "Next"}</button>
      </div>
    </div>
  );
}

export function AnalysisScreen({
  status,
  elapsedMs,
  facts,
  progress,
  sizeBytes,
}: {
  status: JobStatus["status"] | null;
  elapsedMs: number;
  facts: Array<{ label: string; value: string }>;
  progress?: JobStatus["progress"];
  sizeBytes?: number | null;
}) {
  const copy = analysisStatusCopy(status);
  const waiting = isLongRunning(elapsedMs);
  const notice = waiting ? longRunningNotice(sizeBytes) : null;
  const percent = progress?.detailAvailable ? progress.percent : null;
  return (
    <main className="analysis-screen" data-screen="analysis" aria-live="polite">
      {status ? <p className="analysis-kicker">Status: {status}</p> : null}
      <h1 className="analysis-title">{copy.title}</h1>
      <p className="analysis-detail">{copy.detail}</p>
      {progress?.repositoryName ? <p className="analysis-repo">{progress.repositoryName}</p> : null}
      {percent == null ? (
        <p className="analysis-unavailable">Detailed progress is unavailable while this analysis is running.</p>
      ) : (
        <p className="analysis-percent">{percent}%</p>
      )}
      <div
        className="analysis-progress"
        role="progressbar"
        aria-label="Analysis progress"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={percent ?? undefined}
        aria-valuetext={percent == null ? "Detailed progress unavailable" : `${percent}%`}
      >
        <span
          className={percent == null ? "analysis-progress-bar" : "analysis-progress-bar determinate"}
          style={percent == null ? undefined : { width: `${percent}%`, transform: "none" }}
        />
      </div>
      {progress?.detailAvailable && progress.stages.length > 0 ? (
        <ol className="analysis-stages">
          {progress.stages.map((stage) => (
            <li key={stage.id} className={`analysis-stage ${stage.state}`}>
              <span aria-hidden="true">{stage.state === "complete" ? "✓" : stage.state === "active" ? "●" : "○"}</span>
              {stage.label}
            </li>
          ))}
        </ol>
      ) : null}
      <p className="analysis-elapsed">Elapsed {formatElapsed(elapsedMs)}</p>
      {facts.length > 0 ? (
        <dl className="analysis-facts">
          {facts.map((fact) => (
            <div key={fact.label}>
              <dt>{fact.label}</dt>
              <dd>{fact.value}</dd>
            </div>
          ))}
        </dl>
      ) : null}
      {notice ? (
        <div className="analysis-long-running" role="status">
          <p className="analysis-long-running-title">{notice.title}</p>
          <p>{notice.detail}</p>
        </div>
      ) : null}
    </main>
  );
}

function AnalysisError({
  reason,
  stage,
  onRetry,
  onChangeRepository,
}: {
  reason: string;
  stage?: string | null;
  onRetry: () => void;
  onChangeRepository: () => void;
}) {
  return (
    <main className="analysis-screen" data-screen="error">
      <h1 className="analysis-title">Analysis failed</h1>
      {stage ? <p className="analysis-kicker">{stage}</p> : null}
      <p className="analysis-detail">
        Reason:
        <span className="analysis-reason">{reason}</span>
      </p>
      <div className="analyze-actions analysis-error-actions">
        <button type="button" className="ghost-button" onClick={onChangeRepository}>
          Change repository
        </button>
        <button type="button" onClick={onRetry}>
          Try again
        </button>
      </div>
    </main>
  );
}

export default function App() {
  const [source, setSource] = useState("");
  const [phase, setPhase] = useState<AppPhase>("landing");
  const [entry, setEntry] = useState<"landing" | "compose">("landing");
  const [status, setStatus] = useState<JobStatus | null>(null);
  const [result, setResult] = useState<AnalysisResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [elapsedMs, setElapsedMs] = useState(0);
  const [runId, setRunId] = useState(0);
  const [recent, setRecent] = useState<RecentRepository[]>(() => readRecent(browserStorage()));
  const [tutorial, setTutorial] = useState<TutorialRecord>(() => readTutorial(browserStorage()));
  const [tutorialOpen, setTutorialOpen] = useState(false);
  const [tutorialStep, setTutorialStep] = useState(0);
  const [sourceNotice, setSourceNotice] = useState<string | null>(null);
  const abortRef = useRef<AbortController | null>(null);
  const generationRef = useRef(0);
  const startedAtRef = useRef<number | null>(null);
  const [selection, setSelection] = useState<Selection>(null);
  const [focusId, setFocusId] = useState<string | null>(null);
  const [view, setView] = useState<GraphViewMode>("architecture");
  const [intelTab, setIntelTab] = useState<IntelligenceTab | null>(null);
  const [filters, setFilters] = useState<GraphFilterState>(DEFAULT_GRAPH_FILTERS);
  const [query, setQuery] = useState("");
  const [searchOpen, setSearchOpen] = useState(false);
  const [inspectorCollapsed, setInspectorCollapsed] = useState(false);
  const [explorerCollapsed, setExplorerCollapsed] = useState(false);
  const searchBoxRef = useRef<HTMLDivElement | null>(null);
  const [searchMenuStyle, setSearchMenuStyle] = useState<CSSProperties>({});
  const [themePreference, setThemePreference] = useState<ThemePreference>(() =>
    readThemePreference(),
  );
  const [resolvedTheme, setResolvedTheme] = useState<ResolvedTheme>(() =>
    resolveTheme(readThemePreference()),
  );

  useLayoutEffect(() => {
    const resolved = resolveTheme(themePreference);
    setResolvedTheme(resolved);
    applyResolvedTheme(resolved);
    writeThemePreference(themePreference);

    if (themePreference !== "auto") {
      return;
    }
    const media = window.matchMedia("(prefers-color-scheme: dark)");
    const onChange = () => {
      const next = resolveTheme("auto");
      setResolvedTheme(next);
      applyResolvedTheme(next);
    };
    media.addEventListener("change", onChange);
    return () => media.removeEventListener("change", onChange);
  }, [themePreference]);

  const hits = useMemo(() => {
    if (!result || !query.trim()) {
      return [];
    }
    return searchNodes(result.graph.nodes, query);
  }, [result, query]);

  useLayoutEffect(() => {
    if (!searchOpen || hits.length === 0 || !searchBoxRef.current) {
      return;
    }
    const place = () => {
      const rect = searchBoxRef.current?.getBoundingClientRect();
      if (!rect) {
        return;
      }
      setSearchMenuStyle({
        position: "fixed",
        top: rect.bottom + 4,
        left: rect.left,
        width: rect.width,
        zIndex: 10000,
      });
    };
    place();
    window.addEventListener("resize", place);
    window.addEventListener("scroll", place, true);
    return () => {
      window.removeEventListener("resize", place);
      window.removeEventListener("scroll", place, true);
    };
  }, [searchOpen, hits.length, query]);

  useEffect(() => {
    if (!searchOpen) {
      return;
    }
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setSearchOpen(false);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [searchOpen]);

  useEffect(() => {
    return () => {
      abortRef.current?.abort();
    };
  }, []);

  useEffect(() => {
    if (phase === "ready" && result && shouldShowTutorial(tutorial)) {
      setTutorialOpen(true);
    }
    if (phase !== "ready") {
      setTutorialOpen(false);
    }
  }, [phase, result, tutorial]);

  useEffect(() => {
    if (phase !== "running") {
      return;
    }
    const started = startedAtRef.current ?? Date.now();
    const tick = () => setElapsedMs(Date.now() - started);
    tick();
    const timer = window.setInterval(tick, 1000);
    return () => window.clearInterval(timer);
  }, [phase, runId]);

  function abandonAnalysis() {
    abortRef.current?.abort();
    abortRef.current = null;
    generationRef.current += 1;
    return generationRef.current;
  }

  function clearWorkspaceSelection() {
    setSelection(null);
    setFocusId(null);
    setQuery("");
    setSearchOpen(false);
    setView("architecture");
    setIntelTab(null);
    setFilters(DEFAULT_GRAPH_FILTERS);
  }

  function openNewLens() {
    abandonAnalysis();
    setEntry("compose");
    setPhase(phaseForNewLens());
    setResult(null);
    setStatus(null);
    setError(null);
    clearWorkspaceSelection();
  }

  function goHome() {
    abandonAnalysis();
    setEntry("landing");
    setPhase("landing");
    setResult(null);
    setStatus(null);
    setError(null);
    setSelection(null);
    setFocusId(null);
    setQuery("");
    setSearchOpen(false);
    setIntelTab(null);
  }

  function changeRepository() {
    abandonAnalysis();
    setResult(null);
    setStatus(null);
    setError(null);
    setPhase(entry === "compose" ? "compose" : "landing");
  }

  /** Every repository submission, including a repeat analysis, enters here. */
  function beginAnalysis(rawSource: string) {
    const validation = validateRepositorySource(rawSource);
    if (!validation.ok) {
      setError(validation.message);
      return;
    }
    const generation = abandonAnalysis();
    const controller = new AbortController();
    abortRef.current = controller;
    setSource(validation.source);
    setError(null);
    setStatus(null);
    setResult(null);
    clearWorkspaceSelection();
    startedAtRef.current = Date.now();
    setElapsedMs(0);
    setRunId(generation);
    setPhase("running");
    void trackAnalysis(generation, controller, validation.source);
  }

  async function trackAnalysis(generation: number, controller: AbortController, trimmed: string) {
    try {
      const job = await startAnalysis(trimmed, controller.signal);
      if (!isCurrentAnalysis(generationRef.current, generation) || controller.signal.aborted) {
        return;
      }
      setStatus(job);
      const analysis = await waitForResult(
        job.id,
        (next) => {
          if (isCurrentAnalysis(generationRef.current, generation) && !controller.signal.aborted) {
            setStatus(next);
          }
        },
        controller.signal,
      );
      if (!isCurrentAnalysis(generationRef.current, generation) || controller.signal.aborted) {
        return;
      }
      setResult(analysis);
      const nextRecent = rememberRepository(readRecent(browserStorage()), {
        url: analysis.repository.source || trimmed,
        analyzedAt: new Date().toISOString(),
      });
      writeRecent(browserStorage(), nextRecent);
      setRecent(nextRecent);
      setSourceNotice(null);
      setPhase("ready");
    } catch (err) {
      if (
        !isCurrentAnalysis(generationRef.current, generation) ||
        controller.signal.aborted ||
        isAbortError(err)
      ) {
        return;
      }
      setPhase("error");
      setError(err instanceof AnalysisFailedError ? err.reason : err instanceof Error ? err.message : null);
    }
  }

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    beginAnalysis(source);
  }

  function persistTutorial(next: TutorialRecord) {
    setTutorial(next);
    writeTutorial(browserStorage(), next);
  }

  function skipTutorial() {
    setTutorialOpen(false);
    persistTutorial({ version: TUTORIAL_VERSION, completed: false, skipped: true });
  }

  function advanceTutorial() {
    if (tutorialStep >= TUTORIAL_STEPS.length - 1) {
      setTutorialOpen(false);
      persistTutorial({ version: TUTORIAL_VERSION, completed: true, skipped: false });
      return;
    }
    setTutorialStep((step) => step + 1);
  }

  function replayTutorial() {
    setTutorialStep(0);
    setTutorialOpen(true);
    persistTutorial({ version: TUTORIAL_VERSION, completed: false, skipped: false });
  }

  function openSource(nodeId: string) {
    if (!result) {
      return;
    }
    const node = result.graph.nodes.find((item) => item.id === nodeId);
    const symbol = result.symbols?.find((item) => item.id === node?.sourceEntityId);
    const revision = result.metadata?.lastCommit?.sha || result.metadata?.defaultBranch || null;
    const url = githubBlobUrl({
      source: result.repository.source,
      revision,
      filePath: symbol?.filePath,
      startLine: symbol?.startLine,
    });
    if (!url) {
      setSourceNotice("No GitHub source mapping for this selection.");
      return;
    }
    setSourceNotice(null);
    window.open(url, "_blank", "noopener,noreferrer");
    if (tutorialOpen && TUTORIAL_STEPS[tutorialStep]?.id === "github") {
      setTutorialOpen(false);
      persistTutorial({ version: TUTORIAL_VERSION, completed: true, skipped: false });
    }
  }

  function selectNode(id: string) {
    setSelection({ type: "node", id });
    const node = result?.graph.nodes.find((item) => item.id === id);
    if (
      intelTab === null &&
      node &&
      ["class", "interface", "enum", "type"].includes(node.kind)
    ) {
      if (view === "architecture" || view === "package") {
        setView("class");
      }
    }
  }

  function selectEntity(entityId: string) {
    const node = result?.graph.nodes.find(
      (item) => item.sourceEntityId === entityId || item.id === entityId,
    );
    if (node) {
      selectNode(node.id);
    }
  }

  function showDiagram(next: GraphViewMode) {
    setView(next);
    setIntelTab(null);
    setFocusId(null);
    setSelection(null);
  }

  function applySearchHit(id: string) {
    selectNode(id);
    setFocusId(id);
    setQuery("");
    setSearchOpen(false);
  }

  const surface = analysisSurface(phase, result != null);
  const loadingFacts = analysisFacts({
    source: status?.progress?.sourceUrl ?? status?.source ?? (phase === "running" ? source : null),
    repositoryName: status?.progress?.repositoryName,
    fileCount: status?.progress?.fileCount,
    moduleCount: null,
    symbolCount: null,
    importCount: null,
    sizeBytes: null,
  });
  const activeDiagram = result?.diagrams?.find((diagram) => diagram.type === view) ?? null;
  const graphNodes = isCoreDiagram(view)
    ? (result?.graph.nodes ?? [])
    : (activeDiagram?.graph.nodes ?? []);
  const graphEdges = isCoreDiagram(view)
    ? (result?.graph.edges ?? [])
    : (activeDiagram?.graph.edges ?? []);
  const diagramEmptyMessage =
    !isCoreDiagram(view) && graphNodes.length === 0
      ? activeDiagram?.emptyMessage || "No diagram data available for this view."
      : null;
  const diagramAdvisory =
    !isCoreDiagram(view) && graphNodes.length > 0
      ? activeDiagram?.advisoryMessage ||
        (activeDiagram?.truncated && activeDiagram.totalNodeCount
          ? `Showing ${activeDiagram.graph.nodes.length} of ${activeDiagram.totalNodeCount} nodes`
          : null)
      : null;

  const statusLabel =
    phase === "running"
      ? status?.status ?? "RUNNING"
      : phase === "error"
        ? "ERROR"
        : surface === "workspace"
          ? "READY"
          : "IDLE";

  return (
    <div className={surface === "workspace" ? "app exploring" : "app"}>
      <header className="topbar">
        <div className="topbar-left">
          <Brand onClick={phase !== "landing" ? goHome : undefined} />
          <ThemeToggle preference={themePreference} onChange={setThemePreference} />
        </div>

        {surface === "workspace" || showNewLensControl(phase) ? (
          <div className="topbar-tools">
            {surface === "workspace" ? (
              <div className="search-box" ref={searchBoxRef}>
                <label className="sr-only" htmlFor="repo-search">
                  Search repository
                </label>
                <span className="search-glyph" aria-hidden="true">
                  &gt;
                </span>
                <input
                  id="repo-search"
                  value={query}
                  onChange={(event) => {
                    setQuery(event.target.value);
                    setSearchOpen(true);
                  }}
                  onFocus={() => setSearchOpen(true)}
                  onBlur={() => {
                    window.setTimeout(() => setSearchOpen(false), 150);
                  }}
                  placeholder="Search repository..."
                  autoComplete="off"
                />
                {searchOpen && hits.length > 0 ? (
                  <ul className="search-results" role="listbox" style={searchMenuStyle}>
                    {hits.map((hit) => (
                      <li key={hit.id}>
                        <button
                          type="button"
                          onMouseDown={(event) => event.preventDefault()}
                          onClick={() => applySearchHit(hit.id)}
                        >
                          <span className="search-kind">{kindMeta(hit.kind).short}</span>
                          <span>{hit.label}</span>
                        </button>
                      </li>
                    ))}
                  </ul>
                ) : null}
              </div>
            ) : null}
            {surface === "workspace" && !shouldShowTutorial(tutorial) ? (
              <button className="tool-button" type="button" onClick={replayTutorial}>
                Replay tutorial
              </button>
            ) : null}
            {showNewLensControl(phase) ? (
              <button className="tool-button" type="button" onClick={openNewLens}>
                New
              </button>
            ) : null}
          </div>
        ) : null}
      </header>

      {surface === "analysis" ? (
        <AnalysisScreen
          status={status?.status ?? null}
          elapsedMs={elapsedMs}
          facts={loadingFacts}
          progress={status?.progress}
        />
      ) : surface === "error" ? (
        <AnalysisError
          reason={failureReason(error)}
          stage={status?.progress?.activeLabel}
          onRetry={() => beginAnalysis(source)}
          onChangeRepository={changeRepository}
        />
      ) : surface === "form" ? (
        <main className={entry === "landing" ? "landing" : "compose"}>
          <h1 className="landing-title">Analyze a repository</h1>
          {entry === "landing" ? (
            <section className="landing-intro" aria-labelledby="landing-intro-title">
              <h2 id="landing-intro-title">Understand unfamiliar codebases</h2>
              <p>
                RepoLens analyzes GitHub repositories to map software architecture, dependencies, REST APIs,
                database entities, classes, and code relationships in interactive diagrams.
              </p>
            </section>
          ) : null}
          <AnalyzeForm
            source={source}
            onSourceChange={(value) => {
              setSource(value);
              if (shouldClearSubmitError(phase)) {
                setError(null);
              }
            }}
            onSubmit={onSubmit}
            error={error}
          />
          <RecentMenu
            entries={recent}
            onFill={(url) => {
              setSource(url);
              setError(null);
            }}
            onAnalyze={(url) => {
              setSource(url);
              beginAnalysis(url);
            }}
          />
        </main>
      ) : surface === "workspace" && result ? (
        <div className="workspace" data-screen="workspace">
          {sourceNotice ? <p className="error workspace-notice" role="status">{sourceNotice}</p> : null}
          <main className={explorerGridClass(explorerCollapsed, inspectorCollapsed)}>
            <ExplorerPanel
              nodes={result.graph.nodes}
              edges={result.graph.edges}
              selectedId={selection?.type === "node" ? selection.id : null}
              onSelectNode={selectNode}
              repoName={result.repository.name}
              repoSource={result.repository.source}
              skipWarning={oversizedSkipWarning(result)}
              metadata={result.metadata}
              metadataWarning={metadataWarning(result)}
              collapsed={explorerCollapsed}
              onToggleCollapsed={() => setExplorerCollapsed((value) => !value)}
            />

            <section className="graph-stage">
              <div className="view-tabs" role="tablist" aria-label="Diagram views">
                {DIAGRAM_TABS.filter((tab) => tab.group === "core").map((tab) => (
                  <button
                    key={tab.id}
                    type="button"
                    role="tab"
                    aria-selected={intelTab === null && view === tab.id}
                    className={intelTab === null && view === tab.id ? "view-tab active" : "view-tab"}
                    onClick={() => showDiagram(tab.id)}
                  >
                    {tab.label}
                  </button>
                ))}
                {INTELLIGENCE_TABS.map((tab) => (
                  <button
                    key={tab.id}
                    type="button"
                    role="tab"
                    aria-selected={intelTab === tab.id}
                    className={intelTab === tab.id ? "view-tab active" : "view-tab"}
                    onClick={() => {
                      setIntelTab(tab.id);
                      setFocusId(null);
                    }}
                  >
                    {tab.label}
                  </button>
                ))}
                <label className="view-select-wrap">
                  <span className="sr-only">More diagrams</span>
                  <select
                    className="view-select"
                    value={intelTab === null && !isCoreDiagram(view) ? view : ""}
                    aria-label="Additional diagrams"
                    onChange={(event) => {
                      const next = event.target.value as GraphViewMode;
                      if (!next) {
                        return;
                      }
                      showDiagram(next);
                    }}
                  >
                    <option value="">More diagrams…</option>
                    {DIAGRAM_TABS.filter((tab) => tab.group === "advanced").map((tab) => (
                      <option key={tab.id} value={tab.id}>
                        {tab.label}
                      </option>
                    ))}
                  </select>
                </label>
              </div>
              {intelTab ? (
                <IntelligencePanel result={result} tab={intelTab} onSelectEntity={selectEntity} />
              ) : (
                <>
                  <GraphFilters view={view} filters={filters} onChange={setFilters} />
                  {diagramEmptyMessage ? (
                    <p className="diagram-empty" role="status">
                      {diagramEmptyMessage}
                    </p>
                  ) : (
                    <>
                      {diagramAdvisory ? (
                        <p className="diagram-truncate" role="status">
                          {diagramAdvisory}
                        </p>
                      ) : null}
                      <RepositoryGraph
                        nodes={graphNodes}
                        edges={graphEdges}
                        view={view}
                        filters={filters}
                        selection={selection}
                        focusId={focusId}
                        onSelect={setSelection}
                        onOpenSource={openSource}
                        theme={resolvedTheme}
                      />
                    </>
                  )}
                </>
              )}
            </section>

            <DetailsPanel
              result={result}
              nodes={isCoreDiagram(view) ? result.graph.nodes : graphNodes}
              edges={isCoreDiagram(view) ? result.graph.edges : graphEdges}
              selection={selection}
              onSelectNode={selectNode}
              onChangeView={setView}
              focused={focusId !== null && selection?.type === "node" && focusId === selection.id}
              onFocus={() => {
                if (selection?.type === "node") {
                  setFocusId(selection.id);
                }
              }}
              onResetFocus={() => setFocusId(null)}
              collapsed={inspectorCollapsed}
              onToggleCollapsed={() => setInspectorCollapsed((value) => !value)}
            />
          </main>

          <div className="status-bar" aria-label="Analysis status">
            <span>
              Status: <strong>{statusLabel}</strong>
            </span>
            <span className="sep">|</span>
            <span>
              Files: <strong>{result.modelStats.fileCount}</strong>
            </span>
            <span className="sep">|</span>
            <span>
              Modules: <strong>{result.modelStats.moduleCount}</strong>
            </span>
            <span className="sep">|</span>
            <span>
              Symbols: <strong>{result.modelStats.symbolCount}</strong>
            </span>
            <span className="sep">|</span>
            <span>
              Imports: <strong>{result.modelStats.importCount}</strong>
            </span>
            <span className="sep">|</span>
            <span>
              View: <strong>{intelTab ?? view}</strong>
            </span>
            <span className="sep">|</span>
            <span>
              Theme:{" "}
              <strong>
                {themePreference === "auto" ? `auto/${resolvedTheme}` : resolvedTheme}
              </strong>
            </span>
          </div>
        </div>
      ) : null}

      {surface === "workspace" && tutorialOpen ? (
        <TutorialCard stepIndex={tutorialStep} onNext={advanceTutorial} onSkip={skipTutorial} />
      ) : null}

      <SiteFooter />
    </div>
  );
}

function browserStorage(): Storage | null {
  try {
    return window.localStorage;
  } catch {
    return null;
  }
}

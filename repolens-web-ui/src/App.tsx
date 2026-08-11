import { FormEvent, useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import type { CSSProperties } from "react";
import {
  type AnalysisResponse,
  type JobStatus,
  oversizedSkipWarning,
  startAnalysis,
  waitForResult,
} from "./api";
import { DetailsPanel } from "./DetailsPanel";
import { ExplorerPanel } from "./ExplorerPanel";
import {
  kindMeta,
  searchNodes,
  type GraphViewMode,
  type Selection,
} from "./graphModel";
import { RepositoryGraph } from "./RepositoryGraph";
import {
  applyResolvedTheme,
  readThemePreference,
  resolveTheme,
  writeThemePreference,
  type ResolvedTheme,
  type ThemePreference,
} from "./theme";

type Phase = "landing" | "running" | "ready" | "error";

const REPO_URL = "https://github.com/chandru2002-2/repolens";
const AUTHOR_URL = "https://github.com/chandru2002-2";

function Brand() {
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

export default function App() {
  const [source, setSource] = useState("https://github.com/octocat/Hello-World");
  const [phase, setPhase] = useState<Phase>("landing");
  const [status, setStatus] = useState<JobStatus | null>(null);
  const [result, setResult] = useState<AnalysisResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [selection, setSelection] = useState<Selection>(null);
  const [focusId, setFocusId] = useState<string | null>(null);
  const [view, setView] = useState<GraphViewMode>("architecture");
  const [query, setQuery] = useState("");
  const [searchOpen, setSearchOpen] = useState(false);
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

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setResult(null);
    setSelection(null);
    setFocusId(null);
    setQuery("");
    setView("architecture");
    setPhase("running");
    try {
      const job = await startAnalysis(source);
      setStatus(job);
      const analysis = await waitForResult(job.id, setStatus);
      setResult(analysis);
      setPhase("ready");
    } catch (err) {
      setPhase("error");
      setError(err instanceof Error ? err.message : "Analysis failed");
    }
  }

  function selectNode(id: string) {
    setSelection({ type: "node", id });
    const node = result?.graph.nodes.find((item) => item.id === id);
    if (node && ["class", "interface", "enum", "type", "function"].includes(node.kind)) {
      if (view !== "symbols") {
        setView("symbols");
      }
    }
  }

  function applySearchHit(id: string) {
    selectNode(id);
    setFocusId(id);
    setQuery("");
    setSearchOpen(false);
  }

  const exploring = phase === "ready" && result;
  const statusLabel =
    phase === "running"
      ? status?.status ?? "RUNNING"
      : phase === "error"
        ? "ERROR"
        : exploring
          ? "READY"
          : "IDLE";

  return (
    <div className={exploring ? "app exploring" : "app"}>
      <header className="topbar">
        <div className="topbar-left">
          <Brand />
          <ThemeToggle preference={themePreference} onChange={setThemePreference} />
        </div>

        {exploring ? (
          <div className="topbar-tools">
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
            <button
              className="tool-button"
              type="button"
              onClick={() => {
                setPhase("landing");
                setResult(null);
                setStatus(null);
                setError(null);
                setSelection(null);
                setFocusId(null);
              }}
            >
              New
            </button>
          </div>
        ) : null}
      </header>

      {!exploring ? (
        <main className="hero">
          <h1 className="brand-hero">
            REPO<span className="slash">//</span>LENS
          </h1>
          <p className="hero-sub">Repository intelligence tool</p>

          <div className="hero-intro">
            <p>
              RepoLens is a repository intelligence tool that helps developers understand
              unfamiliar codebases through static analysis.
            </p>
            <p>
              Explore repository architecture, package dependencies, symbols, and
              relationships through an interactive visual interface.
            </p>
          </div>

          <p className="hero-github">
            <a href={REPO_URL} target="_blank" rel="noopener noreferrer">
              View on GitHub ↗
            </a>
          </p>

          <hr className="rule" />

          <form className="analyze-form" onSubmit={onSubmit}>
            <label className="field-label" htmlFor="source">
              Repository URL
            </label>
            <div className="input-row">
              <span className="prompt" aria-hidden="true">
                &gt;
              </span>
              <input
                id="source"
                name="source"
                value={source}
                onChange={(event) => setSource(event.target.value)}
                placeholder="https://github.com/owner/repo"
                autoComplete="off"
                required
              />
            </div>
            <hr className="rule" />
            <div className="analyze-actions">
              <button type="submit" disabled={phase === "running"}>
                {phase === "running" ? "Analyzing…" : "Analyze"}
              </button>
            </div>
          </form>

          <hr className="rule" />
          <p className="hero-foot">Structure · Dependencies · Symbols</p>
          <p className="hint">
            {phase === "running" && status
              ? `Job ${status.status.toLowerCase()}…`
              : "Local paths and public GitHub HTTPS URLs are supported."}
          </p>
          {error ? <p className="error">{error}</p> : null}
        </main>
      ) : (
        <div className="workspace">
          <main className="explorer">
            <ExplorerPanel
              nodes={result.graph.nodes}
              edges={result.graph.edges}
              selectedId={selection?.type === "node" ? selection.id : null}
              onSelectNode={selectNode}
              repoName={result.repository.name}
              repoSource={result.repository.source}
              skipWarning={oversizedSkipWarning(result)}
            />

            <section className="graph-stage">
              <div className="view-tabs" role="tablist" aria-label="Graph views">
                {(
                  [
                    ["architecture", "Architecture"],
                    ["packages", "Packages"],
                    ["symbols", "Symbols"],
                  ] as const
                ).map(([id, label]) => (
                  <button
                    key={id}
                    type="button"
                    role="tab"
                    aria-selected={view === id}
                    className={view === id ? "view-tab active" : "view-tab"}
                    onClick={() => {
                      setView(id);
                      setFocusId(null);
                    }}
                  >
                    {label}
                  </button>
                ))}
              </div>
              <RepositoryGraph
                nodes={result.graph.nodes}
                edges={result.graph.edges}
                view={view}
                selection={selection}
                focusId={focusId}
                onSelect={setSelection}
                theme={resolvedTheme}
              />
            </section>

            <DetailsPanel
              nodes={result.graph.nodes}
              edges={result.graph.edges}
              selection={selection}
              onSelectNode={selectNode}
              focused={focusId !== null && selection?.type === "node" && focusId === selection.id}
              onFocus={() => {
                if (selection?.type === "node") {
                  setFocusId(selection.id);
                }
              }}
              onResetFocus={() => setFocusId(null)}
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
              View: <strong>{view}</strong>
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
      )}

      <SiteFooter />
    </div>
  );
}

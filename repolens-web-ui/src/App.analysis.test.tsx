/** @vitest-environment jsdom */
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import App, { AnalysisScreen } from "./App";
import { AnalysisFailedError, type AnalysisResponse, type JobStatus } from "./api";

const { startAnalysis, waitForResult } = vi.hoisted(() => ({
  startAnalysis: vi.fn(),
  waitForResult: vi.fn(),
}));

vi.mock("./RepositoryGraph", () => ({
  RepositoryGraph: () => null,
}));

vi.mock("./api", async () => {
  const actual = await vi.importActual<typeof import("./api")>("./api");
  return {
    ...actual,
    startAnalysis,
    waitForResult,
  };
});

const URL_A = "https://github.com/octocat/Hello-World";
const URL_B = "https://github.com/octocat/Spoon-Knife";

function job(id: string, status: JobStatus["status"], source: string): JobStatus {
  return { id, status, source, remote: true, error: null };
}

function analysis(name: string, source: string): AnalysisResponse {
  return {
    schemaVersion: "1",
    repository: { id: name, name, origin: "remote", source },
    modelStats: { fileCount: 4, moduleCount: 1, symbolCount: 2, importCount: 0, relationshipCount: 0 },
    results: [],
    graph: { id: "g", nodes: [], edges: [] },
  };
}

function installMatchMedia() {
  Object.defineProperty(window, "matchMedia", {
    writable: true,
    configurable: true,
    value: (query: string) => ({
      matches: false,
      media: query,
      addEventListener() {},
      removeEventListener() {},
      addListener() {},
      removeListener() {},
      dispatchEvent() {
        return false;
      },
    }),
  });
}

beforeEach(() => {
  localStorage.clear();
  installMatchMedia();
  startAnalysis.mockReset();
  waitForResult.mockReset();
  waitForResult.mockReturnValue(new Promise(() => {}));
  startAnalysis.mockImplementation(async (source: string) => job("job-1", "QUEUED", source));
});

afterEach(() => {
  cleanup();
});

function typeSource(value: string) {
  fireEvent.change(screen.getByLabelText("GitHub repository URL"), {
    target: { value },
  });
}

describe("landing analysis", () => {
  it("rejects empty input", () => {
    render(<App />);
    fireEvent.click(screen.getByRole("button", { name: "Analyze", exact: true }));
    expect(screen.getByText("Enter a GitHub repository URL.")).toBeTruthy();
    expect(startAnalysis).not.toHaveBeenCalled();
    expect(screen.getByRole("heading", { name: "Analyze a repository" })).toBeTruthy();
    expect(screen.getByRole("heading", { name: "Understand unfamiliar codebases" })).toBeTruthy();
    expect(screen.getByText(/architecture, dependencies, REST APIs, database entities/)).toBeTruthy();
  });

  it("rejects an invalid URL", () => {
    render(<App />);
    typeSource("https://gitlab.com/acme/demo");
    fireEvent.click(screen.getByRole("button", { name: "Analyze", exact: true }));
    expect(screen.getByText("Only https://github.com/owner/repo URLs are supported.")).toBeTruthy();
    expect(startAnalysis).not.toHaveBeenCalled();
  });

  it("starts analysis when the form is submitted with Enter", async () => {
    render(<App />);
    typeSource(URL_A);
    fireEvent.submit(screen.getByRole("form", { name: "Analyze a repository" }));
    expect(await screen.findByRole("heading", { name: "Preparing repository analysis" })).toBeTruthy();
    expect(startAnalysis).toHaveBeenCalledWith(URL_A, expect.any(AbortSignal));
  });

  it("starts the same analysis when Analyze is clicked", async () => {
    render(<App />);
    typeSource(URL_A);
    fireEvent.click(screen.getByRole("button", { name: "Analyze", exact: true }));
    expect(await screen.findByRole("heading", { name: "Preparing repository analysis" })).toBeTruthy();
    expect(startAnalysis).toHaveBeenCalledWith(URL_A, expect.any(AbortSignal));
  });

  it("transitions to the running analysis screen", async () => {
    let resolveStart: (value: JobStatus) => void = () => {};
    startAnalysis.mockReturnValue(
      new Promise<JobStatus>((resolve) => {
        resolveStart = resolve;
      }),
    );
    render(<App />);
    typeSource(URL_A);
    fireEvent.click(screen.getByRole("button", { name: "Analyze", exact: true }));
    expect(screen.getByRole("heading", { name: "Preparing repository analysis" })).toBeTruthy();
    expect(screen.queryByRole("heading", { name: "Analyze a repository" })).toBeNull();
    resolveStart(job("job-1", "QUEUED", URL_A));
    expect(await screen.findByText("Status: QUEUED")).toBeTruthy();
  });

  it("shows the QUEUED job status and the submitted URL", async () => {
    render(<App />);
    typeSource(URL_A);
    fireEvent.click(screen.getByRole("button", { name: "Analyze", exact: true }));
    expect(await screen.findByText("Status: QUEUED")).toBeTruthy();
    expect(screen.getByRole("heading", { name: "Preparing repository analysis" })).toBeTruthy();
    expect(screen.getByText("RepoLens is waiting for the analysis worker to begin.")).toBeTruthy();
    expect(screen.getByText(URL_A)).toBeTruthy();
    expect(screen.queryByText("Files")).toBeNull();
    const progress = screen.getByRole("progressbar");
    expect(progress.getAttribute("aria-valuenow")).toBeNull();
    expect(progress.getAttribute("aria-valuetext")).toBe("Detailed progress unavailable");
  });

  it("shows the RUNNING job status", async () => {
    let publish: (status: JobStatus) => void = () => {};
    waitForResult.mockImplementation((_id: string, onStatus?: (status: JobStatus) => void) => {
      publish = onStatus ?? (() => {});
      return new Promise(() => {});
    });
    render(<App />);
    typeSource(URL_A);
    fireEvent.click(screen.getByRole("button", { name: "Analyze", exact: true }));
    expect(await screen.findByText("Status: QUEUED")).toBeTruthy();
    publish(job("job-1", "RUNNING", URL_A));
    expect(await screen.findByText("Status: RUNNING")).toBeTruthy();
    expect(screen.getByRole("heading", { name: "Analyzing repository" })).toBeTruthy();
    expect(
      screen.getByText("RepoLens is analyzing the repository structure and building its repository model."),
    ).toBeTruthy();
    expect(screen.queryByText("Building relationships")).toBeNull();
    expect(screen.queryByText("Detecting tests")).toBeNull();
  });

  it("opens the workspace when the job COMPLETED", async () => {
    waitForResult.mockResolvedValue(analysis("Hello-World", URL_A));
    render(<App />);
    typeSource(URL_A);
    fireEvent.click(screen.getByRole("button", { name: "Analyze", exact: true }));
    expect(await screen.findByText("Hello-World")).toBeTruthy();
    expect(screen.getByText(/Files:/)).toBeTruthy();
    expect(screen.getByText("4")).toBeTruthy();
  });

  it("shows the FAILED job reason and can retry or change repository", async () => {
    waitForResult.mockRejectedValueOnce(new AnalysisFailedError("repository not found"));
    render(<App />);
    typeSource(URL_A);
    fireEvent.click(screen.getByRole("button", { name: "Analyze", exact: true }));
    expect(await screen.findByRole("heading", { name: "Analysis failed" })).toBeTruthy();
    expect(screen.getByText("repository not found")).toBeTruthy();
    expect(screen.queryByText(/at GitRemote/)).toBeNull();

    waitForResult.mockReturnValue(new Promise(() => {}));
    fireEvent.click(screen.getByRole("button", { name: "Try again" }));
    expect(await screen.findByRole("heading", { name: "Preparing repository analysis" })).toBeTruthy();
    expect(startAnalysis).toHaveBeenCalledTimes(2);
    expect(screen.queryByText("repository not found")).toBeNull();
  });

  it("says when a failed job has no reason and can change repository", async () => {
    waitForResult.mockRejectedValueOnce(new AnalysisFailedError(null));
    render(<App />);
    typeSource(URL_A);
    fireEvent.click(screen.getByRole("button", { name: "Analyze", exact: true }));
    expect(await screen.findByText("The analysis job failed without a detailed reason.")).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "Change repository" }));
    expect(screen.getByRole("heading", { name: "Analyze a repository" })).toBeTruthy();
    expect((screen.getByLabelText("GitHub repository URL") as HTMLInputElement).value).toBe(URL_A);
  });

  it("clears a stale result when a new analysis starts", async () => {
    waitForResult.mockResolvedValueOnce(analysis("First Repo", URL_A));
    render(<App />);
    typeSource(URL_A);
    fireEvent.click(screen.getByRole("button", { name: "Analyze", exact: true }));
    expect(await screen.findByText("First Repo")).toBeTruthy();

    waitForResult.mockReturnValue(new Promise(() => {}));
    fireEvent.click(screen.getByRole("button", { name: "New" }));
    typeSource(URL_B);
    fireEvent.click(screen.getByRole("button", { name: "Analyze", exact: true }));
    expect(await screen.findByRole("heading", { name: "Preparing repository analysis" })).toBeTruthy();
    expect(screen.queryByText("First Repo")).toBeNull();
    expect(screen.queryByText(/Files:/)).toBeNull();
  });

  it("keeps the newer analysis when an older job completes later", async () => {
    const resolvers: Array<(value: AnalysisResponse) => void> = [];
    const signals: AbortSignal[] = [];
    waitForResult.mockImplementation(
      (_id: string, _onStatus?: (status: JobStatus) => void, signal?: AbortSignal) => {
        if (signal) {
          signals.push(signal);
        }
        return new Promise<AnalysisResponse>((resolve) => {
          resolvers.push(resolve);
        });
      },
    );
    render(<App />);
    typeSource(URL_A);
    fireEvent.click(screen.getByRole("button", { name: "Analyze", exact: true }));
    expect(await screen.findByText("Status: QUEUED")).toBeTruthy();

    fireEvent.click(screen.getByRole("button", { name: "New" }));
    typeSource(URL_B);
    fireEvent.click(screen.getByRole("button", { name: "Analyze", exact: true }));
    expect(await screen.findByText(URL_B)).toBeTruthy();
    expect(signals[0]?.aborted).toBe(true);
    expect(signals[1]?.aborted).toBe(false);

    resolvers[1](analysis("Spoon-Knife", URL_B));
    expect(await screen.findByText("Spoon-Knife")).toBeTruthy();
    resolvers[0](analysis("Hello-World", URL_A));
    await waitFor(() => {
      expect(screen.queryByText("Hello-World")).toBeNull();
    });
    expect(screen.getByText("Spoon-Knife")).toBeTruthy();
  });
});

async function openWorkspace(name = "Hello-World", source = URL_A) {
  waitForResult.mockResolvedValueOnce(analysis(name, source));
  render(<App />);
  typeSource(source);
  fireEvent.click(screen.getByRole("button", { name: "Analyze", exact: true }));
  expect(await screen.findByText(name)).toBeTruthy();
  expect(screen.getByRole("tablist", { name: "Diagram views" })).toBeTruthy();
}

function submitNext(source: string, via: "analyze" | "enter") {
  fireEvent.click(screen.getByRole("button", { name: "New" }));
  typeSource(source);
  waitForResult.mockReturnValue(new Promise(() => {}));
  if (via === "enter") {
    fireEvent.submit(screen.getByRole("form", { name: "Analyze a repository" }));
  } else {
    fireEvent.click(screen.getByRole("button", { name: "Analyze", exact: true }));
  }
}

function expectDedicatedAnalysis(previousName: string, nextSource: string) {
  expect(screen.getByRole("heading", { name: "Preparing repository analysis" })).toBeTruthy();
  expect(screen.getByText(nextSource)).toBeTruthy();
  expect(screen.queryByText(/Elapsed 0:00/)).toBeTruthy();
  expect(document.querySelector("[data-screen='workspace']")).toBeNull();
  expect(document.querySelector("[data-screen='analysis']")).toBeTruthy();
  expect(screen.queryByRole("tablist", { name: "Diagram views" })).toBeNull();
  expect(screen.queryByText(/Files:/)).toBeNull();
  expect(screen.queryByText(previousName)).toBeNull();
  expect(screen.queryByText("No endpoints in this analysis result. Endpoints appear only when the API returns endpoint facts.")).toBeNull();
}

describe("analysis screen on every submission", () => {
  it.each([
    ["architecture graph", "Architecture"],
    ["endpoints", "Endpoints"],
    ["tests", "Tests"],
    ["traces", "Traces"],
  ] as const)("replaces the %s workspace when Analyze is clicked", async (_label, tab) => {
    await openWorkspace();
    fireEvent.click(screen.getByRole("tab", { name: tab }));
    expect(screen.getByRole("tab", { name: tab })).toHaveProperty("ariaSelected", "true");
    submitNext(URL_B, "analyze");
    expect(await screen.findByRole("heading", { name: "Preparing repository analysis" })).toBeTruthy();
    expectDedicatedAnalysis("Hello-World", URL_B);
  });

  it("replaces another diagram when the same repository is submitted with Enter", async () => {
    await openWorkspace();
    fireEvent.change(screen.getByRole("combobox", { name: "Additional diagrams" }), {
      target: { value: "sequence" },
    });
    expect(screen.getByText("No diagram data available for this view.")).toBeTruthy();
    submitNext(URL_A, "enter");
    expect(await screen.findByRole("heading", { name: "Preparing repository analysis" })).toBeTruthy();
    expectDedicatedAnalysis("Hello-World", URL_A);
    expect(screen.queryByText("No diagram data available for this view.")).toBeNull();
  });

  it("opens the analysis screen after Change repository", async () => {
    waitForResult.mockRejectedValueOnce(new AnalysisFailedError("repository not found"));
    render(<App />);
    typeSource(URL_A);
    fireEvent.click(screen.getByRole("button", { name: "Analyze", exact: true }));
    expect(await screen.findByRole("heading", { name: "Analysis failed" })).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "Change repository" }));
    typeSource(URL_B);
    waitForResult.mockReturnValue(new Promise(() => {}));
    fireEvent.click(screen.getByRole("button", { name: "Analyze", exact: true }));
    expect(await screen.findByRole("heading", { name: "Preparing repository analysis" })).toBeTruthy();
    expect(screen.getByText(URL_B)).toBeTruthy();
    expect(screen.queryByText("repository not found")).toBeNull();
    expect(document.querySelector("[data-screen='workspace']")).toBeNull();
  });
});

describe("analysis screen", () => {
  it("shows the long-running notice without a percentage", () => {
    render(
      <AnalysisScreen
        status="RUNNING"
        elapsedMs={20_000}
        facts={[{ label: "URL", value: URL_A }]}
      />,
    );
    expect(screen.getByText("Analysis is taking longer than usual.")).toBeTruthy();
    expect(
      screen.getByText(
        "RepoLens is still processing the repository. Larger repositories may require more time.",
      ),
    ).toBeTruthy();
    expect(screen.getByRole("progressbar").getAttribute("aria-valuenow")).toBeNull();
    expect(screen.queryByText(/%/)).toBeNull();
  });
});

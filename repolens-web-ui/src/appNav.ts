/** UI phase machine helpers — no routing library; App owns the state. */

export type AppPhase = "landing" | "compose" | "running" | "ready" | "error";

/** After "New", open the creation flow (not the marketing landing). */
export function phaseForNewLens(): AppPhase {
  return "compose";
}

export function showsCreationForm(phase: AppPhase): boolean {
  return phase === "landing" || phase === "compose" || phase === "running" || phase === "error";
}

export function isMarketingLanding(phase: AppPhase): boolean {
  return phase === "landing";
}

export function isExploring(phase: AppPhase): boolean {
  return phase === "ready";
}

export function showNewLensControl(phase: AppPhase): boolean {
  return phase === "ready" || phase === "running";
}

export function explorerGridClass(
  explorerCollapsed: boolean,
  inspectorCollapsed: boolean,
): string {
  const classes = ["explorer"];
  if (explorerCollapsed) {
    classes.push("explorer-collapsed");
  }
  if (inspectorCollapsed) {
    classes.push("inspector-collapsed");
  }
  return classes.join(" ");
}

export function analyzeHint(
  phase: AppPhase,
  status: { status: string } | null,
): string {
  if (phase === "running") {
    const current = status?.status?.toUpperCase();
    if (current && current !== "FAILED" && current !== "ERROR") {
      return `Job ${status!.status.toLowerCase()}…`;
    }
    return "Analyzing…";
  }
  return "Local paths and public GitHub HTTPS URLs are supported.";
}

/** Clear a previous submit error when the user edits the repository input. */
export function shouldClearSubmitError(phase: AppPhase): boolean {
  return phase !== "running";
}

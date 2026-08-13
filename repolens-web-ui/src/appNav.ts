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

import { describe, expect, it } from "vitest";
import {
  explorerGridClass,
  isExploring,
  isMarketingLanding,
  phaseForNewLens,
  showNewLensControl,
  showsCreationForm,
} from "./appNav";

describe("appNav", () => {
  it("New lens opens compose creation flow, not marketing landing", () => {
    expect(phaseForNewLens()).toBe("compose");
    expect(isMarketingLanding(phaseForNewLens())).toBe(false);
    expect(showsCreationForm(phaseForNewLens())).toBe(true);
  });

  it("exposes New while analyzing or exploring", () => {
    expect(showNewLensControl("running")).toBe(true);
    expect(showNewLensControl("ready")).toBe(true);
    expect(showNewLensControl("landing")).toBe(false);
    expect(showNewLensControl("compose")).toBe(false);
  });

  it("exploring is only ready phase", () => {
    expect(isExploring("ready")).toBe(true);
    expect(isExploring("compose")).toBe(false);
  });

  it("collapsed panels use grid classes that free graph space", () => {
    expect(explorerGridClass(false, false)).toBe("explorer");
    expect(explorerGridClass(true, false)).toBe("explorer explorer-collapsed");
    expect(explorerGridClass(false, true)).toBe("explorer inspector-collapsed");
    expect(explorerGridClass(true, true)).toBe(
      "explorer explorer-collapsed inspector-collapsed",
    );
  });
});

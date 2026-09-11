import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

const css = readFileSync(
  resolve(dirname(fileURLToPath(import.meta.url)), "styles.css"),
  "utf8",
);

describe("stacked workspace layout", () => {
  it("keeps explorer, graph, and inspector in disjoint grid areas", () => {
    expect(css).toContain('grid-template-areas: "explorer graph inspector"');
    const stacked = css.split("@media (max-width: 1100px)")[1] ?? "";
    expect(stacked).toContain('"explorer"');
    expect(stacked).toContain('"graph"');
    expect(stacked).toContain('"inspector"');
    expect(css).toMatch(/\.graph-stage[\s\S]*?overflow:\s*hidden/);
    expect(css).toMatch(/\.graph-shell[\s\S]*?contain:\s*layout paint/);
    expect(css).toMatch(/\.explorer-panel[\s\S]*?grid-area:\s*explorer/);
  });
});

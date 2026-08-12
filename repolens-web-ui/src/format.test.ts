import { describe, expect, it } from "vitest";
import {
  buildMetadataRows,
  formatAbsoluteDate,
  formatBytes,
  formatRelativeTime,
} from "./format";

describe("formatBytes", () => {
  it("formats bytes kb mb gb", () => {
    expect(formatBytes(512)).toBe("512 B");
    expect(formatBytes(2048)).toBe("2 KB");
    expect(formatBytes(18.4 * 1024 * 1024)).toBe("18.4 MB");
    expect(formatBytes(3 * 1024 * 1024 * 1024)).toBe("3 GB");
  });

  it("returns null for missing values", () => {
    expect(formatBytes(null)).toBeNull();
    expect(formatBytes(undefined)).toBeNull();
  });
});

describe("date formatting", () => {
  it("formats absolute dates", () => {
    expect(formatAbsoluteDate("2026-01-14T10:00:00Z")).toContain("2026");
    expect(formatAbsoluteDate("not-a-date")).toBeNull();
  });

  it("formats relative times for recent commits", () => {
    const now = Date.parse("2026-08-11T07:00:00Z");
    expect(formatRelativeTime("2026-08-11T05:00:00Z", now)).toBe("2 hours ago");
  });
});

describe("metadata rendering", () => {
  it("builds compact rows including first-commit label", () => {
    const rows = buildMetadataRows(
      {
        name: "RepoLens",
        owner: "Chandru M",
        sizeBytes: 18.4 * 1024 * 1024,
        firstCommitAt: "2026-01-14T10:00:00Z",
        defaultBranch: "main",
        lastCommit: {
          message: "Add documentation-aware inspection\n\nDetails",
          author: "Chandru M",
          authoredAt: "2026-08-11T05:00:00Z",
        },
      },
      "fallback",
      Date.parse("2026-08-11T07:00:00Z"),
    );
    expect(rows.find((row) => row.label === "Repository")?.value).toBe("RepoLens");
    expect(rows.find((row) => row.label === "Owner")?.value).toBe("Chandru M");
    expect(rows.find((row) => row.label === "Size")?.value).toBe("18.4 MB");
    expect(rows.find((row) => row.label === "First commit")).toBeTruthy();
    expect(rows.find((row) => row.label === "Created")).toBeFalsy();
    expect(rows.find((row) => row.label === "Last commit")?.value).toBe("2 hours ago");
    expect(rows.find((row) => row.label === "Commit message")?.value).toBe(
      "Add documentation-aware inspection",
    );
  });

  it("omits rows when metadata is missing", () => {
    expect(buildMetadataRows(null)).toEqual([]);
    expect(buildMetadataRows({})).toEqual([]);
    expect(buildMetadataRows({ name: "Only" })[0]?.value).toBe("Only");
  });
});

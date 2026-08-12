/** Display helpers for repository metadata. */

export function formatBytes(sizeBytes: number | null | undefined): string | null {
  if (sizeBytes == null || !Number.isFinite(sizeBytes) || sizeBytes < 0) {
    return null;
  }
  if (sizeBytes < 1024) {
    return `${Math.round(sizeBytes)} B`;
  }
  const kb = sizeBytes / 1024;
  if (kb < 1024) {
    return `${trimNumber(kb)} KB`;
  }
  const mb = kb / 1024;
  if (mb < 1024) {
    return `${trimNumber(mb)} MB`;
  }
  const gb = mb / 1024;
  return `${trimNumber(gb)} GB`;
}

function trimNumber(value: number): string {
  if (value >= 100) {
    return String(Math.round(value));
  }
  if (value >= 10) {
    return value.toFixed(1).replace(/\.0$/, "");
  }
  return value.toFixed(1).replace(/\.0$/, "");
}

export function formatAbsoluteDate(iso: string | null | undefined): string | null {
  if (!iso) {
    return null;
  }
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return null;
  }
  return new Intl.DateTimeFormat("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  }).format(date);
}

export function formatRelativeTime(
  iso: string | null | undefined,
  nowMs: number = Date.now(),
): string | null {
  if (!iso) {
    return null;
  }
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return null;
  }
  const deltaSec = Math.round((date.getTime() - nowMs) / 1000);
  const abs = Math.abs(deltaSec);
  const rtf = new Intl.RelativeTimeFormat("en", { numeric: "auto" });
  if (abs < 60) {
    return rtf.format(deltaSec, "second");
  }
  const deltaMin = Math.round(deltaSec / 60);
  if (Math.abs(deltaMin) < 60) {
    return rtf.format(deltaMin, "minute");
  }
  const deltaHour = Math.round(deltaMin / 60);
  if (Math.abs(deltaHour) < 48) {
    return rtf.format(deltaHour, "hour");
  }
  const deltaDay = Math.round(deltaHour / 24);
  if (Math.abs(deltaDay) < 60) {
    return rtf.format(deltaDay, "day");
  }
  return formatAbsoluteDate(iso);
}

export type RepositoryMetadataView = {
  name?: string | null;
  owner?: string | null;
  sizeBytes?: number | null;
  createdAt?: string | null;
  firstCommitAt?: string | null;
  defaultBranch?: string | null;
  lastCommit?: {
    sha?: string | null;
    message?: string | null;
    author?: string | null;
    authoredAt?: string | null;
    committedAt?: string | null;
  } | null;
  commitCount?: number | null;
};

export type MetadataRow = {
  label: string;
  value: string;
  title?: string;
  emphasis?: boolean;
};

export function buildMetadataRows(
  metadata: RepositoryMetadataView | null | undefined,
  fallbackName?: string,
  nowMs: number = Date.now(),
): MetadataRow[] {
  if (!metadata) {
    return [];
  }
  const rows: MetadataRow[] = [];
  const name = metadata.name ?? fallbackName;
  if (name) {
    rows.push({ label: "Repository", value: name });
  }
  if (metadata.owner) {
    rows.push({ label: "Owner", value: metadata.owner });
  }
  const size = formatBytes(metadata.sizeBytes ?? null);
  if (size) {
    rows.push({ label: "Size", value: size });
  }
  if (metadata.createdAt) {
    const absolute = formatAbsoluteDate(metadata.createdAt);
    if (absolute) {
      rows.push({ label: "Created", value: absolute, title: metadata.createdAt });
    }
  } else if (metadata.firstCommitAt) {
    const absolute = formatAbsoluteDate(metadata.firstCommitAt);
    if (absolute) {
      rows.push({
        label: "First commit",
        value: absolute,
        title: metadata.firstCommitAt,
      });
    }
  }
  if (metadata.defaultBranch) {
    rows.push({ label: "Branch", value: metadata.defaultBranch });
  }
  const commit = metadata.lastCommit;
  if (commit) {
    const whenIso = commit.authoredAt ?? commit.committedAt ?? null;
    const relative = formatRelativeTime(whenIso, nowMs);
    const absolute = formatAbsoluteDate(whenIso);
    if (relative) {
      rows.push({
        label: "Last commit",
        value: relative,
        title: absolute ? `${absolute}${whenIso ? ` (${whenIso})` : ""}` : whenIso ?? undefined,
      });
    }
    if (commit.message) {
      rows.push({
        label: "Commit message",
        value: firstLine(commit.message),
        emphasis: true,
        title: commit.message,
      });
    }
    if (commit.author) {
      rows.push({ label: "Commit author", value: commit.author });
    }
  }
  return rows;
}

function firstLine(message: string): string {
  return message.split(/\r?\n/, 1)[0]?.trim() || message.trim();
}

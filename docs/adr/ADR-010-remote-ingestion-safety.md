# ADR-010: Remote Repository Ingestion Trust and Safety Limits

- Status: Accepted
- Date: 2026-08-10

## Context

Web users will paste remote repository URLs. Unbounded clones create DoS, disk, and secret-exfiltration risks.

## Decision

When remote ingestion is implemented, enforce:

- allowlisted protocols/hosts (initially HTTPS GitHub)
- max repository size / file count / timeout budgets
- analysis workspace isolation under a controlled temp/cache directory
- no execution of repository build scripts during analysis
- redact/avoid persisting secrets discovered in artifacts by default

Exact numeric limits will be configured at implementation time.

## Consequences

- Safer default hosted/self-hosted behavior
- Some very large monorepos may require explicit override flags
- Ingestion becomes a security-sensitive module
- Remote clones are cached under `~/.repolens/cache/remotes/{owner}/{repo}` and
  reused whenever `.git` exists. **The cache is not refreshed** (no `fetch`/`pull`).
  Stale or locally modified cached trees can be analyzed until the directory is
  deleted. Treat cache freshness as a future issue, not a v1.6 guarantee.

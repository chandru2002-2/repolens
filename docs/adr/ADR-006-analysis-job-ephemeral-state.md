# ADR-006: Analysis Job Model and Ephemeral State

- Status: Accepted
- Date: 2026-08-10

## Context

Web analysis of remote repositories is asynchronous and may be expensive. Mandatory databases would increase ops cost.

## Decision

For v1:

- Web analysis runs as **jobs** with status polling
- Default state store is **in-memory**, with optional local disk cache later
- No required Redis/Postgres/K8s

CLI continues to run synchronously.

## Consequences

- Easy local runs
- No multi-instance durability until deliberately added
- Job APIs must document ephemeral semantics

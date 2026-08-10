# ADR-011: Optional Tree-sitter with First-Class Structural Fallback

- Status: Accepted
- Date: 2026-08-10

## Context

ADR-003 chose Tree-sitter plus language profiles for multi-language extraction.
Published `ch.usi.si.seart:java-tree-sitter` natives in Maven Central are x86_64-only
(`libjava-tree-sitter.dylib` / `.so`). On Apple Silicon (arm64), JNI load fails with
`UnsatisfiedLinkError` (have x86_64, need arm64). RepoLens already probes the engine and
uses structural fallback extractors that emit the same capture names.

Shipping or building multi-arch natives inside RepoLens would add heavy packaging and CI
burden and conflicts with the lightweight modular-monolith stance. Switching bindings
(e.g. tree-sitter-ng) is deferred to a separate, explicit milestone.

## Decision

**Option D — Optional Tree-sitter with first-class structural fallback.**

1. Do **not** redesign parsing architecture, `RepositoryModel`, or language-profile contracts.
2. Do **not** vendor, build, or ship multi-arch Tree-sitter natives in RepoLens in this phase.
3. Runtime policy:
   - Prefer Tree-sitter when the native engine probe succeeds.
   - Always keep structural fallback present, tested, and **supported**.
4. On Apple Silicon (and any host where natives fail), **structural fallback is the expected
   supported default** with the current dependency.
5. Tree-sitter is **not** a hard requirement for RepoLens v1 correctness or the analysis pipeline.
6. Defer binding replacement or upstream multi-arch adoption to a later approved milestone;
   any future engine must remain behind `SyntaxQueryEngine` with ADR-009 license audit.

### Runtime engine identifiers

| Condition | Engine id |
|---|---|
| Native Tree-sitter (seart) loads successfully | `tree-sitter-seart` |
| Native load/probe fails | `structural-fallback` |

CLI analyze output reports this as `Parse engine: …`.

## Consequences

- Apple Silicon, Windows, and constrained CI keep working without custom natives.
- No new Gradle native pipeline or large arm64 artifacts in-repo from this ADR.
- AST-query fidelity on arm64 remains below full Tree-sitter until a later milestone.
- Dual extraction paths must preserve capture-name parity (enforced by profile design + tests).
- The seart JAR may still be downloaded even when unused on arm64 (optional dependency
  hygiene is an open follow-up, not part of this ADR’s required changes).

## Rejected for this phase

- **A** Fallback-only as the sole strategy (undersells ADR-003 preference for Tree-sitter).
- **B/C** RepoLens-owned multi-arch / arm64 native packaging (too heavy for v1).
- **E** Immediate switch to another binding (requires a separate implementation approval).

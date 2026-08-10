# ADR-003: Parsing Strategy — Tree-sitter + Language Profiles

- Status: Accepted
- Date: 2026-08-10
- Amended: 2026-08-10 (see [ADR-011](ADR-011-optional-tree-sitter-first-class-fallback.md))

## Context

We need multi-language structural extraction without immediately building a large custom language-plugin ecosystem.

## Decision

Use **Tree-sitter** as the **preferred** parsing engine when native bindings are available,
with thin **language profiles** (grammar selection + queries + normalization) behind the
`SourceAnalyzer` port.

**Structural fallback extractors** (same capture vocabulary as Tree-sitter queries) are a
**first-class, supported** parsing path. They are not a temporary debug hack.

Tree-sitter is **not** a hard requirement for RepoLens v1: the ingest → parse →
`RepositoryModel` → analyzers pipeline must succeed when natives cannot load.

Do **not** start with hand-written per-language plugin parsers outside language profiles.
Do **not** require SCIP/LSP for v1.

Platform expectation with the current `ch.usi.si.seart:java-tree-sitter` dependency:
**Apple Silicon (arm64) is expected to use structural fallback** because published natives
are x86_64-only. See ADR-011.

### Runtime engine status

| Engine id | When |
|---|---|
| `tree-sitter-seart` | Native loading / probe succeeds |
| `structural-fallback` | Native loading / probe fails |

## Consequences

- Broad language reach with moderate effort
- Syntactic (not fully semantic) relationships initially
- Native multi-arch packaging is **not** required for v1 (ADR-011); optional Tree-sitter remains a best-effort accelerator
- Future SCIP enrichment or alternate Tree-sitter bindings remain possible without analyzer rewrites
- Fallback and Tree-sitter paths must stay capture-compatible

## Implementation note

RepoLens integrates Tree-sitter through `ch.usi.si.seart:java-tree-sitter` behind
`SyntaxQueryEngine`. Language profiles always provide `fallbackExtract` for unsupported
hosts. Multi-arch native enablement or binding replacement requires a separate approved
milestone; do not silently pretend Tree-sitter works when the engine id is
`structural-fallback`.

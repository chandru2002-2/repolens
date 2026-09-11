# Project Status

**Date:** 2026-08-13
**Phase:** Interactive Repository Intelligence
**Version:** `1.6.0`

## Current capabilities

- End-to-end local + public GitHub analysis path
- CLI: `ingest`, `analyze [--json] [-o file]`, `serve`
- Web API + packaged Web UI (Cytoscape graph explorer)
- Contributor docs: `CONTRIBUTING.md`, `docs/README.md`, `docs/architecture/REPO_LAYOUT.md`, `./scripts/package-ui.sh`
- Diagram modes: Architecture, Package, Class, Sequence, ER, DFD, Activity, Deployment, Use Case, State Machine
- Client-side graph filtering (kinds, relationship types, name search)
- Deterministic structural facts for specialized diagrams (Java/Spring + config heuristics)
- Focused structural extractors under `repolens-parse/.../structural/` (JPA, Spring, calls, activity, state, deployment, config)
- CALLS relationships carry confidence (`high` typed receiver / static type, `medium` name heuristic); low-confidence guesses are not emitted
- State-machine transitions require assignment/return/transition-call evidence (enum or switch order alone is not enough)
- Deployment service links require Compose `depends_on` evidence (declaration order is not enough)
- Deterministic README / `docs/` documentation extraction and entity matching
- Documentation-aware Inspector excerpts
- Repository metadata (local Git + public GitHub API; non-blocking on failure)
- Core analyzers + GraphView projection (including EXTENDS / IMPLEMENTS when parsed)
- Java structural fallback: fields, methods, extends, implements
- Oversized files skipped (default 5 MB) with non-blocking ingest warnings
- Tree-sitter with structural fallback when natives unavailable
- Language profiles (PARTIAL): Java, JavaScript, TypeScript, Python, Go, Rust, C#, Kotlin
- ADRs 001–011
- Language matrix: `docs/architecture/LANGUAGE_SUPPORT.md`

## Known limitations

- Private GitHub repos / non-GitHub hosts
- Multi-arch bundled Tree-sitter natives
- Persistent job store
- AI features
- Phase B+ languages (C/C++, Swift, PHP, Ruby, …)
- Perfect runtime sequence reconstruction (static inference only)
- Full CFG activity diagrams for arbitrary methods
- Specialized diagrams may be empty when the repository lacks structural signals
- Large graphs are capped by node/edge/fact limits
- Call resolution is heuristic (not a full Java compiler); unresolved receivers yield no CALLS edge
- Manifest ingestion, DETECTED_ONLY language status
- Remote GitHub clone cache is reuse-only: if `~/.repolens/cache/remotes/{owner}/{repo}/.git`
  exists, analysis uses that working tree without fetch/pull. Extra or outdated
  files in the cache are included. Delete the cache directory to re-clone.
- Chromium's accessibility snapshot may mark native graph-filter checkboxes as
  `readonly` even when `readOnly`/`disabled` are false and mouse/keyboard toggle
  works. This is an AX-tree representation quirk, not a non-interactive control.

## Open product decisions

- Project license (MIT vs Apache-2.0)
- Whether to vendor/build multi-arch Tree-sitter natives in-repo
- Hosted vs self-hosted-only product packaging

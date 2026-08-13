# Project Status

**Date:** 2026-08-13
**Phase:** Interactive Repository Intelligence
**Version:** `1.5.0`

## What works

- End-to-end local + public GitHub analysis path
- CLI: `ingest`, `analyze [--json] [-o file]`, `serve`
- Web API + packaged Web UI (Cytoscape graph explorer)
- Contributor docs: `CONTRIBUTING.md`, `docs/README.md`, `docs/architecture/REPO_LAYOUT.md`, `./scripts/package-ui.sh`
- Diagram modes: Architecture, Package, Class, Sequence, ER, DFD, Activity, Deployment, Use Case, State Machine
- Client-side graph filtering (kinds, relationship types, name search)
- Deterministic structural facts for specialized diagrams (Java/Spring + config heuristics)
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

## What does not work yet

- Private GitHub repos / non-GitHub hosts
- Multi-arch bundled Tree-sitter natives
- Persistent job store
- AI features
- Phase B+ languages (C/C++, Swift, PHP, Ruby, …)
- Perfect runtime sequence reconstruction (static inference only)
- Full CFG activity diagrams for arbitrary methods
- Manifest ingestion, DETECTED_ONLY language status

## Open product decisions

- Project license (MIT vs Apache-2.0)
- Whether to vendor/build multi-arch Tree-sitter natives in-repo
- Hosted vs self-hosted-only product packaging

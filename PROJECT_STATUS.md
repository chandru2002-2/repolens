# Project Status

**Date:** 2026-08-11
**Phase:** Interactive Repository Intelligence (v1.2 feature set)
**Version:** `0.1.0-SNAPSHOT` (feature set branded v1.2.0)

## What works

- End-to-end local + public GitHub analysis path
- CLI: `ingest`, `analyze [--json] [-o file]`, `serve`
- Web API + packaged Web UI (Cytoscape graph explorer)
- Diagram modes: Architecture, Package, Class (shared entity ids with Explorer/Inspector)
- Client-side graph filtering (kinds, relationship types, name search)
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
- Sequence / ER / DFD / Activity / Deployment / Use Case / State Machine diagrams
- Manifest ingestion, DETECTED_ONLY language status

## Open product decisions

- Project license (MIT vs Apache-2.0)
- Whether to vendor/build multi-arch Tree-sitter natives in-repo
- Hosted vs self-hosted-only product packaging

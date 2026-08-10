# Project Status

**Date:** 2026-08-10  
**Phase:** Language support Phase A (Go, Rust, C#, Kotlin)  
**Version:** `0.1.0-SNAPSHOT`

## What works

- End-to-end local + public GitHub analysis path
- CLI: `ingest`, `analyze [--json] [-o file]`, `serve`
- Web API + packaged Web UI (Cytoscape graph explorer)
- Core analyzers + GraphView projection
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
- Inheritance edges, manifest ingestion, DETECTED_ONLY status

## Open product decisions

- Project license (MIT vs Apache-2.0)
- Whether to vendor/build multi-arch Tree-sitter natives in-repo
- Hosted vs self-hosted-only product packaging

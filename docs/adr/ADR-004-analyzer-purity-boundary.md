# ADR-004: Analyzer Purity Boundary

- Status: Accepted
- Date: 2026-08-10

## Context

If analyzers parse source directly, language logic spreads and Web/CLI diverge.

## Decision

`Analyzer` implementations **must only** read `RepositoryModel` (and produce `AnalysisResult`).

Forbidden in analyzers:

- reading source files for syntax parsing
- depending on Tree-sitter types
- embedding language grammars/queries

## Consequences

- Testable analyzers with fixture models
- Language changes stay in parse/profiles
- ArchUnit/module deps enforce the boundary

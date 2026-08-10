# ADR-002: RepositoryModel as Central Contract

- Status: Accepted
- Date: 2026-08-10

## Context

RepoLens must support multiple consumers (CLI, Web API, UI, future exporters) without duplicating parsing or leaking language syntax into analyzers/UI.

## Decision

`RepositoryModel` is the **central domain aggregate**.

Pipeline:

```
Source → Ingestion → Parsing → RepositoryModel → Analyzers → AnalysisResult → Adapters
```

Analyzers, CLI, Web API, and UI consume structured model/results only.

## Consequences

- Consistent behavior across interfaces
- Enforced separation of parsing and analysis
- Model evolution requires careful versioning of API DTOs

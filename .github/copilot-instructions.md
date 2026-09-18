# RepoLens Copilot Instructions

You are working in RepoLens, a Java 21 Gradle modular-monolith repository-intelligence platform.

Before coding:
- Read `AGENTS.md` and the relevant ADRs.
- Identify the owning Gradle module and preserve its dependency boundary.
- Inspect existing implementations and tests before introducing new abstractions.

Non-negotiable architecture:
- `RepositoryModel` is the central domain contract.
- Parsing/source-language concerns stay in `repolens-parse`.
- Analyzers consume `RepositoryModel`; they do not parse source or depend on parser internals.
- CLI/Web remain thin adapters.
- External JSON contracts belong in `repolens-api-model`.
- Preserve deterministic structural analysis as the source of truth.
- AI/MCP integrations consume structured RepoLens results and must not replace deterministic analysis.
- Do not introduce Spring Boot, microservices, Kubernetes, or required cloud/database infrastructure without an ADR.

Implementation:
- Prefer small, explicit, testable changes.
- Use Java 21 features appropriately; avoid raw types, unsafe casts, unnecessary reflection, and global mutable state.
- Preserve existing safety limits for remote ingestion.
- Consider time and memory complexity for graph/static-analysis code.
- Never invent benchmark numbers or production-scale claims. Measure first.

Validation:
- Run focused tests for the changed module.
- Run `./gradlew test` for broader changes.
- For UI changes run the existing npm tests/build.
- Run `git diff --check`.
- Update documentation and ADRs when architecture or product behavior changes.

When proposing alternatives, prefer the smallest change that preserves RepoLens' current architecture and invariants.

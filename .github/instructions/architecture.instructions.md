---
applyTo: "**/*.java,**/*.kt,**/*.md"
---

# Architecture Instructions

- Treat `RepositoryModel` as the central contract.
- Keep parsing inside `repolens-parse`.
- Keep analyzers model-only and independent of Tree-sitter/parser implementation details.
- Keep CLI/Web as adapters.
- Keep API DTOs in `repolens-api-model`.
- Preserve the modular-monolith architecture and current dependency direction.
- Read the relevant ADR before changing module boundaries, parser strategy, API contracts, persistence, or external integrations.
- New architectural decisions require an ADR under `docs/adr/`.
- AI features must consume structured RepoLens facts; AI output is explanatory, not the source of truth.
- Prefer extending existing ports/interfaces over introducing parallel architectural paths.

# Repository layout

RepoLens is a **modular monolith monorepo**. The Gradle multi-module split plus a
separate frontend package is intentional (ADR-001). Do not flatten modules or
rewrite adapters into the domain layer.

## Backend (Gradle)

```
settings.gradle.kts
├── repolens-core          Domain model, ports, pipeline contracts
├── repolens-ingest        Local + allowlisted GitHub ingestion
├── repolens-parse         Source → RepositoryModel (language profiles)
├── repolens-analyzers     Model-only analyzers + graph/diagram projection
├── repolens-api-model     Stable v1 JSON DTOs / mappers
├── repolens-cli           CLI adapter
└── repolens-web           Javalin API + packaged static UI
```

Dependency direction (simplified):

```
cli / web  →  api-model, analyzers, parse, ingest, core
analyzers  →  core          (never Tree-sitter / parse internals)
parse      →  core
ingest     →  core
api-model  →  core (+ DTOs for external JSON)
```

Invariants:

1. `RepositoryModel` is the central contract (ADR-002).
2. Analyzers must not parse source (ADR-004).
3. CLI and Web stay thin adapters — no language-specific parsing.

## Frontend

| Path | Role |
|---|---|
| `repolens-web-ui/` | Vite + React + TypeScript **source** (npm package) |
| `repolens-web/src/main/resources/public/` | **Built** static assets served by Javalin |

`repolens-web-ui` is intentionally **not** a Gradle subproject. Packaging is a
copy of `dist/` into `public/` (see `scripts/package-ui.sh` / CONTRIBUTING).

## Docs and tooling

| Path | Role |
|---|---|
| `docs/adr/` | Architecture Decision Records |
| `docs/architecture/` | Living notes (language support, this layout) |
| `AGENTS.md` | Coding-agent / contributor invariants |
| `CONTRIBUTING.md` | Day-to-day developer workflow |
| `.github/workflows/ci.yml` | Backend tests, frontend build, Docker build |

## What we deliberately do not do

- Merge all Java modules into one jar/source tree
- Put UI source inside `repolens-web` as the only editable frontend
- Introduce microservices, required databases, or Spring Boot for v1

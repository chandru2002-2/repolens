# RepoLens Agent Instructions

## Mission

RepoLens is a deterministic repository-intelligence platform. It ingests source repositories, builds a structured `RepositoryModel`, runs analyzers, projects graphs/diagrams, and exposes results through CLI and Web adapters.

Before changing code, inspect the relevant module, ADRs, and existing tests. Preserve the current architecture unless a change is explicitly required.

## Architecture invariants

1. `RepositoryModel` is the central domain contract.
2. `repolens-analyzers` consumes structured model data; analyzers must not parse source.
3. Language-specific parsing belongs in `repolens-parse`.
4. `repolens-cli` and `repolens-web` are thin adapters, not analysis engines.
5. `repolens-api-model` owns stable external JSON DTOs.
6. Keep the modular-monolith boundaries intact. Do not introduce microservices, Kubernetes, or a required database without an ADR.
7. Keep Spring Boot out of the v1 architecture unless an ADR explicitly changes that decision.
8. Deterministic structural analysis remains the source of truth. Optional AI features may explain structured results but must not become the primary parser or invent repository facts.
9. Preserve the Tree-sitter/structural-fallback parsing strategy unless an ADR changes it.

## Engineering standards

- Target Java 21 and the existing Gradle build.
- Prefer simple, explicit designs over speculative abstractions.
- Preserve SOLID boundaries and dependency direction.
- Prefer immutable data, records/value objects where appropriate, explicit types, and composition.
- Avoid raw types, unchecked casts, global mutable state, and reflection when a typed alternative exists.
- Document non-obvious algorithmic complexity and memory implications.
- Do not claim performance improvements without a reproducible benchmark or measurement.
- Security limits around remote ingestion, file sizes, file counts, depth, and binary/media handling are intentional; do not weaken them casually.

## Testing and validation

Run focused tests first, then the full suite when practical:

```bash
./gradlew test
```

For frontend changes:

```bash
cd repolens-web-ui
npm ci
npm test
npm run build
```

Also use `git diff --check` before finishing. Add or update tests for behavior changes.

## Architecture changes

Any new architectural decision, module dependency, external integration, persistence strategy, parser strategy, or major performance trade-off requires an ADR under `docs/adr/`.

When uncertain, read the existing ADRs and `docs/architecture/REPO_LAYOUT.md` before designing a new approach.

## AI / MCP work

AI integrations must consume existing structured RepoLens interfaces such as `RepositoryModel`, `AnalysisResult`, and `GraphView`. Prefer a separate adapter/module for MCP or model-provider integrations rather than coupling protocols to the core domain.

Use narrow, deterministic tools with structured responses instead of a single opaque "analyze everything" tool.

## Definition of done

A change is complete only when the implementation, tests, documentation/ADR requirements, and relevant validation are updated consistently. Keep the README and roadmap honest: never document a feature as implemented until it actually works.

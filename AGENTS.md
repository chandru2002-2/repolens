# AGENTS.md

Guidance for coding agents working on RepoLens.

## Product invariants

1. `RepositoryModel` is the central contract.
2. Analyzers must not parse source or depend on Tree-sitter types.
3. CLI and Web are thin adapters over core; they must not contain language-specific parsing.
4. AI is optional and must consume structured RepoLens data.
5. Prefer a modular monolith and avoid unnecessary infrastructure.
6. Parsing: Tree-sitter is preferred when natives load (`tree-sitter-seart`); structural
   fallback is first-class and supported (`structural-fallback`). Tree-sitter is not a v1
   hard requirement. Apple Silicon is expected to use fallback with the current seart
   dependency (ADR-011). Do not vendor multi-arch natives or swap bindings without an ADR.

## Module map

- Change domain/ports in `repolens-core`
- Ingestion in `repolens-ingest`
- Parsing/language profiles in `repolens-parse`
- Analyzers in `repolens-analyzers`
- External JSON contracts in `repolens-api-model`
- CLI in `repolens-cli`
- Web API + packaged static UI in `repolens-web`
- Web UI **source** in `repolens-web-ui` (npm; package into `repolens-web` via `./scripts/package-ui.sh`)

Do not flatten or merge these modules. Layout rationale: [docs/architecture/REPO_LAYOUT.md](docs/architecture/REPO_LAYOUT.md).

## Before implementing features

- Read relevant ADRs under `docs/adr/`
- Update `PROJECT_STATUS.md` when phase changes
- Keep Spring Boot out of v1 (see ADR-005)
- Do not introduce microservices/K8s/required DBs casually
- Follow [CONTRIBUTING.md](CONTRIBUTING.md) for local workflow

## Verification

```bash
./gradlew test
./gradlew :repolens-cli:run --args='analyze <path>'
./gradlew :repolens-cli:run --args='serve --port 8080'
```

When changing the UI:

```bash
cd repolens-web-ui && npm test && npm run build
./scripts/package-ui.sh   # from repo root, after UI build (script also builds)
```

Architecture boundaries are enforced with ArchUnit in `repolens-cli` tests.

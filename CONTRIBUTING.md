# Contributing to RepoLens

Thanks for contributing. This guide covers local development in the monorepo.
Product architecture and module boundaries are intentional — see
[docs/architecture/REPO_LAYOUT.md](docs/architecture/REPO_LAYOUT.md).

## Prerequisites

- Java 21+
- Git
- Node.js 20+ (only when changing or packaging the Web UI)

## Repository layout (do not flatten)

| Path | Role |
|---|---|
| `repolens-core` … `repolens-web` | Gradle Java modules (domain → adapters) |
| `repolens-web-ui` | Vite/React UI **source** (not a Gradle module) |
| `repolens-web/.../public` | Packaged UI served by the Javalin app |
| `docs/adr/` | Architecture Decision Records |
| `docs/architecture/` | Living architecture notes |

Do **not** merge modules, collapse frontend/backend into single files, or move
language parsing into CLI/Web.

## Architecture invariants

1. `RepositoryModel` is the central contract (ADR-002).
2. Analyzers must not parse source or depend on Tree-sitter types (ADR-004).
3. CLI and Web are thin adapters; they must not contain language-specific parsing.
4. AI is optional and is not shipped in v1.6.0. If added later, it must consume
   structured RepoLens data; it must not discover or parse source (ADR-008).
5. Prefer a modular monolith; do not introduce microservices, Kubernetes, or
   required databases casually (ADR-001, ADR-005).
6. Parsing prefers Tree-sitter when natives load (`tree-sitter-seart`); structural
   fallback is first-class and supported (`structural-fallback`). Tree-sitter is
   not a v1 hard requirement. Apple Silicon is expected to use fallback with the
   current seart dependency (ADR-011). Do not vendor multi-arch natives or swap
   bindings without an ADR.
7. Keep Spring Boot out of v1 (ADR-005).

## Backend

```bash
./gradlew test
./gradlew :repolens-cli:run --args='analyze .'
./gradlew :repolens-cli:run --args='serve --port 8080'
```

Open http://localhost:8080 after `serve`.

Architecture boundaries are enforced with ArchUnit tests in `repolens-cli`.

## Frontend

```bash
cd repolens-web-ui
npm ci          # or: npm install
npm test
npm run build
```

Dev server (API must already be running, typically on port 8080):

```bash
cd repolens-web-ui
npm run dev
```

### Package UI into the web module

After `npm run build`, copy `dist/` into the server’s static assets:

```bash
./scripts/package-ui.sh
```

Or manually:

```bash
cd repolens-web-ui && npm run build
rm -rf ../repolens-web/src/main/resources/public
mkdir -p ../repolens-web/src/main/resources/public
cp -R dist/. ../repolens-web/src/main/resources/public/
```

Commit packaged `public/` assets when the UI change should ship with the Java app.

## Documentation

- Update [PROJECT_STATUS.md](PROJECT_STATUS.md) when the product phase or capability set changes
- New architectural choices need an ADR under `docs/adr/`
- Keep README honest about what works (no invented features)

## Pull requests

- Prefer small, reviewable changes that respect module boundaries
- Run `./gradlew test` and, for UI work, `npm test` + `npm run build`
- Run `git diff --check` before opening a PR
- Do not introduce Spring Boot, required DBs, or microservices casually (ADR-005)

## License

Project license is still TBD (MIT vs Apache-2.0). See
[ADR-009](docs/adr/ADR-009-licensing-grammar-policy.md) for dependency/grammar policy.

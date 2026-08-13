# RepoLens

Open-source **repository intelligence** platform: analyze a codebase into a structured `RepositoryModel`, run analyzers, and explore results through **CLI** and an interactive **Web UI**.

Core analysis is deterministic and works without an LLM.

## What is RepoLens?

RepoLens helps developers understand unfamiliar codebases through static analysis.

It provides interactive views of:

- Repository architecture
- Package dependencies
- Symbols and types
- Relationships between components

## Live Demo

**[Open RepoLens Live Demo →](https://repolens-dsce.onrender.com/)**

## Pipeline

```
GitHub / local repository
        ↓
   Ingestion
        ↓
 Parsing (Tree-sitter when available, else structural fallback)
        ↓
  RepositoryModel
        ↓
    Analyzers
        ↓
 Analysis results / graph
        ↓
   CLI  ·  Web API  ·  UI
```

Parsing prefers Tree-sitter (`Parse engine: tree-sitter-seart`) when natives load; otherwise
uses first-class structural fallback (`Parse engine: structural-fallback`). With the current
dependency, Apple Silicon typically runs fallback. Tree-sitter is not required for v1.
Details: [ADR-003](docs/adr/ADR-003-parsing-tree-sitter-language-profiles.md),
[ADR-011](docs/adr/ADR-011-optional-tree-sitter-first-class-fallback.md).

## Language support

Structural profiles (PARTIAL): Java, JavaScript, TypeScript, Python, Go, Rust, C#, Kotlin.

Honest capability matrix: [docs/architecture/LANGUAGE_SUPPORT.md](docs/architecture/LANGUAGE_SUPPORT.md).

## Status

Web UI MVP is available: paste a public GitHub URL or local path, analyze, explore the architecture graph.

See [PROJECT_STATUS.md](PROJECT_STATUS.md) and [ROADMAP.md](ROADMAP.md).

## Modules

| Module | Role |
|---|---|
| `repolens-core` | Domain model, ports, analysis pipeline |
| `repolens-ingest` | Local + allowlisted GitHub clone ingestion |
| `repolens-parse` | Source → `RepositoryModel` |
| `repolens-analyzers` | Model-only analyzers + graph projection |
| `repolens-api-model` | Stable v1 JSON DTOs |
| `repolens-cli` | CLI adapter |
| `repolens-web` | Javalin API + packaged UI |
| `repolens-web-ui` | Vite/React frontend source (npm; not a Gradle module) |

Layout details: [docs/architecture/REPO_LAYOUT.md](docs/architecture/REPO_LAYOUT.md). Docs index: [docs/README.md](docs/README.md).

## Requirements

- Java 21+
- Git (for remote GitHub clone)
- Node.js 20+ (only to rebuild the UI)

## Interactive Diagrams

### Available

- **Architecture** — repository and package/module layout with package dependencies
- **Package** — package-to-package dependency diagram
- **Class** — classes/interfaces with inheritance (`extends`) / implementation (`implements`) when present
- **Sequence** — inferred controller → service → repository interaction chains (static)
- **ER** — JPA `@Entity` models and association cardinality when annotated
- **DFD** — data-flow sketch from API processes, services, and data stores
- **Activity** — simplified per-method activity summary (calls / decisions / end)
- **Deployment** — Docker Compose / Dockerfile / Kubernetes / datasource hints
- **Use Case** — user-facing operations inferred from REST mappings / controllers
- **State Machine** — enum/status states and only confidently observed transitions

Core views (Architecture / Package / Class) filter the primary graph. Specialized diagrams are separate projections from deterministic structural facts. Empty diagrams show an explanatory message instead of inventing relationships. Large graphs are capped (node/edge limits) with a truncation notice.

Graph filtering (client-side only — no re-analysis):

- Toggle packages, classes, interfaces, enums, methods, and fields (core views)
- Toggle relationship types (`Depends on`, `Extends`, `Implements`, `Contains`)
- Name search across the active diagram

Documentation-aware inspection:

- Deterministic extraction from `README.md` / README files and `docs/**/*.md`
- Inspector shows matched excerpts only when a class, package, or similar entity is explicitly referenced
- Documentation is supplemental context, never the source of truth for structure

Repository information (Explorer):

- Optional metadata such as owner, size, branch, and last commit
- Local repos: size from inventoried analysis files; Git history when available (`First commit` is labeled distinctly from GitHub `Created`)
- Public GitHub repos: GitHub API metadata when reachable (no auth); analysis continues if metadata fetch fails

Large-file handling: files over the default **5 MB** `maxFileBytes` limit are skipped with a non-blocking warning; analysis continues.

## Ingestion safety limits

Local and remote ingest apply ADR-010 safety limits (defaults in `IngestLimits`):

- **maxFileBytes** — 5 MB per file. Oversized files are **skipped** (not fatal); analysis continues and the result includes an `ingest` warning such as “Skipped 1 file exceeding the 5 MB file-size limit.”
- **maxTotalBytes** / **maxFileCount** / **maxDepth** — repository-wide caps (still fail the job if exceeded)
- Common binary/media extensions under the size limit are omitted from the analysis inventory so they do not enter source parsing

## Quick start

```bash
./gradlew test
./gradlew :repolens-cli:run --args='serve --port 8080'
```

Open [http://localhost:8080](http://localhost:8080).

```bash
./gradlew :repolens-cli:run --args='analyze .'
./gradlew :repolens-cli:run --args='analyze . --json'
```

### Rebuild UI

```bash
./scripts/package-ui.sh
```

Or manually:

```bash
cd repolens-web-ui && npm install && npm test && npm run build
rm -rf ../repolens-web/src/main/resources/public
mkdir -p ../repolens-web/src/main/resources/public
cp -R dist/. ../repolens-web/src/main/resources/public/
```

See [CONTRIBUTING.md](CONTRIBUTING.md) for the full developer workflow.

## Continuous Integration

GitHub Actions (`.github/workflows/ci.yml`) runs on pushes and pull requests to `main`. It automatically:

- runs backend tests with `./gradlew test` (Java 21)
- installs, tests, and builds the frontend in `repolens-web-ui` (`npm ci` / `npm test` / `npm run build`)
- validates that the existing `Dockerfile` builds successfully (image is not pushed)

## Architecture decisions

See [docs/](docs/README.md) and [docs/adr/](docs/adr/).

## License

Project license TBD (MIT vs Apache-2.0). Dependency/grammar policy: [ADR-009](docs/adr/ADR-009-licensing-grammar-policy.md).

## Author

**Chandru M**

Java Backend Developer · Spring Boot · Repository Intelligence

GitHub: https://github.com/chandru2002-2

© 2026 Chandru M · RepoLens

# RepoLens

Open-source **repository intelligence** platform: analyze a codebase into a structured `RepositoryModel`, run analyzers, and explore results through **CLI** and an interactive **Web UI**.

RepoLens is inspired by the simplicity of tools like GitDiagram, but is **not** an AI-diagram clone. Core analysis is deterministic and works without an LLM.

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
| `repolens-web-ui` | Vite/React frontend source |

## Requirements

- Java 21+
- Git (for remote GitHub clone)
- Node.js 20+ (only to rebuild the UI)

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
cd repolens-web-ui && npm install && npm run build
rm -rf ../repolens-web/src/main/resources/public
mkdir -p ../repolens-web/src/main/resources/public
cp -R dist/. ../repolens-web/src/main/resources/public/
```

## Architecture decisions

See [docs/adr/](docs/adr/).

## License

Project license TBD (MIT vs Apache-2.0). Dependency/grammar policy: [ADR-009](docs/adr/ADR-009-licensing-grammar-policy.md).

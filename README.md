# RepoLens

**Repository intelligence engine for understanding unfamiliar codebases through deterministic static analysis.**

[![CI](https://github.com/chandru2002-2/repolens/actions/workflows/ci.yml/badge.svg)](https://github.com/chandru2002-2/repolens/actions/workflows/ci.yml)
[![Latest Release](https://img.shields.io/github/v/release/chandru2002-2/repolens)](https://github.com/chandru2002-2/repolens/releases)

RepoLens turns a repository into a structured `RepositoryModel`, runs analyzers over that model, and exposes the results through a CLI, REST API, and interactive Web UI.

> **Current release:** `v1.8.0-alpha.1` — Python Intelligence

## Why RepoLens?

Understanding an unfamiliar codebase usually requires jumping between files, packages, documentation, APIs, tests, and database models.

RepoLens brings those structural relationships into one analyzable model and visual workspace.

### Core capabilities

- Repository architecture and package dependency analysis
- Classes, methods, fields, types, and relationships
- REST endpoint discovery
- Test discovery and test-to-subject relationships
- Static execution traces and impact analysis
- JPA entity and relationship analysis
- Interactive architecture and dependency graphs
- Sequence, ER, DFD, activity, deployment, use-case, and state-machine projections
- Documentation-aware inspection
- CLI, REST API, and Web UI
- Large-repository safety limits and graceful degradation
- Evidence-backed relationships with confidence levels

## Demo

**[Open the RepoLens Live Demo →](https://repolens-dsce.onrender.com/)**

## Architecture

```text
GitHub / Local Repository
          ↓
      Ingestion
          ↓
 Language Analysis
(Tree-sitter when available,
 structural fallback otherwise)
          ↓
   RepositoryModel
          ↓
      Analyzers
          ↓
 Graph / Diagram Projections
          ↓
 CLI · REST API · Web UI
```

The core analysis is deterministic and does not require an LLM. AI explanations are planned separately and are not required for repository analysis.

## Language support

Current structural profiles include:

| Language | Support |
|---|---|
| Java | Structural analysis |
| JavaScript | Structural profile |
| TypeScript | Structural profile |
| Python | Structural analysis |
| Go | Structural profile |
| Rust | Structural profile |
| C# | Structural profile |
| Kotlin | Structural profile |

See the [language support matrix](docs/architecture/LANGUAGE_SUPPORT.md) for the current capability details.

## Intelligence and traceability

RepoLens v1.7+ extends structural analysis into repository intelligence:

- HTTP endpoint facts
- Java and Python test discovery
- Test-to-subject relationship inference
- Static execution trace composition
- Entity impact analysis
- Evidence and inference provenance
- Confidence-aware relationships
- Runtime/static distinctions in CLI output

The design keeps repository facts as the canonical source of truth and avoids inventing relationships when sufficient evidence is unavailable.

## Interactive diagrams

Available projections include:

- **Architecture** — repository and package/module layout
- **Package** — package-to-package dependencies
- **Class** — classes, interfaces, inheritance, and implementation
- **Sequence** — evidence-backed static call relationships
- **ER** — JPA entities and annotated associations
- **DFD** — API processes, services, and data stores
- **Activity** — simplified method-level activity summaries
- **Deployment** — Docker, Compose, Kubernetes, and datasource hints
- **Use Case** — operations inferred from REST mappings/controllers
- **State Machine** — evidence-backed status transitions

Large graphs are capped with explicit truncation information rather than silently dropping context.

## Project structure

| Module | Responsibility |
|---|---|
| `repolens-core` | Domain model, ports, and analysis pipeline |
| `repolens-ingest` | Local and GitHub repository ingestion |
| `repolens-parse` | Source code → RepositoryModel |
| `repolens-analyzers` | Analysis and graph/diagram projection |
| `repolens-api-model` | Stable API DTOs |
| `repolens-cli` | CLI interface |
| `repolens-web` | Javalin API and packaged UI |
| `repolens-web-ui` | React/Vite frontend |

## Requirements

- Java 21+
- Git
- Node.js 20+ for rebuilding the Web UI

## Quick start

Run the test suite:

```bash
./gradlew test
```

Start the Web UI:

```bash
./gradlew :repolens-cli:run --args='serve --port 8080'
```

Then open:

```text
http://localhost:8080
```

Analyze a local repository:

```bash
./gradlew :repolens-cli:run --args='analyze .'
```

JSON output:

```bash
./gradlew :repolens-cli:run --args='analyze . --json'
```

## Continuous Integration

GitHub Actions runs on pushes and pull requests to `main` and validates:

- Backend tests with Java 21
- Frontend installation, tests, and production build
- Docker image build validation

## Documentation

- [Documentation index](docs/README.md)
- [Project status](PROJECT_STATUS.md)
- [Roadmap](ROADMAP.md)
- [Architecture Decision Records](docs/adr/)
- [Contributing guide](CONTRIBUTING.md)

## Safety and analysis limits

RepoLens applies explicit ingestion limits so large or problematic repositories do not silently produce unreliable results.

Examples include:

- 5 MB default per-file limit
- Repository-wide file-count, size, and depth limits
- Binary/media exclusion
- Non-blocking warnings for skipped oversized files
- Confidence-aware relationship inference

## Project status

RepoLens is under active development. Releases are published as functionality is added and validated.

The repository currently does **not declare a software license**. A license should be added before describing the project as licensed for redistribution.

## Author

**Chandru M**

Java Backend Developer · Spring Boot · Repository Intelligence

[GitHub](https://github.com/chandru2002-2)
# Contributing to RepoLens

Thanks for your interest in contributing to **RepoLens**.

RepoLens is a repository intelligence workspace focused on helping developers understand unfamiliar codebases through deterministic static analysis, structured repository models, and interactive visualization.

This guide explains how to set up the project, understand its architecture, make changes, and submit contributions.

> **Before making architectural changes, read the [Repository Layout](docs/architecture/REPO_LAYOUT.md) and relevant [Architecture Decision Records](docs/adr/).**

---

# Getting Started

## Prerequisites

You will need:

* **JDK 21**
* **Git**
* **Node.js 20+** when working on or packaging the Web UI

Clone the repository:

```bash
git clone https://github.com/chandru2002-2/repolens.git
cd repolens
```

Run the test suite:

```bash
./gradlew test
```

If the tests pass, you are ready to start contributing.

---

# Repository Structure

RepoLens is organized as a modular monolith.

| Path                  | Purpose                                      |
| --------------------- | -------------------------------------------- |
| `repolens-core/`      | Domain model and core contracts              |
| `repolens-ingest/`    | Local and supported remote repository ingest |
| `repolens-parse/`     | Language parsing and structural extraction   |
| `repolens-analyzers/` | Repository analysis and graph projections    |
| `repolens-api-model/` | API DTOs and response mapping                |
| `repolens-cli/`       | Command-line interface                       |
| `repolens-web/`       | Web API and packaged UI                      |
| `repolens-web-ui/`    | React/Vite Web UI source                     |
| `docs/adr/`           | Architecture Decision Records                |
| `docs/architecture/`  | Living architecture documentation            |
| `scripts/`            | Development and packaging scripts            |

### Keep the boundaries intact

Do not:

* flatten the modules
* move language parsing into the CLI
* move language parsing into the Web layer
* merge the frontend into the Gradle source tree
* introduce dependencies between modules without considering the architecture

The separation between the analysis engine and its adapters is intentional.

---

# Architecture Principles

RepoLens has several architectural invariants that contributors should understand before making structural changes.

## 1. `RepositoryModel` is the central contract

The `RepositoryModel` provides the structured representation shared between repository ingestion, analysis, and presentation.

Changes to this contract can affect multiple modules and should be considered carefully.

See **ADR-002**.

---

## 2. Analyzers do not parse source

Analyzers consume structured repository information.

They should not:

* parse source files directly
* depend on Tree-sitter types
* contain language-specific parsing logic

Language-specific extraction belongs in the parsing layer.

See **ADR-004**.

---

## 3. CLI and Web are adapters

The CLI and Web layers should remain thin adapters around the analysis engine.

They should not become alternative analysis engines or contain language-specific parsing logic.

---

## 4. AI is optional

AI is not a required part of the core RepoLens analysis workflow.

If AI-based explanations or summaries are introduced, they should consume structured RepoLens analysis results rather than independently discovering or parsing repository source.

See **ADR-008**.

---

## 5. Prefer a modular monolith

RepoLens is intentionally designed as a modular monolith.

Do not introduce:

* microservices
* Kubernetes
* mandatory cloud infrastructure
* required databases
* unnecessary distributed systems

unless there is a strong architectural reason and the change is documented through an ADR.

See **ADR-001** and **ADR-005**.

---

## 6. Parsing must degrade gracefully

RepoLens prefers Tree-sitter when the required native parser is available.

Structural fallback is a first-class part of the system.

Tree-sitter is therefore **not a hard requirement for the entire analysis workflow**.

Do not replace parser bindings, vendor multi-platform native binaries, or significantly change the parsing architecture without documenting the decision.

See **ADR-011**.

---

## 7. Keep Spring Boot out of the core architecture

RepoLens intentionally does not use Spring Boot as a required foundation for its core architecture.

Do not introduce Spring Boot simply to provide functionality that can be implemented within the existing architecture.

See **ADR-005**.

---

# Backend Development

Run the complete test suite:

```bash
./gradlew test
```

Run repository analysis:

```bash
./gradlew :repolens-cli:run --args='analyze .'
```

Generate JSON analysis:

```bash
./gradlew :repolens-cli:run --args='analyze . --json'
```

Start the Web workspace:

```bash
./gradlew :repolens-cli:run --args='serve --port 8080'
```

Then open:

```text
http://localhost:8080
```

Architecture boundaries are enforced through automated tests, including ArchUnit checks in the CLI module.

---

# Frontend Development

The Web UI source lives in:

```text
repolens-web-ui/
```

Install dependencies:

```bash
cd repolens-web-ui
npm ci
```

Run frontend tests:

```bash
npm test
```

Build the UI:

```bash
npm run build
```

---

## Development Server

The backend API should already be running, typically on port `8080`.

```bash
cd repolens-web-ui
npm run dev
```

---

# Packaging the Web UI

After building the frontend:

```bash
npm run build
```

Package the generated UI into the Web module:

```bash
./scripts/package-ui.sh
```

This updates the static assets served by the Java application.

### Manual packaging

If necessary:

```bash
cd repolens-web-ui
npm run build

rm -rf ../repolens-web/src/main/resources/public

mkdir -p ../repolens-web/src/main/resources/public

cp -R dist/. ../repolens-web/src/main/resources/public/
```

When a UI change is intended to ship with the Java application, make sure the generated packaged assets are included as required by the repository workflow.

---

# Adding or Improving Language Support

Language support should follow the existing separation between:

```text
Parsing
   ↓
Structured Repository Model
   ↓
Analysis
   ↓
Visualization / API / CLI
```

When adding language-specific support:

* keep parsing concerns inside the parsing layer
* avoid putting parser-specific types into analyzers
* provide structural fallback where appropriate
* add tests for the extracted structures
* update the language support documentation
* avoid changing unrelated language behavior

See:

* [Language Support Matrix](docs/architecture/LANGUAGE_SUPPORT.md)
* [Architecture Documentation](docs/architecture/)
* [Architecture Decision Records](docs/adr/)

---

# Documentation

Documentation is part of the product.

When making changes:

### Update project status

Update [PROJECT_STATUS.md](PROJECT_STATUS.md) when the project's capabilities, limitations, or development phase materially change.

### Update architecture documentation

If an architectural decision changes, create or update an ADR under:

```text
docs/adr/
```

### Keep the README accurate

Do not document features that do not actually exist.

The README should reflect the current behavior of RepoLens rather than planned functionality.

---

# Tests

Before submitting a contribution, run:

```bash
./gradlew test
```

For Web UI changes:

```bash
cd repolens-web-ui
npm test
npm run build
```

For broader changes, test both the affected module and the complete project where practical.

---

# Code Quality

Before opening a pull request, check your changes with:

```bash
git diff --check
```

Keep changes:

* focused
* readable
* testable
* consistent with existing architecture
* free of unrelated formatting changes

Avoid introducing abstractions simply for the sake of abstraction.

---

# Pull Requests

A good pull request should make it easy to understand:

### What changed?

Briefly describe the implementation.

### Why was it needed?

Explain the problem or use case.

### How was it tested?

Mention the tests and commands you ran.

### Architectural impact

If the change affects module boundaries, the repository model, parsing architecture, or another architectural decision, explain the impact and reference the relevant ADR.

---

## Pull Request Checklist

Before opening a PR:

* [ ] The change has a clear purpose.
* [ ] Existing architecture boundaries are respected.
* [ ] Tests have been added or updated where appropriate.
* [ ] `./gradlew test` passes.
* [ ] UI changes pass `npm test`.
* [ ] UI changes pass `npm run build`.
* [ ] `git diff --check` passes.
* [ ] Documentation has been updated where necessary.
* [ ] No unsupported features are documented as implemented.
* [ ] Architectural changes have an ADR when appropriate.

---

# Issues and Discussions

If you find a bug, please open an issue with enough information to reproduce it.

Useful information includes:

* operating system
* Java version
* Node.js version for UI issues
* repository being analyzed
* command used
* expected behavior
* actual behavior
* relevant logs or error messages

For questions, ideas, and broader technical discussions, use:

**[GitHub Discussions](https://github.com/chandru2002-2/repolens/discussions)**

For implementation bugs and actionable tasks:

**[GitHub Issues](https://github.com/chandru2002-2/repolens/issues)**

---

# Good First Contributions

If you are new to RepoLens, start with issues labeled:

**[Good First Issue →](https://github.com/chandru2002-2/repolens/issues?q=is%3Aissue+label%3A%22good+first+issue%22)**

Good contribution areas include:

* documentation improvements
* tests
* UI improvements
* graph visualization
* language support
* structural extractors
* analysis quality
* developer experience

---

# Architecture Changes

If your contribution changes the architecture, consider whether an Architecture Decision Record is needed.

Examples include:

* introducing a new module
* changing module dependencies
* changing the `RepositoryModel`
* changing parser architecture
* introducing a new persistence layer
* introducing an external service
* changing the AI integration boundary
* changing the deployment architecture

Create an ADR under:

```text
docs/adr/
```

Explain:

1. The problem.
2. The proposed decision.
3. Alternatives considered.
4. Consequences.
5. Why the decision fits RepoLens.

---

# Code of Conduct

Please read the [Code of Conduct](CODE_OF_CONDUCT.md) before participating in the project.

RepoLens aims to maintain a respectful, constructive, and technically focused community.

---

# License

RepoLens is licensed under the **MIT License**.

See [LICENSE](LICENSE) for the complete license text.

---

## Thank You

Every contribution helps make RepoLens better.

Whether you fix a bug, improve documentation, add language support, improve the UI, or simply report an issue, your contribution helps move the project forward.

**Understand the codebase before you change it.**

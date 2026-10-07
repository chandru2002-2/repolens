<div align="center">

# RepoLens

### Understand an unfamiliar codebase before you modify it.

**Repository intelligence for developers who need to understand software before changing it.**

[![GitHub Stars](https://img.shields.io/github/stars/chandru2002-2/repolens?style=flat\&logo=github)](https://github.com/chandru2002-2/repolens/stargazers)
[![GitHub Forks](https://img.shields.io/github/forks/chandru2002-2/repolens?style=flat\&logo=github)](https://github.com/chandru2002-2/repolens/network/members)
[![Latest Release](https://img.shields.io/github/v/release/chandru2002-2/repolens?display_name=tag\&style=flat)](https://github.com/chandru2002-2/repolens/releases)
[![GitHub Issues](https://img.shields.io/github/issues/chandru2002-2/repolens?style=flat)](https://github.com/chandru2002-2/repolens/issues)
[![Java](https://img.shields.io/badge/Java-21+-orange?style=flat\&logo=openjdk)](https://www.oracle.com/java/)
[![React](https://img.shields.io/badge/React-TypeScript-blue?style=flat\&logo=react)](https://react.dev/)
[![Gradle](https://img.shields.io/badge/build-Gradle-02303A?style=flat\&logo=gradle)](https://gradle.org/)

<br>

[**🚀 Try RepoLens**](https://repolens-dsce.onrender.com/)    ·   
[**📖 Documentation**](docs/)    ·   
[**⭐ Star on GitHub**](https://github.com/chandru2002-2/repolens)

</div>

---

## See the Codebase, Not Just the Files

Large repositories are difficult to understand.

You jump between packages, classes, methods, APIs, dependencies, tests, configuration and documentation just to answer a simple question:

> **"How does this codebase actually fit together?"**

RepoLens turns those scattered structural signals into an **interactive repository intelligence workspace**.

```text
                    GitHub Repository
                           │
                           ▼
                       RepoLens
                           │
          ┌────────────────┼────────────────┐
          │                │                │
          ▼                ▼                ▼
      Structure       Relationships     Documentation
          │                │                │
          └────────────────┼────────────────┘
                           ▼
                    Repository Model
                           │
                           ▼
                 Interactive Workspace
```

**Explore the structure. Understand the relationships. Then change the code.**

---

<div align="center">

## 🚀 Explore RepoLens

### Analyze a public GitHub repository and explore its structure.

[**Open the Live Demo →**](https://repolens-dsce.onrender.com/)

</div>

> The hosted deployment is intended for exploration. Run RepoLens locally when you need full control over the analysis workflow.

---

# Why RepoLens?

Understanding an unfamiliar repository normally requires moving between:

**Files → Packages → Classes → Methods → Dependencies → APIs → Tests → Documentation**

RepoLens brings these signals together.

### Instead of asking:

> "Where is this code?"

### You can start asking:

> "How is this repository structured?"

> "What depends on this class?"

> "Which APIs connect to these components?"

> "What tests are related to this code?"

> "What relationships exist between these entities?"

---

# 🔍 Repository Intelligence

RepoLens analyzes a repository and builds a structured representation of its codebase.

<div align="center">

| 🔎 Structure | 🔗 Relationships | 🌐 APIs        | 🧪 Tests      |
| ------------ | ---------------- | -------------- | ------------- |
| Packages     | Imports          | REST endpoints | Test classes  |
| Classes      | Dependencies     | Controllers    | Test methods  |
| Methods      | Calls            | API structure  | Test subjects |
| Fields       | Inheritance      | Services       | Relationships |

</div>

### Repository Structure

Explore:

* packages
* classes
* interfaces
* methods
* fields
* inheritance
* implementations
* imports
* dependencies

### API Analysis

Detect supported REST endpoints and connect them with surrounding application structures.

### Test Intelligence

Discover tests and connect them to related code when sufficient structural evidence exists.

### Documentation Analysis

Inspect repository documentation, including:

* `README`
* `docs/`
* documented entities
* relationships between documentation and repository entities

---

# 🗺️ Explore the Codebase Visually

RepoLens provides multiple views of the same underlying repository model.

```text
Architecture
     │
     ├── Package
     ├── Class
     ├── Sequence
     ├── ER
     ├── DFD
     ├── Activity
     ├── Deployment
     ├── Use Case
     └── State Machine
```

### Architecture

Understand the high-level organization of the repository.

### Package

Explore package and module dependencies.

### Class

Inspect classes, interfaces, methods, fields, inheritance and implementations.

### Sequence

Explore statically inferred call relationships.

### ER

Inspect database and entity relationships where structural evidence is available.

### DFD

Explore data-flow-oriented relationships.

### Activity

Inspect supported activity and control-flow signals.

### Deployment

Explore deployment relationships derived from supported configuration.

### Use Case

Explore structural relationships relevant to use-case-oriented views.

### State Machine

Inspect supported state-transition evidence.

---

# 🧠 Intelligence Without Guessing

RepoLens is built around a simple principle:

> **When the repository does not provide enough evidence, don't invent the relationship.**

Relationships are derived from structural evidence.

For example:

```text
┌─────────────────────────────┐
│       High Confidence       │
│                             │
│ Typed receiver              │
│ Statically identifiable     │
│ relationship                │
└─────────────────────────────┘

              ↓

┌─────────────────────────────┐
│      Medium Confidence      │
│                             │
│ Supported structural        │
│ name-based heuristic        │
└─────────────────────────────┘

              ↓

┌─────────────────────────────┐
│    Insufficient Evidence    │
│                             │
│ Relationship is not emitted │
└─────────────────────────────┘
```

This keeps the repository model grounded in what can actually be established from the code.

---

# ☕ Built for Real Codebases

RepoLens includes specialized structural analysis for supported **Java and Spring** repositories.

It can identify signals such as:

* Spring controllers
* REST endpoints
* services
* repositories
* JPA entities
* entity relationships
* method calls
* configuration
* application structure

All of these are derived from static repository evidence rather than runtime execution.

---

# 🌐 Language Support

| Language       | Support     |
| -------------- | ----------- |
| **Java**       | ✅ Supported |
| **JavaScript** | 🟡 Partial  |
| **TypeScript** | 🟡 Partial  |
| **Python**     | 🟡 Partial  |
| **Go**         | 🟡 Partial  |
| **Rust**       | 🟡 Partial  |
| **C#**         | 🟡 Partial  |
| **Kotlin**     | 🟡 Partial  |

Language coverage is actively evolving.

→ [View the language support matrix](docs/architecture/LANGUAGE_SUPPORT.md)

---

# ⚙️ How It Works

RepoLens separates repository ingestion, parsing, analysis and presentation.

```text
                       Repository
                           │
                           ▼
                      Ingestion
                           │
                           ▼
                   Language Parsing
                           │
                           ▼
                    RepositoryModel
                           │
             ┌─────────────┼─────────────┐
             ▼             ▼             ▼
         Structure     Dependencies     Tests
             │             │             │
             └─────────────┼─────────────┘
                           ▼
                        Analysis
                           │
                           ▼
                        GraphView
                           │
              ┌────────────┼────────────┐
              ▼            ▼            ▼
             CLI          API          Web UI
                                        │
                                        ▼
                                    Visualizations
```

The `RepositoryModel` is the central contract between ingestion, analysis and presentation.

The CLI and Web layers remain adapters around the analysis engine rather than owning language-specific parsing logic.

→ [Architecture](docs/architecture/)
→ [Repository Layout](docs/architecture/REPO_LAYOUT.md)
→ [Architecture Decision Records](docs/adr/)
→ [Language Support](docs/architecture/LANGUAGE_SUPPORT.md)

---

# 🛠️ Run It Yourself

## Requirements

* JDK 21
* Git
* Node.js 20+ for Web UI development

### Clone

```bash
git clone https://github.com/chandru2002-2/repolens.git
cd repolens
```

### Build & Test

```bash
./gradlew test
```

---

## Analyze a Repository

### Basic analysis

```bash
./gradlew :repolens-cli:run --args='analyze .'
```

### JSON output

```bash
./gradlew :repolens-cli:run --args='analyze . --json'
```

### Save analysis

```bash
./gradlew :repolens-cli:run --args='analyze . -o analysis.json'
```

---

# 🌐 Run the Web Workspace

Start the server:

```bash
./gradlew :repolens-cli:run --args='serve --port 8080'
```

Open:

```text
http://localhost:8080
```

The Web UI provides an interactive repository graph explorer powered by Cytoscape.

---

# 💻 Web UI Development

The Web UI lives in:

```text
repolens-web-ui/
```

Install dependencies:

```bash
cd repolens-web-ui
npm ci
```

Development:

```bash
npm run dev
```

Production build:

```bash
npm run build
```

Package the UI:

```bash
./scripts/package-ui.sh
```

---

# 🏗️ Project Architecture

```text
repolens/
│
├── repolens-core/
│   └── Domain model and core contracts
├── repolens-ingest/
│   └── Local and supported remote repository ingestion
│
├── repolens-parse/
│   └── Language parsing and structural extraction
│
├── repolens-analyzers/
│   └── Repository analysis and graph/diagram projections
│
├── repolens-api-model/
│   └── API DTOs and response mapping
│
├── repolens-cli/
│   └── Command-line interface
│
├── repolens-web/
│   └── Web API and packaged UI
│
├── repolens-web-ui/
│   └── React / Vite interface
│
├── docs/
│   ├── adr/
│   └── architecture/
│
└── scripts/
```

→ [Explore the repository architecture](docs/architecture/REPO_LAYOUT.md)

---

# 🎯 Design Philosophy

RepoLens intentionally follows a few principles.

### Deterministic First

Repository intelligence should come from reproducible structural analysis.

### Evidence-Backed

Relationships should have a structural basis.

### Graceful Degradation

When a parser or analyzer cannot establish a fact, analysis should continue where possible.

### Local First

The core analysis workflow should remain useful without requiring a cloud database or mandatory AI service.

### AI Optional

AI may eventually explain structured RepoLens results, but it should not be responsible for discovering the underlying repository structure.

### Modular

RepoLens is intentionally designed as a modular monolith rather than a collection of unnecessary services.

---

# 📊 What RepoLens Can Analyze

* Repository structure
* Packages
* Classes
* Methods
* Fields
* Inheritance
* Implementations
* Dependencies
* REST endpoints
* Tests
* Test-to-subject relationships
* Method calls
* Impact relationships
* JPA entities
* Documentation
* Architecture
* Sequence relationships
* ER relationships
* DFD relationships
* Activity signals
* Deployment relationships
* Use-case relationships
* State transitions
* Repository metadata

---

# ⚠️ Current Limitations

RepoLens is a **static-analysis system**, not a runtime debugger.

Current limitations include:

* Private GitHub repositories are not currently supported.
* Non-GitHub remote hosts are not currently supported.
* Runtime behavior cannot be completely reconstructed through static analysis.
* Call resolution can be heuristic.
* Specialized diagrams may be empty when structural signals are unavailable.
* Large graphs are subject to analysis limits.
* Full arbitrary-method control-flow reconstruction is not currently supported.
* Some language profiles are partial.
* Persistent job storage is not currently part of the core workflow.
* Remote repository caching currently reuses an existing cached repository rather than always fetching the latest state.

→ [View Project Status](PROJECT_STATUS.md)

---

# 🛣️ Roadmap

RepoLens is actively evolving.

Current development areas include:

* richer node and entity details
* graph export
* improved multi-platform parser support
* optional AI explanations over structured analysis
* persistent analysis/job storage
* remote repository cache freshness
* expanded language support

→ [View Roadmap](ROADMAP.md)

---

# 🤝 Contributing

RepoLens is open to contributions.

You can contribute to:

* language support
* structural extractors
* graph visualization
* UI improvements
* documentation
* analysis quality
* tests
* performance
* developer experience

Before making architectural changes, read:

→ [CONTRIBUTING.md](CONTRIBUTING.md)
→ [Architecture Decision Records](docs/adr/)
→ [Repository Layout](docs/architecture/REPO_LAYOUT.md)

### 🟢 Good First Issues

New to the project?

[**Browse Good First Issues →**](https://github.com/chandru2002-2/repolens/issues?q=is%3Aissue+label%3A%22good+first+issue%22)

Have an idea?

[**Start a GitHub Discussion →**](https://github.com/chandru2002-2/repolens/discussions)

---

# 📌 Project Status

<div align="center">

### RepoLens `v1.9.0`

**Repository Intelligence Workspace**

</div>

Currently focused on:

`Analysis Quality` · `Visualization` · `Developer Experience` · `Language Coverage` · `Architecture Exploration`

---

# 📄 License

RepoLens is licensed under the **MIT License**.

See [LICENSE](LICENSE) for details.

---

# 👨‍💻 Built by Chandru M

**Backend Developer · Java · Spring Boot · Developer Tools**

[GitHub](https://github.com/chandru2002-2/) ·
[LinkedIn](https://www.linkedin.com/in/chandru-mohan-232932240/)

---

<div align="center">

# ⭐ Like RepoLens?

If RepoLens helps you understand a repository, give it a star.

[**⭐ Star RepoLens on GitHub**](https://github.com/chandru2002-2/repolens)

<br>

**Understand the codebase before you change it.**

</div>

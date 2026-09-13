# Roadmap

## Completed

- [x] Architecture re-evaluation (Web + CLI product direction)
- [x] Foundation bootstrap: ADRs, Gradle modules, `RepositoryModel`, ports, boundary tests
- [x] Ingestion MVP — local working-tree ingest with ignore rules and safety limits scaffolding
- [x] Tree-sitter parse MVP — engine + first language profiles → populate `RepositoryModel`
- [x] Core analyzers — structure, imports/deps, basic metrics, `GraphView` projection
- [x] CLI useful output — human report + `--json` / `-o` + quieter logs
- [x] Web API MVP — Javalin jobs + status + graph endpoints
- [x] Web UI MVP — URL/path input + interactive Cytoscape graph explorer
- [x] Remote ingestion (MVP) — allowlisted public GitHub HTTPS shallow clone
- [x] v1.6.0 — focused structural extractors (JPA, Spring, CALLS, activity, state, deployment, config)
- [x] v1.6.0 — diagram projections (Architecture, Package, Class, Sequence, ER, DFD, Activity, Deployment, Use Case, State Machine)
- [x] v1.6.0 — client-side graph filters (kinds, relationship types, name search)
- [x] v1.6.0 — analysis quality and UI reliability (empty-diagram handling, explorer layout, inspector fields)

## Next

1. **UI polish** — richer node details, export
2. **Multi-arch Tree-sitter natives** — avoid structural fallback on Apple Silicon
3. **Optional AI explanations** — behind feature flag, consuming structured analysis results (not implemented in v1.6.0)
4. **Persistent job/cache store** (optional SQLite)
5. **Remote clone freshness** — fetch/pull or TTL for `~/.repolens/cache/remotes` (v1.6.0 reuses `.git` without updating)

## Non-goals (near term)

- Microservices / Kubernetes
- Required cloud databases
- Spring Boot
- Full semantic SCIP/LSP indexing as the foundation
- AI-generated architecture as the primary analysis path

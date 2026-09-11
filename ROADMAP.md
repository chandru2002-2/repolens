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

## Next

1. **Context Studio polish** — richer multi-select scope, optional Mermaid/PNG export (separate), confidence visible in UI (AI prompt packaging shipped: local prompt generation, no provider)
2. **UI polish** — richer node details, filters, export
3. **Multi-arch Tree-sitter natives** — avoid structural fallback on Apple Silicon
4. **Optional AI explanations** — behind feature flag, consuming structured results / Context Studio output
5. **Persistent job/cache store** (optional SQLite) including context history

## Non-goals (near term)

- Microservices / Kubernetes
- Required cloud databases
- Spring Boot
- Full semantic SCIP/LSP indexing as the foundation
- AI-generated architecture as the primary analysis path

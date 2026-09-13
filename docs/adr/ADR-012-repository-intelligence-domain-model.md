# ADR-012: Repository Intelligence Domain Model (v1.7)

- Status: Accepted
- Date: 2026-09-13
- Spec: [V1.7_INTELLIGENCE_SPEC.md](../architecture/V1.7_INTELLIGENCE_SPEC.md)

## Context

v1.6 stores repository structure on `RepositoryModel` (`Symbol`, `Relationship`,
`StructuralFact`, and related types). `Relationship` already has numeric
`confidence` and optional **string** `provenance`. Spring HTTP mappings exist
only as use-case `StructuralFact`s (`route=` detail). Tests are not modeled;
sequence diagrams merely omit `*Test` names.

The v1.7 spec asks for endpoint/test intelligence, traces, impact, and
inspectable evidence. It left open whether `Endpoint` should be a typed model
collection or a `StructuralFact` category, and how provenance should evolve
without breaking ADR-007 JSON contracts.

This ADR locks the domain model **before implementation**. None of the new types
below exist in v1.6 code.

## Decision

v1.7 extends `RepositoryModel` **additively**. It does not replace ADR-002.

1. **`Endpoint` is a first-class canonical fact** on `RepositoryModel` (own ids,
   not a stringly-typed `StructuralFact.detail` map). An endpoint is a declared
   HTTP (or equivalent) operation captured from source: method, path, owning
   type id, handler method id when known, `SourceLocation`, framework evidence.
   Handler→service hops are **not** endpoint facts; they are traces.

2. **`Test` is a first-class canonical fact** for discovered test types/methods
   (annotation and/or documented name patterns). “This construct is a test” is
   a fact. “This test exercises symbol X” is **derived** (analyzer relationships
   / intelligence), never silently stored as an unattributed fact.

3. **Reuse existing `SourceLocation`.** Do not add a second location type.
   Unknown coordinates remain `0` as in v1.6.

4. **Evidence is structured.** Introduce an `Evidence` value type (inference
   method, optional `SourceLocation`, optional fact/relationship id, optional
   short excerpt/summary). `Relationship.provenance` string may remain for
   compatibility; new edges and intelligence hops must populate structured
   evidence. Do not treat the string as the v1.7 source of truth.

5. **Facts stay on `RepositoryModel`. Derived intelligence does not.**
   **Trace** and **Impact** are result objects produced after analyzers run
   (alongside `AnalysisResult` / projections such as `GraphView`). They cite
   fact and relationship ids. They are not additional core fact collections.

6. **Confidence stays `0.0`–`1.0`** on relationships and on derived hops.
   UI/CLI may map bands (e.g. high/medium) from those numbers. Bands are
   presentation, not a second stored scale. Confidence is **not** runtime proof.

7. **Omit rather than invent.** Below-threshold CALLS remain omitted (v1.6
   policy). Missing endpoints/tests yield empty lists. Traces may include an
   explicit unresolved hop; they must not skip to a same-named type without
   evidence. Framework naming conventions may label roles; they must not alone
   create CALLS or trace hops.

8. **Language/framework knowledge stays in parse/profiles/extractors.**
   Analyzers consume the model only (ADR-004). Java/Spring may be the first
   extractor target; model types remain framework-agnostic.

9. **Delivery shape is unchanged:** modular monolith, existing Gradle modules,
   in-memory jobs, no required DB, no AI dependency for core intelligence
   (ADR-008 remains optional/unimplemented). No `RepositorySession`, plugin SDK,
   renderer module, or microservices for v1.7.

## Domain boundaries

| Kind | Lives in | Examples |
|------|----------|----------|
| Facts | `RepositoryModel` | `Endpoint`, `Test` discovery, `Symbol`, `StructuralFact` |
| Relationships | `RepositoryModel` | Existing types; additive types (e.g. `HANDLES`) only when an edge is a first-class graph fact with evidence |
| Evidence | Value on relationships / facts / hops | `annotation`, `type_resolution`, `name_heuristic`, `ast_capture` |
| Derived views | Analysis/intelligence results | Trace, Impact, diagrams, `GraphView` |
| Syntax | `repolens-parse` | Spring/JUnit annotations, Tree-sitter queries |
| Adapters | CLI / Web / `repolens-api-model` | DTO mapping only |

Existing use-case `StructuralFact`s may later be **projected from** `Endpoint`
facts; they are not a substitute for the `Endpoint` type.

## Relationship to ADR-004 and ADR-007

**ADR-004:** Unchanged. Endpoint/Test **extraction** runs in parse/extractors.
Endpoint/test **normalization**, subject linking, traces, and impact run as
analyzers/composition on the model. Analyzers must not read source, depend on
Tree-sitter, or embed grammars.

**ADR-007:** Unchanged policy. External JSON remains `repolens-api-model`.
v1.7 fields are **additive** (empty lists/optional objects). Keep
`schemaVersion = v1` until a breaking change. Never expose parser types.
Route layout (`/result` vs `/endpoints`) is an implementation choice within
this policy, not a domain-model decision.

## Consequences

- CLI, API, and UI can share one engine model; UI must not infer endpoints.
- Inspectable evidence enables traces without claiming execution.
- Typed `Endpoint`/`Test` avoid UI/CLI parsing of `detail` strings.
- Additive model + DTOs preserve v1.6 diagrams and jobs.
- First implementation can be Java/Spring-only extractors with empty lists
  elsewhere.

## Alternatives considered

| Alternative | Rejected because |
|-------------|------------------|
| Encode endpoints only as `StructuralFact` | Ambiguous `detail` maps; UI/analyzers fork parsers |
| Put traces/impact on `RepositoryModel` | Duplicates facts; circular graphs; harder to omit safely |
| New location type | Duplicates `SourceLocation` |
| Structured evidence only in the UI | Diverges CLI/API; UI becomes source of truth |
| New Gradle module / `RepositorySession` / plugin SDK | Premature; current ports suffice |
| Semantic confidence enum as the stored form | Conflicts with existing `0.0`–`1.0`; bands belong in presentation |

## Non-goals

- Implementing `Endpoint`, `Test`, or `Evidence` in this change
- Runtime tracing, coverage, compiler-level Java resolution
- Complete Spring/JUnit/WebFlux coverage
- AI analysis, MCP, VS Code, product GitHub Action
- Breaking `schemaVersion v1` or rewriting ADR-001–011 decisions
- Language parity for endpoints/tests

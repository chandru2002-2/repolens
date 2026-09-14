# ADR-013: Multi-Language Intelligence Architecture (v1.8)

- Status: Accepted
- Date: 2026-09-14
- Spec: [V1.8_MULTILANGUAGE_INTELLIGENCE_SPEC.md](../architecture/V1.8_MULTILANGUAGE_INTELLIGENCE_SPEC.md)
- Matrix: [LANGUAGE_CAPABILITY_MATRIX.md](../architecture/LANGUAGE_CAPABILITY_MATRIX.md)
- Supersedes: none (extends [ADR-012](ADR-012-repository-intelligence-domain-model.md); does not replace it)

## Context

v1.7 (ADR-012) locked a **language-agnostic** intelligence model: canonical
`Endpoint` and `Test` facts on `RepositoryModel`, structured `Evidence`, numeric
confidence `0.0`–`1.0`, and derived Trace/Impact **off** the model. Java/Spring
and JUnit extractors populate those facts. `TestSubjectAnalyzer`,
`TraceComposer`, and `ImpactComposer` consume the model only (ADR-004).

Multi-language QA after v1.7:

- **Java/Spring:** structural + Endpoint + Test + TESTS + Trace working.
- **Python/Flask, Go/Gin, TypeScript/NestJS, C#/.NET, Rust/Axum:** structural
  working; **intelligence currently 0**.

That gap is missing **extractors**, not missing composers. A tempting response
is a per-language “intelligence engine” (Python traces vs Go traces) or
UI-side framework inference. Both would fork the source of truth and violate
ADR-002 / ADR-004 / ADR-012.

v1.8 needs an architecture decision **before** Flask/Nest/Gin/ASP.NET
extractors are implemented: how multi-language intelligence extends v1.7
without new product shape.

## Decision

v1.8 extends v1.7 by **adding language/framework extractors** that emit the
**same canonical facts**. It does not add intelligence engines.

1. **Extractors discover and normalize.** Framework-specific knowledge
   (Flask routes, Nest decorators, Gin registrations, ASP.NET attributes,
   Axum routers, Kotlin/Spring or Ktor) lives in `repolens-parse`
   profiles/extractors. They write `Endpoint`, `Test`, and `Evidence`
   onto `RepositoryModel` using existing types. Extractors MAY emit
   evidence-backed `CALLS` using existing thresholds; they are not required
   to emit `CALLS`.

2. **`RepositoryModel` remains the single source of truth** for facts
   (ADR-002, ADR-012). No parallel per-language intelligence model.

3. **Downstream remains language-agnostic.** `TESTS` linking, Trace, Impact,
   `GraphView` projection, API DTOs, CLI, and Web must not branch on
   `python` / `go` / `csharp`. They already treat empty lists as valid.

4. **No per-language intelligence engines.** No `PythonTraceComposer`,
   `GinImpactAnalyzer`, or Web UI Nest parser.

5. **Unsupported or unknown intelligence is omitted or empty**, never
   represented as an invented proof that facts do not exist at runtime.
   “Intelligence currently 0” in QA means I0 capability, not a failed job.

6. **Confidence stays `0.0`–`1.0`.** Evidence stays structured. CALLS emit
   threshold and omit-rather-than-invent (ADR-012) apply to every language.

7. **Delivery shape is unchanged:** modular monolith, existing Gradle
   modules, in-memory jobs, no required database, no AI dependency for core
   intelligence, no microservices, no `RepositorySession`, no plugin SDK.

8. **Priority:**
   - **Tier 1:** Java/Spring (existing I3) — non-regression.
   - **Tier 2A:** Python Flask and FastAPI; TypeScript/NestJS; C#/ASP.NET Core;
     Go/Gin.
   - **Tier 2B:** Rust/Axum; Kotlin/Spring or Ktor.

9. **API compatibility:** keep `schemaVersion = v1`. Existing additive fields
   (`endpoints`, `tests`, `traces`, impact routes) fill as extractors ship.
   No consumer contract change is required to begin 2A work.

## Relationship to existing ADRs

| ADR | Effect |
|-----|--------|
| 001 Product shape | Unchanged |
| 002 RepositoryModel | Unchanged; still the fact contract |
| 003 / 011 Parsing | Unchanged; extractors sit on profiles + fallback |
| 004 Analyzer purity | Unchanged; no source/Tree-sitter in analyzers |
| 005–007 Web/jobs/JSON | Unchanged; v1 routes and schema |
| 008 AI | Remains optional/unimplemented |
| 012 Domain model | Unchanged types; this ADR only extends **who may populate** them |

## Consequences

- Implementation work is **N extractors**, not N composers.
- Flask/Nest/Gin can reach I1 (facts) before I2 (traces) if CALLS extraction
  lags; that is an honest capability level.
- CLI/Web/API keep one mapping path.
- Capability must be documented in LANGUAGE_CAPABILITY_MATRIX separately from
  LANGUAGE_SUPPORT (structure).
- First 2A slice can land without Kotlin/Axum.

## Alternatives considered

| Alternative | Rejected because |
|-------------|------------------|
| Per-language trace/impact engines | Duplicates ADR-012 composers; UI/API fork |
| Infer endpoints in Web UI from labels | UI becomes source of truth; violates v1.7 |
| Encode new frameworks only as use-case `StructuralFact` strings | Repeats the ambiguity ADR-012 rejected |
| Plugin SDK / RepositorySession | Premature; current ports suffice |
| Require CALLS before shipping any Endpoint facts | Blocks honest I1; traces can stay empty |
| Claim language parity when structure works | Contradicts QA (intelligence 0) |
| Lower CALLS thresholds for new languages | Invents traces |

## Non-goals

- Implementing extractors in this ADR
- Runtime tracing, coverage, compilers, AI analysis
- Universal framework coverage in one release
- Changing `schemaVersion`, analyzers’ language independence, or putting
  Trace/Impact on `RepositoryModel`
- Treating I0 empty lists as a defect of TestSubjectAnalyzer or TraceComposer

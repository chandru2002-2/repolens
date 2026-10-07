# Language Capability Matrix (Intelligence)

**Current status:** RepoLens `v1.9.0`
**Historical baseline:** Original v1.8 QA and proposal, dated 2026-09-14
**Complements:** [LANGUAGE_SUPPORT.md](LANGUAGE_SUPPORT.md) (structural coverage)
**Architecture:** [V1.8_MULTILANGUAGE_INTELLIGENCE_SPEC.md](V1.8_MULTILANGUAGE_INTELLIGENCE_SPEC.md),
[ADR-013](../adr/ADR-013-multi-language-intelligence-architecture.md)

This matrix describes **repository intelligence** (Endpoint, Test, TESTS, Trace,
Impact) separately from **structure** (modules, symbols, imports, `DEPENDS_ON`,
diagrams). A language can be structurally PARTIAL and intelligence **I0**.

Empty intelligence collections mean **no evidence was extracted**, not **the
runtime has no routes or tests**. Do not represent unsupported/unknown
intelligence as an invented absence of facts.


## 1. Levels

### Structure (from LANGUAGE_SUPPORT)

| Status | Meaning |
|--------|---------|
| **FULL** | Not used today |
| **PARTIAL** | Meaningful `RepositoryModel` contribution |
| **UNSUPPORTED** | No language profile |

### Intelligence

| Level | Facts | TESTS / Trace / Impact |
|-------|-------|------------------------|
| **I0** | No Endpoint/Test extractors for this stack | Empty; composers have nothing to attach |
| **I1** | Endpoint and/or Test facts from declarations / documented test patterns | TESTS/traces only if CALLS/IMPORTS already exist |
| **I2** | I1 plus CALLS (or equivalent) enough for some static traces | Composers run unchanged |
| **I3** | Java/Spring-class depth (Spring mappings, Java tests, CALLS, traces) | Current v1.9 Java/Spring capability bar |

I0 is a **capability**, not a failed analysis.

## Current v1.9 status

All eight language profiles provide **PARTIAL** structural support. None is a complete language implementation. Intelligence levels below describe available extractor facts, not guaranteed framework coverage.

| Language / stack | Structure | v1.9 intelligence | Current evidence and limits |
|------------------|-----------|-------------------|-----------------------------|
| Java / Spring | PARTIAL | **I3** | Selected Spring endpoint facts, Java test facts, and heuristic Java CALLS; TESTS, traces, and impact are composed where evidence resolves. |
| Java without Spring | PARTIAL | **I1-I2, varies** | Java test and CALLS facts may be available; endpoint facts require recognized Spring mappings. |
| Python / Flask and FastAPI | PARTIAL | **I1** | Selected Flask route and FastAPI decorator endpoint facts, plus supported Python test patterns. Python CALLS are not extracted; downstream links and traces depend on resolvable available relationships. |
| JavaScript | PARTIAL | **I0** | Structural profile only; no current endpoint or test extractor. |
| TypeScript / NestJS | PARTIAL | **I0** | Structural profile only; no current NestJS endpoint or TypeScript test extractor. |
| C# / ASP.NET Core | PARTIAL | **I0** | Structural profile only; no current endpoint or test extractor. |
| Go / Gin | PARTIAL | **I0** | Structural profile only; no current endpoint or test extractor. |
| Rust / Axum | PARTIAL | **I0** | Structural profile only; no current endpoint or test extractor. |
| Kotlin / Spring or Ktor | PARTIAL | **I0** | Structural profile only; no current endpoint or test extractor. |
| Unsupported languages | UNSUPPORTED | **I0** | No language profile; files may still be inventoried. |

`TESTS`, Trace, and Impact remain evidence-derived and language-agnostic. Empty results do not prove that a repository has no routes or tests. Java is currently the only profile that emits CALLS; Python facts therefore do not imply call traces.


## Historical v1.8 QA summary (2026-09-14)

Historical v1.8 QA results and proposal targets as of 2026-09-14; these values do not describe v1.9.

| Language / stack | Structure in snapshot | Intelligence in snapshot (QA) | v1.8 target at publication | Notes |
|------------------|-----------------|-------------------------|-------------|-------|
| Java / Spring | PARTIAL | **I3** — Endpoint, Test, TESTS, Trace working | **Tier 1** — maintain I3 | Non-regression baseline |
| Java (no Spring) | PARTIAL | Tests possible; endpoints empty unless mappings exist | Maintain | Honest empty endpoints |
| Python / Flask | PARTIAL | **I0** | **Tier 2A → I1+** | No Flask endpoint extractor had shipped at that snapshot |
| Python / FastAPI | PARTIAL | **I0** | **Tier 2A → I1+** | Same Python profile; distinct extractor |
| TypeScript / NestJS | PARTIAL | **I0** | **Tier 2A → I1+** | TS structure; no Nest extractor yet |
| JavaScript (generic) | PARTIAL | **I0** | Not 2A unless facts exist | No invented Express/Next routes |
| C# / ASP.NET Core | PARTIAL | **I0** (C#/.NET QA: 0) | **Tier 2A → I1+** | Structure working |
| C# (no ASP.NET) | PARTIAL | **I0** | Empty endpoints | |
| Go / Gin | PARTIAL | **I0** | **Tier 2A → I1+** | Structure working |
| Go (stdlib `net/http` only) | PARTIAL | **I0** | Optional later; not 2A gate | Do not guess handlers |
| Rust / Axum | PARTIAL | **I0** | **Tier 2B → I1+** | After 2A |
| Kotlin / Spring or Ktor | PARTIAL | **I0** | **Tier 2B → I1+** | After 2A; Spring-like vs Ktor extractors |
| Unsupported languages | UNSUPPORTED | **I0** | Stay I0 | Inventory only |


## 3. Intelligence dimensions

The following table records the v1.8 QA snapshot, not current v1.9 capability.

Consumers (API/CLI/Web) already expose these collections. Fill only from
canonical facts and composers.

| Dimension | Java/Spring in snapshot | Other 2A/2B stacks in snapshot |
|-----------|-------------------|------------------------------|
| Endpoint facts | Yes (Spring mappings) | Empty |
| Test facts | Yes (JUnit + name/file) | Empty |
| TESTS relationships | Yes (analyzer, not model) | Empty unless Test facts + CALLS/IMPORTS |
| Static traces | Yes (Endpoint + CALLS) | Empty or unresolved if no CALLS |
| Impact | Derived from model + traces | Empty categories without evidence |
| Graph `TESTS` edges | Projected from analyzer when nodes exist | None |

In that snapshot, downstream composers had no Python/Go/TypeScript extractor
facts to consume. See the current v1.9 table above for the later Python
extractors.


## Historical v1.8 framework extraction plan

These were proposed targets at publication time, not a record of current
support. Flask and FastAPI now have limited endpoint extractors; see the current
v1.9 table.

| Stack | Typical declarations (extractor input) | Must not treat as enough |
|-------|----------------------------------------|---------------------------|
| Spring | `@GetMapping`, `@RequestMapping`, … | Class named `FooController` |
| Flask | `@app.route`, blueprint routes | `app.py` filename |
| FastAPI | `@app.get`, `APIRouter` | Module named `api` |
| NestJS | `@Controller` + `@Get` | `*.controller.ts` alone |
| ASP.NET Core | `[HttpGet]`, `MapGet` | `*Controller.cs` alone |
| Gin | `router.GET`, group routes | Package named `handler` |
| Axum | `Router` / `.route` | `main.rs` |
| Ktor | `routing { get(` | Application file name |

Omit multi-path/multi-method mappings that cannot be one `Endpoint` without
inventing cardinality (same rule as v1.7 Spring).


## Historical v1.8 test discovery plan

At publication time, Java was the only language with test extraction. The
Python patterns below were proposed scope; supported Python patterns now emit
test facts.

| Language | Fact signals | Subject linking |
|----------|--------------|-----------------|
| Java | JUnit 4/5 annotations; `*Test` / `*Tests` / `*IT` | `TestSubjectAnalyzer` via CALLS/IMPORTS only |
| Python | pytest/unittest; `test_*.py` / `*_test.py` | Same analyzer |
| TypeScript | Jest/Vitest-style; `*.test.ts` / `*.spec.ts` | Same analyzer |
| C# | `[Fact]` / `[Test]` / `[TestMethod]`; `*Tests` | Same analyzer |
| Go | `func TestXxx(*testing.T)` | Same analyzer |
| Rust | `#[test]` | Same analyzer |
| Kotlin | JUnit / kotlin.test | Same analyzer |

Name/file patterns produce Test **facts** with `NAME_HEURISTIC` evidence. They
must not create subject edges by name (`FooTest` ↛ `Foo`).


## Historical v1.8 empty-result examples

| Situation | Honest reading |
|-----------|----------------|
| Spring app, extractor ran, `endpoints: []` | No matching mapping facts (possible incomplete coverage) |
| Flask app in the v1.8 QA snapshot, `endpoints: []` | **I0 at that time** — extractor not shipped; not proof of zero routes |
| Proposed Go/Gin endpoints-only slice with no CALLS | **I1** — traces need CALLS; this was a planned example, not current Go support |
| Unsupported language | Structure may be inventory-only; intelligence empty |

UI/CLI empty states should stay valid. They must not say “no APIs exist.”


## Planned improvements

- Extend evidence-backed endpoint and test extraction from the current Java/Python patterns to the prioritized stacks in [ADR-013](../adr/ADR-013-multi-language-intelligence-architecture.md): TypeScript/NestJS, C#/ASP.NET Core, and Go/Gin, followed by Rust/Axum and Kotlin/Spring or Ktor.
- Improve relationship and test-subject resolution only where existing facts support it; do not imply language parity or compiler-level analysis.

## Maintenance

- Keep the current v1.9 section in sync when an extractor ships.
- Keep historical v1.8 QA values labeled with their snapshot date.
- Update [LANGUAGE_SUPPORT.md](LANGUAGE_SUPPORT.md) for structural parser/profile changes; this matrix tracks intelligence separately.
- Never mark a language FULL for intelligence merely because structural parsing works.

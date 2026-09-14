# Language Capability Matrix (Intelligence)

**Status:** Proposed for v1.8 (not a claim of shipped multi-language intelligence)
**Date:** 2026-09-14
**Complements:** [LANGUAGE_SUPPORT.md](LANGUAGE_SUPPORT.md) (structural coverage)
**Architecture:** [V1.8_MULTILANGUAGE_INTELLIGENCE_SPEC.md](V1.8_MULTILANGUAGE_INTELLIGENCE_SPEC.md),
[ADR-013](../adr/ADR-013-multi-language-intelligence-architecture.md)

This matrix describes **repository intelligence** (Endpoint, Test, TESTS, Trace,
Impact) separately from **structure** (modules, symbols, imports, `DEPENDS_ON`,
diagrams). A language can be structurally PARTIAL and intelligence **I0**.

Empty intelligence collections mean **no evidence was extracted**, not **the
runtime has no routes or tests**. Do not represent unsupported/unknown
intelligence as an invented absence of facts.

---

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
| **I3** | Java/Spring-class depth (Spring mappings, JUnit, CALLS, traces) | Current v1.7 Java/Spring bar |

I0 is a **capability**, not a failed analysis.

---

## 2. Summary (post–v1.7 QA)

Structural column is current product behavior. Intelligence column is **QA
result today** plus **v1.8 target** (not implemented by these docs).

| Language / stack | Structure today | Intelligence today (QA) | v1.8 target | Notes |
|------------------|-----------------|-------------------------|-------------|-------|
| Java / Spring | PARTIAL | **I3** — Endpoint, Test, TESTS, Trace working | **Tier 1** — maintain I3 | Non-regression baseline |
| Java (no Spring) | PARTIAL | Tests possible; endpoints empty unless mappings exist | Maintain | Honest empty endpoints |
| Python / Flask | PARTIAL | **I0** (intelligence 0) | **Tier 2A → I1+** | Structure working; no Flask extractor yet |
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

---

## 3. Intelligence dimensions

Consumers (API/CLI/Web) already expose these collections. Fill only from
canonical facts and composers.

| Dimension | Java/Spring today | 2A/2B until extractors exist |
|-----------|-------------------|------------------------------|
| Endpoint facts | Yes (Spring mappings) | Empty |
| Test facts | Yes (JUnit + name/file) | Empty |
| TESTS relationships | Yes (analyzer, not model) | Empty unless Test facts + CALLS/IMPORTS |
| Static traces | Yes (Endpoint + CALLS) | Empty or unresolved if no CALLS |
| Impact | Derived from model + traces | Empty categories without evidence |
| Graph `TESTS` edges | Projected from analyzer when nodes exist | None |

Downstream composers **do not** gain Python/Go/TS modes. They stay empty until
extractors emit facts.

---

## 4. Framework extraction (planned, not shipped)

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

---

## 5. Test discovery (planned, not shipped except Java)

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

---

## 6. How to read empty results

| Situation | Honest reading |
|-----------|----------------|
| Spring app, extractor ran, `endpoints: []` | No matching mapping facts (possible incomplete coverage) |
| Flask app today, `endpoints: []` | **I0** — extractor not shipped; not proof of zero routes |
| Go repo with Gin, traces empty after 2A endpoints-only slice | **I1** — traces need CALLS; not a composer bug |
| Unsupported language | Structure may be inventory-only; intelligence empty |

UI/CLI empty states should stay valid. They must not say “no APIs exist.”

---

## 7. Maintenance

- Update **this file** when an intelligence extractor ships (I0 → I1/I2).
- Update **LANGUAGE_SUPPORT.md** only for structural parser/profile changes
  (separate document; not modified by v1.8 architecture publication).
- Never mark a language FULL intelligence because structure works.

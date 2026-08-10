# Language Support Matrix

Living document of RepoLens language coverage. Statuses are honest:

| Status | Meaning |
|--------|---------|
| **FULL** | Structural extraction + imports/modules/symbols + dependency edges + graph, with high confidence for common idioms |
| **PARTIAL** | Meaningful `RepositoryModel` contribution; known gaps (inheritance, manifests, some constructs) |
| **DETECTED_ONLY** | Extension/manifest recognition without structural analysis (not used yet) |
| **UNSUPPORTED** | No profile; files may still be inventoried with a bare extension language tag |

Parsing prefers Tree-sitter when natives load (`tree-sitter-seart`); otherwise uses first-class structural fallback (`structural-fallback`). See [ADR-003](../adr/ADR-003-parsing-tree-sitter-language-profiles.md) and [ADR-011](../adr/ADR-011-optional-tree-sitter-first-class-fallback.md).

Language syntax lives only in `LanguageProfile` implementations under `repolens-parse`. Analyzers, CLI, and Web remain language-independent.

## Summary matrix

| Language | Detection | Parsing | Dependencies | Graph | Status |
|----------|-----------|---------|--------------|-------|--------|
| Java | Extension `.java` | Tree-sitter + fallback | Import → module `DEPENDS_ON` | Yes | PARTIAL |
| JavaScript | `.js` `.mjs` `.cjs` `.jsx` | Tree-sitter + fallback | Relative/require → `DEPENDS_ON` | Yes | PARTIAL |
| TypeScript | `.ts` `.tsx` | Tree-sitter + fallback | Relative imports → `DEPENDS_ON` | Yes | PARTIAL |
| Python | `.py` | Tree-sitter + fallback | `import` / `from` → `DEPENDS_ON` | Yes | PARTIAL |
| Go | `.go` | Tree-sitter + fallback | Import path → `DEPENDS_ON` | Yes | PARTIAL |
| Rust | `.rs` | Tree-sitter + fallback | `use` path segment → `DEPENDS_ON` | Yes | PARTIAL |
| C# | `.cs` | Tree-sitter + fallback | `using` → `DEPENDS_ON` | Yes | PARTIAL |
| Kotlin | `.kt` `.kts` | Tree-sitter + fallback | `import` → `DEPENDS_ON` | Yes | PARTIAL |
| C / C++ / Swift / PHP / Ruby / others | — | — | — | — | UNSUPPORTED |

None of the current languages are marked **FULL**: inheritance (`EXTENDS`/`IMPLEMENTS`), call graphs, and package-manager manifests are not extracted yet.

## Phase A languages (detail)

### Go (`go`) — PARTIAL

| Capability | Support |
|------------|---------|
| Detection | `.go` |
| Module identity | Containing directory path (one package per directory) |
| Imports | Single and grouped `import "..."` paths |
| Symbols | `struct`→class, `interface`→interface, type aliases→type, `func`→function, methods→method |
| Dependencies | Resolves when import path matches directory module name (e.g. `"demo/util"` → `demo/util`) |
| Limitations | No `go.mod` / external module graph; no embedding/implements edges; stdlib imports stay unresolved |

### Rust (`rust`) — PARTIAL

| Capability | Support |
|------------|---------|
| Detection | `.rs` |
| Module identity | File-path module (like JS/Python) |
| Imports | `use` paths; `crate::` / `super::` / `self::` prefixes stripped to first segment for resolution |
| Symbols | `struct`→class, `enum`→enum, `trait`→interface, `type`→type, `fn`→function, impl methods→method |
| Dependencies | Works when `use` first segment matches a file module name (e.g. `use crate::util::…` → `util`) |
| Limitations | No `Cargo.toml`; limited `mod` nesting; complex `use` trees / re-exports incomplete |

### C# (`csharp`) — PARTIAL

| Capability | Support |
|------------|---------|
| Detection | `.cs` |
| Module identity | `namespace` |
| Imports | `using` / `using static` (type/namespace name) |
| Symbols | class, interface, enum, struct/record→type, methods |
| Dependencies | Namespace-style resolution (same heuristics as Java packages) |
| Limitations | No `.csproj` / NuGet; no inheritance edges; top-level statements lightly covered |

### Kotlin (`kotlin`) — PARTIAL

| Capability | Support |
|------------|---------|
| Detection | `.kt`, `.kts` |
| Module identity | `package` |
| Imports | `import` paths |
| Symbols | class/object→class, interface, enum class→enum, top-level `fun`→function, members→method |
| Dependencies | Package/import resolution like Java |
| Limitations | No Gradle/Maven manifests; no inheritance edges; limited annotation/DSL coverage |

## Shared capture vocabulary

Profiles emit captures consumed by `ProfiledSourceAnalyzer`:

`module` · `import` · `class` · `interface` · `enum` · `type` · `function` · `method`

Tree-sitter queries and `fallbackExtract` must stay name-compatible.

## Multi-language repositories

A single inventory may contain multiple profiled languages. Each file is routed independently; all contribute to one `RepositoryModel`. Example: Java + Kotlin or Java + Go in the same tree is supported.

## Out of scope (not Phase A)

C, C++, Swift, PHP, Ruby, Tier 2/3 languages, DETECTED_ONLY status, LanguageCapability API, manifest ingestion, `EXTENDS`/`IMPLEMENTS`, UI redesign.

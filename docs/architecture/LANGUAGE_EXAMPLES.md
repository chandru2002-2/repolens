# Language Examples

Small, verifiable examples of the structural information each language
profile in [LANGUAGE_SUPPORT.md](LANGUAGE_SUPPORT.md) extracts. Every snippet
below is drawn from, and asserted by, the parse test suite, so the "extracted"
lines are reproducible by running `./gradlew :repolens-parse:test`.

Conventions:
- **module** — the module identity the file is routed under.
- **symbols** — extracted with a `SymbolKind`.
- **imports** — raw import text captured before dependency resolution.
- **Not extracted** — what this profile intentionally does not emit.

> All current profiles are **PARTIAL** (none are **FULL**). Package-manager
> manifests are not ingested, so the dependency graph is built from source
> imports only, not from `pom.xml` / `go.mod` / `Cargo.toml` / `package.json`.

---

## Java (`java`)

Source (from `PhaseALanguageParseTest.mixedJavaAndGoRepositoryBuildsCombinedModel`):

```java
// src/demo/core/Core.java
package demo.core;

public class Core {}
```

Extracted:
- module `demo/core` (language `java`)
- symbol `Core` — `CLASS`

Not extracted: `pom.xml` / Gradle dependency graph; full type-checking.
Java is the only Phase A language that additionally emits `EXTENDS` /
`IMPLEMENTS` and heuristic `CALLS` edges — but CALLS are name-based, not
compiler-level resolution, and unresolved receivers yield no edge.

---

## JavaScript (`javascript`)

Source (from `CrossLanguageParseTest.buildsJavascriptRepositoryModel`):

```js
// src/app.js
import util from "./util";
const helper = require("./helper");

export function main() {}
export class App {}
```

Extracted:
- module `src/app`
- symbols: `main` — `FUNCTION`, `App` — `CLASS`
- imports: `./util`, `./helper`

Not extracted: `package.json` module graph; `require` of `module.exports`
targets are captured as imports but not resolved to symbols.

---

## TypeScript (`typescript`)

Source (from `CrossLanguageParseTest.buildsTypescriptRepositoryModel`):

```ts
// src/index.ts
import type { User } from "./types";
import { load } from "./loader";

export function boot() {}
```

Extracted:
- module `src` (note: `index.ts` collapses to its parent directory)
- symbols: `boot` — `FUNCTION`
- imports: `./types`, `./loader`

Type-only symbols from imported files are also captured when present
(`User` — `INTERFACE`, `Id` — `TYPE`). Not extracted: `tsconfig` project
references; `type` imports do not produce runtime dependency edges.

---

## Python (`python`)

Source (from `CrossLanguageParseTest.buildsPythonRepositoryModel`):

```python
# app/main.py
from .utils import helper
import package.core

class Service:
    def run(self):
        pass

def start():
    pass
```

Extracted:
- module `app/main`
- symbols: `Service` — `CLASS`, `start` — `FUNCTION`, `run` — `METHOD`
- imports: `.utils`, `package.core`

Not extracted: `requirements.txt` / `pyproject.toml`; relative-import
`from .utils import helper` is captured as the module token `.utils`, but the
symbol `helper` it binds is not resolved into a cross-file dependency edge.

---

## Go (`go`)

Source (from `PhaseALanguageParseTest.buildsGoRepositoryModel`):

```go
// demo/app/app.go
package app

import "demo/util"

type Service struct{}

func Main() {}
func (s *Service) Start() {}
```

Extracted:
- module `demo/app` (language `go`)
- symbols: `Service` — `CLASS`, `Main` — `FUNCTION`, `Start` — `METHOD`
- import: `demo/util` (resolved to the `demo/util` module when it exists)

Not extracted: `go.mod` / external module graph; embedding and `implements`
edges; stdlib imports (e.g. `"fmt"`) stay unresolved.

---

## Rust (`rust`)

Source (from `PhaseALanguageParseTest.buildsRustRepositoryModel`):

```rust
// main.rs
use crate::util::helper;

pub struct App {}

pub fn main() {}
```

Extracted:
- module `main`
- symbols: `App` — `CLASS`, `main` — `FUNCTION`
- import: `util` (`crate::` prefix stripped to the first path segment)

Not extracted: `Cargo.toml`; deep `mod` nesting; complex `use` trees and
re-exports are only partially handled.

---

## C# (`csharp`)

Source (from `PhaseALanguageParseTest.buildsCSharpRepositoryModel`):

```cs
// Demo/App/App.cs
namespace Demo.App;
using Demo.Core;

public class App {
  public void Run() {}
}
```

Extracted:
- module `Demo.App`
- symbols: `App` — `CLASS`, `Run` — `METHOD`
- import: `Demo.Core`

Not extracted: `.csproj` / NuGet graph; inheritance edges (`EXTENDS` /
`IMPLEMENTS`) are not emitted for C#; top-level statements are only lightly
covered.

---

## Kotlin (`kotlin`)

Source (from `PhaseALanguageParseTest.buildsKotlinRepositoryModel`):

```kotlin
// demo/app/App.kt
package demo.app
import demo.core.Core

class App {
    fun run() {}
}

fun boot() {}
```

Extracted:
- module `demo.app`
- symbols: `App` — `CLASS`, `boot` — `FUNCTION`, `run` — `METHOD`
- import: `demo.core.Core`

Not extracted: Gradle / Maven manifests; inheritance edges; annotation and
DSL coverage is limited.

---

## Intentionally out of scope (all profiles)

C, C++, Swift, PHP, and Ruby have no profile and are inventoried only as
files with a bare extension language tag. No profile reads package-manager
manifests, and only Java emits inheritance and (heuristic) call edges.

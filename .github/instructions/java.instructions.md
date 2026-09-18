---
applyTo: "**/*.java"
---

# Java Instructions

- Target Java 21 and follow the existing Gradle/JUnit conventions.
- Prefer clear domain types and immutable data over primitive/stringly-typed state when a meaningful type exists.
- Use records for suitable immutable DTO/value-like data.
- Keep methods focused and avoid premature abstraction.
- Prefer composition and dependency injection over inheritance where practical.
- Avoid raw types, unchecked casts, hidden global state, and reflection unless there is a documented reason.
- Preserve module dependency direction.
- For graph algorithms, make traversal strategy and complexity explicit; avoid recursion where repository size can make stack depth a concern.
- Add tests for new behavior and regression cases.
- Do not optimize based on intuition alone; use measurements/profiling for performance-sensitive changes.

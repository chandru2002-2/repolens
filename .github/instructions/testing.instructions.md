---
applyTo: "**/*Test.java,**/*Tests.java,**/*Test.kt,**/test/**"
---

# Testing Instructions

- Follow the existing JUnit 5 and Gradle test conventions.
- Test behavior and architectural invariants, not implementation details unnecessarily.
- Add regression tests for discovered bugs.
- Keep unit tests deterministic and isolated.
- Add integration tests when behavior crosses module, filesystem, ingestion, API, or serialization boundaries.
- For architecture changes, preserve or extend boundary/ArchUnit tests.
- Before finishing, run the narrowest relevant tests and then `./gradlew test` when practical.

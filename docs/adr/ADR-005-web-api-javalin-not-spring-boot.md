# ADR-005: Web API Adapter Technology — Javalin (No Spring Boot in v1)

- Status: Accepted
- Date: 2026-08-10

## Context

The CLI-era rule "never Spring Boot" no longer applies automatically. The Web API needs an HTTP adapter, but RepoLens must stay lightweight.

## Decision

- Core remains framework-free.
- Initial Web adapter uses **Javalin** (thin HTTP), introduced when Web endpoints are implemented.
- **Spring Boot is not selected for v1.**

Revisit Spring Boot only if security, operator, or enterprise integration needs clearly justify it.

## Consequences

- Small API surface over core
- Explicit wiring instead of heavy auto-config
- Contributors learn less framework magic to contribute to core

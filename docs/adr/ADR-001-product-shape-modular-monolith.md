# ADR-001: Product Shape — Web + CLI Modular Monolith

- Status: Accepted
- Date: 2026-08-10

## Context

RepoLens is evolving from a CLI-only analyzer concept into a repository intelligence platform with an interactive Web UI and a retained CLI.

## Decision

Ship as a **modular monolith**:

- shared `repolens-core` engine
- thin `repolens-cli` and `repolens-web` adapters
- single deployable backend for v1 Web
- no microservices, Kubernetes, or required cloud infra initially

## Consequences

- Faster local development and contribution
- Clear module boundaries without distributed-system cost
- Can extract services later if scale demands it

# ADR-007: API Versioning and JSON Contracts

- Status: Accepted
- Date: 2026-08-10

## Context

CLI machine output and Web API should share contracts so consumers do not fork parsers of RepoLens output.

## Decision

- Maintain `repolens-api-model` DTOs as the stable external contract
- Start at **schemaVersion = `v1`**
- Version HTTP routes under `/v1/...` when Web is implemented
- Map domain → DTO in adapters; never expose internal parser types

## Consequences

- Shared CLI/Web serialization path
- Controlled breaking changes via version bumps
- Slight mapping overhead

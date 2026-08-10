# ADR-009: Licensing and Third-Party Grammar Policy

- Status: Accepted
- Date: 2026-08-10

## Context

Tree-sitter grammars and native libraries have varied licenses. RepoLens aims to be open source and redistribution-friendly.

## Decision

- Prefer MIT/Apache-2.0 compatible dependencies
- Document each bundled grammar/native artifact and its license before inclusion
- Do not vendor incompatible copyleft into the default distribution without an explicit ADR update
- Project license choice (MIT vs Apache-2.0) remains an open product decision, but dependency policy above applies regardless

## Consequences

- Slightly slower onboarding of grammars
- Safer OSS redistribution
- Clear audit trail in docs

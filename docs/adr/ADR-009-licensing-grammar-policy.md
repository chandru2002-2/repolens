# ADR-009: Licensing and Third-Party Grammar Policy

- Status: Accepted
- Date: 2026-08-10

## Context

RepoLens is licensed under the MIT License, as recorded in the repository's `LICENSE` file. Tree-sitter grammars and native libraries have their own licenses, separate from the RepoLens project license.

## Decision

- The RepoLens project license is MIT.
- Prefer MIT/Apache-2.0 compatible dependencies
- Document each bundled grammar/native artifact and its license before inclusion
- Do not vendor incompatible copyleft into the default distribution without an explicit ADR update

## Consequences

- Slightly slower onboarding of grammars
- Safer OSS redistribution
- Clear audit trail in docs
- The MIT project license does not change or replace the license terms of third-party grammars and parser libraries

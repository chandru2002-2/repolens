# ADR-008: Optional AI Explanation Port

- Status: Accepted
- Date: 2026-08-10

## Context

AI can improve explanations, but must not become the foundation of analysis (unlike diagram-only tools that generate architecture via LLMs).

## Decision

- Core analysis works with **AI disabled**
- Optional explanation port consumes `RepositoryModel` / `AnalysisResult` / `GraphView` (+ cited snippets)
- Provider adapters are optional modules; no hard dependency on OpenAI/Claude/Gemini/Ollama

## Consequences

- Deterministic core value
- Clear extension point for later features
- Avoids cloud cost and privacy coupling in default installs

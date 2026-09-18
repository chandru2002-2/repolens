---
applyTo: "**/*.java,**/benchmarks/**,**/docs/benchmarks/**"
---

# Performance Instructions

- Do not use estimated or invented performance numbers.
- Establish a reproducible baseline before claiming an optimization.
- Prefer JMH for JVM microbenchmarks and representative integration measurements for repository-scale behavior.
- Record workload, repository characteristics, JVM version, hardware/environment, warmup/measurement configuration, and relevant parameters.
- Measure throughput, latency, allocations, or memory only when those metrics answer the actual performance question.
- Compare before/after results using the same workload and methodology.
- Consider algorithmic complexity, allocation pressure, object lifetime, graph size, parser cost, I/O, and concurrency before low-level tuning.
- Keep performance changes isolated enough that their effect can be measured and reviewed.

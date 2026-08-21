# ADR-006: Runtime logging uses facade/backend separation

- Status: Accepted
- Scope: Runtime observability
- Decision: `simulation`/`core` log through SLF4J while platform composition chooses the concrete backend; logging remains structured, bounded and strictly observational.

## Context

A long-running deterministic simulation needs durable evidence beyond screenshots, but console-only output is hard to correlate and an engine-specific logger would duplicate generic infrastructure. Authoritative/headless modules also should not depend directly on a desktop logging backend.

## Decision

```text
simulation / core
      ↓
   SLF4J API
      ↓
platform/runtime backend
      ↓
desktop Logback -> sparse console + bounded persistent files
```

Loggers are scoped by class/package/subsystem. Events use producer-owned identifiers plus structured key/value fields. Repeating streams are sampled or aggregated. Logging never affects ordering, state, scheduling or results.

## Why

A facade keeps lower modules platform-neutral while a standard backend supplies filtering, formatting, rotation and context. Structured text remains readable and can later feed tooling without changing domain APIs.

## Consequences

- Runtime events can be correlated without adding diagnostic state to domain owners.
- New subsystems add local categories/events without a central enum.
- Disk usage remains bounded.
- Hot-loop logging is treated as performance-sensitive work.
- Backend changes do not change simulation ownership.

## Alternatives considered

`System.out`/`Gdx.app.log` alone were rejected as the primary durable channel. A custom EvoForge logger and direct Logback dependency from all modules were rejected. Full per-tick state dumps were rejected as noisy and expensive.

## Current implementation

`simulation`/`core` use SLF4J; the desktop application supplies Logback configuration and framework logging integration. Tests verify behavior from typed domain state rather than parsing logs as authoritative truth.

## Related documentation

- [Runtime Logging Guide](../guides/runtime-logging.md)
- [Architecture](../architecture.md)

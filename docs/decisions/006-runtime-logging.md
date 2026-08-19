# ADR-006: Runtime logging uses facade/backend separation

- Status: Accepted
- Scope: Runtime observability
- Decision: `simulation`/`core` log through SLF4J while platform composition chooses the concrete backend; logging remains structured, bounded and strictly observational.

## Context

A long-running deterministic simulation needs durable evidence beyond screenshots, but console-only output is hard to correlate and an engine-specific logger would duplicate generic infrastructure. Authoritative/headless modules also should not depend directly on a desktop logging backend.

## Decision

The logging boundary is:

```text
simulation / core
      ↓
   SLF4J API
      ↓
platform/runtime backend
      ↓
desktop Logback -> sparse console + bounded persistent files
```

Loggers are scoped by class/package/subsystem. Events use producer-owned open identifiers plus structured key/value fields. Desktop sessions carry a session identifier and scenario context; authoritative tick/entity fields are attached explicitly when relevant.

Console defaults to sparse informational output while project file logs retain deeper debug evidence. Repeating streams are sampled/aggregated. libGDX logging is bridged into the same backend. Logging never affects ordering, state, scheduling or results.

## Why

A facade keeps lower modules platform-neutral while a standard backend supplies filtering, formatting, rotation and context. Structured text remains readable to developers and can later feed machine tooling without changing domain APIs.

## Consequences

- One runtime session can be shared as a coherent diagnostic artifact.
- Startup/scenario/command/performance facts can be correlated.
- New subsystems add local categories/events without editing a central enum.
- Disk usage stays bounded.
- Hot-loop logging must be reviewed like any other performance-sensitive work.
- Backend changes do not change simulation ownership.

## Alternatives considered

`System.out`/`Gdx.app.log` alone were rejected as the primary durable channel. A custom EvoForge logger was rejected as generic infrastructure duplication. Direct Logback dependency from all modules was rejected because it couples headless simulation to one backend. JSON-only output and asynchronous logging were deferred until a real consumer/performance need exists. Full per-tick state dumps were rejected as noisy and expensive.

## Current implementation

`simulation`/`core` use SLF4J; the LWJGL3 desktop application supplies Logback configuration, rolling file output and framework logging bridge. Generated-world audits/diagnostics can emit canonical structured events, but tests compare typed diagnostic records rather than parsing log text.

## Related documentation

- [Runtime Logging Guide](../guides/runtime-logging.md)
- [Generated World Diagnostics](../systems/tooling/generated-world-diagnostics.md)
- [Architecture](../architecture.md)

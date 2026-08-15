# Runtime Logging Architecture

## Context

EvoForge is becoming a long-running deterministic simulation with several interacting world, movement, agent and presentation subsystems. A screenshot is often insufficient to explain a runtime problem, while the previous diagnostic output consisted primarily of one console-only visualizer performance line.

The project needs a durable runtime evidence channel that is easy to attach to a debugging discussion, remains useful as subsystem count grows, does not flood normal console use and does not couple simulation mechanics to a desktop-specific logging implementation.

Game-development tooling also benefits from preserving temporal context around a failure rather than requiring the developer to reconstruct behavior from the final visible frame.

## Decision

EvoForge uses a facade/backend logging boundary:

```text
simulation / core
      |
      v
   SLF4J API
      |
      v
runtime/backend composition
      |
      +--> desktop Logback -> console
      |
      +--------------------> bounded files
```

The rules are:

1. `simulation` and `core` depend only on the SLF4J API.
2. The LWJGL3 desktop module supplies Logback as the runtime provider. A different backend can be composed for another platform without changing simulation call sites.
3. Loggers are scoped by class/package or subsystem category. EvoForge does not introduce one global logging service or project-wide event enum.
4. Events use structured key/value fields and open producer-owned event identifiers such as `scenario.start` or `command.move_to`.
5. Each desktop launch receives a session UUID. The active scenario is propagated as logging context, while semantic simulation events add authoritative tick/entity fields explicitly when relevant.
6. Console output defaults to sparse `INFO+`. Project loggers write `DEBUG+` to a persistent runtime file by default.
7. Runtime files use bounded size-and-time rolling retention rather than growing indefinitely.
8. libGDX `ApplicationLogger` is bridged into the same logging backend so framework diagnostics are not stranded in a second console-only channel.
9. The desktop launcher records process/environment startup and uncaught exceptions; the application records backend/OpenGL readiness and disposal.
10. Repeating diagnostic streams are aggregated or sampled. The existing visualizer performance telemetry becomes one structured `DEBUG` event per measurement window rather than one large console string.
11. Logging is strictly observational: simulation state, determinism, ordering, scheduling and results must never depend on a log level, backend, appender or emitted message.
12. Asynchronous logging is not introduced in the foundation. Current expected event volume is deliberately small, and synchronous behavior keeps crash evidence and configuration simple. A measured logging bottleneck may justify changing the backend later without changing domain APIs.

## Why SLF4J + Logback

A facade allows lower modules and future libraries to express diagnostic events without selecting a concrete storage/formatting implementation. The desktop backend can then own rotation, formatting and routing as an application concern.

Structured key/value fields provide a useful middle ground for EvoForge's current developer workflow: logs remain immediately readable and attachable as plain text while preserving machine-parsable event facts for future tooling.

## Initial scope

The first slice instruments only high-value boundaries:

- desktop/application lifecycle and environment;
- uncaught failures;
- scenario enter/leave/restart;
- interactive MoveTo and CancelMove command outcomes;
- periodic visualizer performance summaries;
- libGDX logging bridge.

It deliberately does not add verbose events to every scheduler callback, Water transfer, path node, cell update or Agent evaluation. Those streams can become extremely large and should be introduced only where a real debugging question establishes the required granularity.

## File policy

Desktop logs live under `logs/` by default and are ignored by Git. The checked-in configuration keeps the active file plus compressed size/time archives with bounded history and total disk use.

The output directory and project verbosity can be overridden with JVM properties for an investigation. Logback's own external-configuration mechanism remains available for advanced experiments, but the checked-in configuration is the normal reproducible default.

## Alternatives considered

### `System.out` / `Gdx.app.log` only

Rejected as the primary architecture. It is adequate for temporary local diagnostics but does not give the project one persistent, category-aware, level-aware, rotating evidence channel.

### A custom EvoForge logger

Rejected. Formatting, filtering, provider selection, context and rolling files are generic infrastructure; reimplementing them would create maintenance work without simulation-domain value.

### Direct Logback dependency from all modules

Rejected because it would couple authoritative/headless simulation code to one desktop logging backend and make future platform composition unnecessarily expensive.

### JSON-only logs from the first slice

Deferred. Machine-oriented JSON is useful for ingestion, but the current primary consumer is a developer attaching and reading a local runtime file. Structured key/value logging preserves an upgrade path without sacrificing immediate readability.

### Full per-tick state dumps

Rejected. They are expensive, noisy and obscure the high-value causal sequence. Authoritative lookups, focused traces, replay/scenario fixtures and profiler measurements remain better tools for bulk state inspection.

## Consequences

Benefits:

- one runtime session can be shared as a coherent diagnostic artifact;
- startup, scenario, command and performance facts can be correlated without reconstructing them from screenshots;
- normal console output stays readable while deeper evidence remains on disk;
- new subsystems can add categories/events locally without central-schema edits;
- rolling retention prevents an unattended development run from consuming unbounded disk space;
- backend changes and richer developer tooling remain possible without changing simulation ownership.

Costs and responsibilities:

- the desktop distribution gains a logging provider dependency;
- event names and important field meanings should remain stable enough to compare sessions;
- hot-loop logging must be reviewed like any other performance-sensitive work;
- logs must be reviewed for accidental sensitive data as future player-facing or network features appear;
- logging itself is evidence, not a substitute for invariant tests, deterministic traces or representative profiling.

See [Runtime Logging](../guides/runtime-logging.md).
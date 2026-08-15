# Runtime Logging

EvoForge runtime logs are a reproducible diagnostic artifact for understanding what actually happened during a desktop simulation session. They complement tests, visual inspection and profiler measurements; they are not simulation state and must never influence authoritative behavior.

## Quick diagnostic workflow

When a runtime problem is difficult to explain from a screenshot alone:

1. start the desktop visualizer normally;
2. reproduce the problem in the smallest representative scenario;
3. note the scenario and the approximate action that triggered the problem;
4. close the application normally when possible;
5. attach `logs/evoforge.log` to the debugging discussion.

The log already carries a session identifier and active scenario context, so a single file is normally enough to correlate startup information, scenario lifecycle, user commands, performance windows and an uncaught failure from the same run.

Runtime logs are plain text. Review them before sharing outside the project if future code adds sensitive values.

## Files and retention

The desktop backend writes:

```text
logs/
  evoforge.log
  archive/
    evoforge.YYYY-MM-DD.N.log.gz
```

The active file rolls by both time and size. Default retention is deliberately bounded:

- maximum active/archive segment size: 10 MB;
- maximum history: 14 days;
- maximum total archive size: 200 MB.

`logs/` is ignored by Git and is never a source of authoritative project data.

## Levels

Use levels according to diagnostic meaning rather than subsystem importance:

| Level | Intended use |
| --- | --- |
| `ERROR` | Unhandled failures, violated runtime invariants or an operation that cannot continue correctly. |
| `WARN` | Abnormal or degraded behavior where the process can continue and investigation is useful. |
| `INFO` | Sparse lifecycle and major state-boundary events useful in almost every diagnostic session. |
| `DEBUG` | Detailed diagnostic events, user-command outcomes and periodic performance summaries. |
| `TRACE` | Narrow, temporary deep tracing for a specific subsystem or investigation. |

Do not emit per-cell, per-object or per-tick `INFO` streams merely because the data is available. Repeating high-volume facts should be aggregated, sampled, traced at a narrow category, or exposed through an existing authoritative diagnostic lookup instead.

## Structured event convention

EvoForge uses ordinary logger categories plus structured key/value fields. An event has a stable open identifier such as:

```text
event=runtime.start
event=scenario.start
event=command.move_to
event=perf.visualizer
```

Event names are strings owned by the producer, not members of a project-wide enum. New mechanics can add their own logger and event names without editing a central catalog.

Every line includes:

- timestamp;
- level;
- thread;
- logger/category;
- runtime `session` identifier;
- active `scenario` when one exists;
- event-specific key/value fields;
- human-readable message;
- exception stack trace when present.

When a simulation event has semantic time, include its authoritative `tick`. When an event concerns an entity, prefer its stable ID and small scalar facts over dumping entire mutable objects.

## Initial event surface

The first logging slice intentionally covers the highest-value diagnostic boundaries rather than instrumenting every subsystem at once.

### Runtime lifecycle

- `runtime.start` — desktop process startup, Java/OS/architecture and resolved log directory;
- `runtime.stop` — normal desktop process return;
- `runtime.uncaught` — uncaught exception and thread;
- `app.ready` — libGDX/backend/window/OpenGL environment after application creation;
- `app.dispose` — application disposal.

### Scenario lifecycle

- `scenario.start`;
- `scenario.stop`;
- `scenario.restart_request`;
- `scenario.exit_request`.

The current scenario ID is also stored in logging context so events from other project loggers automatically carry it while the scenario is active.

### Interactive commands

- `command.move_to` — object, target, tick and accepted/rejected result;
- `command.cancel_move` — object, tick and accepted/rejected result.

Rejected commands also include their domain result code. Future player/debug commands should follow the same pattern at the command boundary instead of reconstructing intent later from unrelated subsystem logs.

### Performance

`perf.visualizer` is emitted once per diagnostic window at `DEBUG`. It replaces the former large console-only performance string and records structured average/max frame, update, landscape, overlay and HUD timings together with FPS and heap measurements.

The console stays readable because it defaults to `INFO+`; the runtime file defaults to project `DEBUG`, so performance data remains available for later diagnosis without continuously occupying the console.

### libGDX

The libGDX `ApplicationLogger` is bridged into the same backend as `gdx.log`, `gdx.debug` and `gdx.error`, preserving its tag and throwable where present. This avoids maintaining a second independent console-only diagnostic channel.

## Configuration

The default project log level for the file is `DEBUG`. It can be changed for one launch with a JVM system property:

```text
-Devoforge.log.level=TRACE
```

The output directory can be overridden:

```text
-Devoforge.log.dir=/path/to/logs
```

Advanced investigations may replace the Logback configuration without changing EvoForge code:

```text
-Dlogback.configurationFile=/path/to/logback.xml
```

For normal development, use the checked-in defaults so diagnostic files are comparable between sessions.

## Engineering rules

Runtime logging follows these project constraints:

1. Logging is observational only. Simulation results, scheduling, ordering, random choices and authoritative state must not depend on whether a logger or level is enabled.
2. Simulation and core code depend on the SLF4J facade, not on the desktop Logback implementation. The backend is a runtime composition concern.
3. Prefer structured fields over long concatenated diagnostic strings.
4. Prefer subsystem/class categories over one global logger.
5. Do not create a universal event-type enum or central switch as new mechanics appear.
6. Do not log secrets, credentials or unnecessary personal/user data.
7. Avoid hot-loop logging. Aggregate recurring measurements and add narrow `TRACE` instrumentation only when an investigation justifies it.
8. A log line should help answer a concrete question: what happened, when, to which stable entity/context, and with what result.

## Extension path

The current foundation is intentionally small but backend-neutral. Future slices can add subsystem events for Movement, Water, Agent decisions, scheduler anomalies or world mutations through ordinary SLF4J categories without changing the storage model.

If runtime diagnosis later requires richer tooling, the same event stream can feed an in-memory ring buffer, an in-game developer log viewer, a crash/support bundle, a machine-readable sink or a remote observability backend. Those are presentation/support consumers; they must not become simulation authorities.

## Manual acceptance for logging changes

Before merging a change that materially affects runtime logging:

1. launch the desktop visualizer;
2. open a scenario and perform at least one logged command;
3. restart or leave the scenario;
4. let at least one performance window elapse;
5. close the app;
6. inspect `logs/evoforge.log` for session/scenario context, structured command/performance events and normal shutdown;
7. confirm console output remains sparse and that the log file does not grow uncontrollably during an ordinary session;
8. for an error-path change, verify the exception and stack trace are retained without swallowing or changing the failure.

A screenshot can show appearance. The runtime log should show the corresponding sequence of facts.
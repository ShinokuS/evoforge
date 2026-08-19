# ADR-019: Generated-world warm-up is explicit observation

- Status: Accepted
- Scope: Generated-world developer tooling
- Decision: Warm-up advances an already started generated world only through the ordinary production stepper to explicit absolute checkpoints and records diagnostics; it does not define equilibrium, balance or alternate physics.

## Context

A newly generated runtime may need simulation time for Water/Soil/other scheduled systems to evolve before developers compare states. If warm-up directly calls domain systems, uses simplified rules or decides when the world is “healthy”, it becomes a second simulation/balancing engine.

## Decision

`GeneratedWorldWarmup` accepts strictly increasing non-negative absolute simulation ticks. It repeatedly calls `SimulationRuntime.stepper().advance()` and captures `GeneratedWorldDiagnostics` at requested checkpoints.

```text
GeneratedWorldRuntime
       ↓ ordinary SimulationStepper
requested checkpoint
       ↓
GeneratedWorldDiagnosticsProbe
       ↓
immutable observation trace
```

Warm-up contains no equilibrium/viability/flood threshold or calibration mutation. Representative fixed seeds/rates/ticks are developer audit inputs, not gameplay presets.

## Why

Using the exact production execution path makes checkpoint traces reproducible and prevents tooling from creating hidden world laws.

## Consequences

- CI and manual audits can compare the same deterministic observations.
- Larger audits can remain opt-in without redefining production semantics.
- Future evaluators may interpret the trace separately.
- There is no universal production warm-up duration.

## Alternatives considered

Automatic stop-on-small-Water-delta, built-in PASS/FAIL balance rules and direct Water/precipitation/evaporation calls were rejected.

## Current implementation

Warm-up code/tests live under `world.diagnostics.warmup` after the Stage 0 package cleanup. The `:simulation:generatedWorldAudit` Gradle task runs representative ordinary runtime stepping and prints the same diagnostic vocabulary used elsewhere.

## Related documentation

- [Generated World Warm-up](../systems/tooling/generated-world-warmup.md)
- [Generated World Diagnostics](../systems/tooling/generated-world-diagnostics.md)
- [Time and Scheduling](../systems/foundations/time.md)

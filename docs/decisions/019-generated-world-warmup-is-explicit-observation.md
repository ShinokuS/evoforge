# Decision 019 — Generated-world warmup is explicit advancement and observation

**Status:** Accepted

## Problem

A newly materialized generated world may need simulation time for existing hydrology and later environmental systems to evolve before developers can judge its behavior. If warmup itself decides when the world is “healthy”, adjusts parameters, or runs alternate simplified physics, it becomes a hidden balancing engine and a second simulation mode.

We also need identical evidence in CI and developer/manual runs so terrain and Water changes can be compared against the same seeds and checkpoints.

## Decision

`GeneratedWorldWarmup` is a deterministic control/tooling boundary over an already started `GeneratedWorldRuntime`.

It accepts explicit, strictly increasing **absolute simulation ticks**, advances only through `SimulationRuntime.stepper()`, and captures `GeneratedWorldDiagnostics` at those checkpoints.

```text
GeneratedWorldRuntime
        ↓
ordinary SimulationStepper
        ↓
checkpoint tick
        ↓
GeneratedWorldDiagnosticsProbe
        ↓
immutable trace of observed facts
```

Warmup does not define equilibrium, viability, desired Water level, acceptable flooding, climate targets or calibration parameters. It does not mutate Atlas facts and it does not call domain systems directly.

The first mandatory CI matrix uses several fixed seeds and internal engine-test HydroClimate specifications. Those exact rates are verification inputs, not user-facing climate presets or gameplay configuration.

An opt-in Gradle task, `:simulation:generatedWorldAudit`, runs representative warmups and prints the same canonical `event=world.generated.audit` snapshot format used by runtime logging. This is diagnostic presentation only; tests and evaluators consume the structured snapshot rather than parsing text.

## Consequences

- warmup cannot silently introduce different world laws from normal runtime;
- a checkpoint trace is reproducible from seed, content setup and requested ticks;
- CI can compare complete traces across replay and across future implementation changes;
- developers can run a verbose audit without enabling per-tick logging;
- future viability/calibration code receives observed facts rather than being embedded in warmup;
- there is no universal production warmup duration yet.

## Performance

The required CI matrix is deliberately small and correctness-oriented. Larger or longer audits remain opt-in until profiling shows they are appropriate for normal CI.

Wall-clock time is not part of `GeneratedWorldDiagnostics` and is not a viability criterion. Performance profiling remains a separate measurement concern.

## Rejected directions

Stopping warmup automatically when Water changes fall below an arbitrary threshold was rejected because no accepted equilibrium model exists yet.

Embedding PASS/FAIL balance rules in `GeneratedWorldWarmup` was rejected because observation and evaluation have different ownership.

Calling Water, precipitation or evaporation systems directly from warmup was rejected because generated worlds must evolve through the same production scheduler as any ordinary runtime.

Exposing the internal matrix HydroClimate rates as user presets was rejected because human-facing world generation controls must remain semantic and minimal; generator/calibration layers derive technical rates.

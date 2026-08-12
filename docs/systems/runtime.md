# Runtime Composition

## Purpose

Build the production simulation once, then expose only the capabilities appropriate to runtime consumers.

## Owns

Bootstrap/composition only. `SimulationAssembly` may wire mutable owners during setup; it is not itself an authoritative domain owner.

## Public runtime boundary

`SimulationAssembly.start()` yields a `SimulationRuntime` exposing:

```text
submit   external Command submission
SimulationTime
SimulationStepper
SimulationView
```

`SimulationView` groups read-only world capabilities used by presentation and other observers. It exposes lookups, not mutable systems.

## Invariants

- setup mutation does not leak through `SimulationRuntime`;
- presentation receives `SimulationView`, `SimulationTime` and `SimulationStepper`, not a service-locator `World`;
- adding a read capability is explicit and reviewable;
- production composition and test fixtures may differ in construction ergonomics without changing domain semantics.

## Diagnostics and tests

`VisualizerBoundaryTest` reflects over the visualizer constructor so mutable simulation dependencies cannot silently enter presentation.

## Deferred

Queued/asynchronous external command delivery, networking and persistence composition remain future consumers.

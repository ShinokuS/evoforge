# ADR-021: World preparation and modular calibration boundary

- Status: Accepted
- Scope: World-generation preparation and runtime handoff
- Decision: Generation/calibration are one-way pre-runtime phases producing immutable typed data; runtime bootstrap consumes that data once and started simulation never depends on generators/calibrators.

## Context

World generation, calibration and live simulation answer different questions. Mixing them risks long-lived generator objects influencing a running world, runtime consumers branching on which calibrator produced a value, one central Balancer/service registry, algorithm-specific knobs leaking into authored intent, and preparation APIs reaching mutable runtime state.

## Decision

Canonical ownership is:

```text
semantic WorldSpec / WorldGenerationIntent + provenance
            ↓
world-generation algorithms
            ↓
WorldAtlas immutable generated facts
            ↓
domain preparation/calibration algorithms
            ↓
PreparedGeneratedWorld immutable prepared inputs
            ↓
---------------- START BOUNDARY ----------------
            ↓
GeneratedWorldRuntimeBootstrap
            ↓
SimulationRuntime mutable owners/processes
```

Calibration is modular, not one global Balancer. Every calibrator has explicit typed domain input/output, a replaceable algorithm contract, immutable semantically meaningful result and no authority to mutate another domain to force balance. Algorithm-specific runtime parameters are compiled separately when they are not true world/domain facts.

Preparation composition is explicit and typed. A central orchestrator may order real data dependencies but may not hide arbitrary extensions in a generic context/bag. Runtime code never calls calibrators or branches on their concrete implementation.

## Why

The boundary keeps causal order and ownership reviewable, lets generation/calibration be tested without a running world, and makes runtime replay/debugging free from hidden preparation-time writers.

## Consequences

- New domains can add narrow calibrators independently.
- Authored meaning remains semantic while exact operating values remain model/domain owned.
- Algorithm replacement does not require runtime consumers to change.
- Runtime bootstrap becomes a one-way compilation/materialization step.
- New genuine prepared facts may require explicit typed composition changes; this cost is intentional.
- Compatibility facades may remain temporarily but do not redefine the canonical lifecycle.

## Alternatives considered

A universal Balancer, `Map<String,Object>`/`Map<Class<?>,Object>` context, long-lived calibrators inside runtime, algorithm parameters promoted to user intent and hidden cross-domain mutation were rejected. A universal formation/pattern framework is also deferred until multiple real consumers prove one exists.

## Current implementation

Stage 0 makes this decision concrete through `WorldGenerationAlgorithms`, `WorldPreparationAlgorithms`, `GeneratedWorldPreparation`, `PreparedGeneratedWorld` and `GeneratedWorldRuntimeBootstrap`. V12 uses `WorldGenerationIntent -> V12LandformCalibrator -> V12LandformCalibration + V12LandformRecipe -> V12LandformElevationAlgorithm`. Soil uses semantic profile -> local formation -> composition -> Saxton-Rawls calibration -> runtime compilation. Rainfall regime preparation similarly separates climate facts from algorithm-specific pulse compilation.

## Related documentation

- [World Generation](../systems/world-generation/overview.md)
- [Generated World Runtime](../systems/world-generation/generated-world-runtime.md)
- [Terrain Generation](../systems/world-generation/terrain-generation.md)
- [Soil Hydraulics](../systems/environment/soil-hydraulics.md)
- [Rainfall Regime Calibration](../systems/environment/rainfall-calibration.md)

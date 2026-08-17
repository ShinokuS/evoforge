# Generated World Runtime

## Purpose

Keep generated-world preparation and lived simulation as two explicit phases with one-way data flow.

Generation/calibration prepare immutable inputs. Runtime bootstrap consumes those inputs once. After `SimulationRuntime` starts, generation and calibration no longer participate in world evolution.

## Canonical two-phase path

```text
WorldSpec + seed
      ↓
WorldGenesis
      ↓
GeneratedWorldPreparation          PURE
      ↓
WorldAtlas
  ├─ ElevationField
  ├─ GeologyField
  ├─ ClimateNormalsField
  ├─ DrainageField
  ├─ HydrographyField
  └─ SurfaceHydrologyField
      ↓
stable TerrainMaterialField
      ↓
PreparedGeneratedWorld
      ↓
================ START BOUNDARY ================
      ↓
GeneratedWorldRuntimeBootstrap
  ├─ bind stable material keys to runtime definitions
  ├─ materialize initial Terrain / finite Water
  ├─ compose runtime atmosphere once
  └─ SimulationAssembly.start()
      ↓
SimulationRuntime
```

`GeneratedWorldPreparation` has no runtime or scheduler dependency. `GeneratedWorldRuntimeBootstrap` has no generator/calibrator dependency.

`GeneratedWorldBootstrap` remains only a convenience facade for older one-call callers; internally it delegates to the two explicit phases. Architecture-sensitive code should use the two phases directly.

## Generated facts vs runtime state

`WorldAtlas` contains durable generated facts only. Runtime interfaces do not belong to `world.atlas`.

Examples:

- `ClimateNormalsField` — durable long-term climate fact;
- `SurfaceHydrologyField` — generated initial-condition fact;
- `WeatherState` — mutable current runtime atmosphere;
- Water/Soil stores — mutable simulation state.

Initial finite Water is materialized once from prepared facts. Thereafter ordinary runtime Water mechanics own it.

## Runtime atmosphere

Atmosphere is composed through the open `AtmosphericRuntimePlan` seam.

```text
prepared immutable facts
        ↓
AtmosphericRuntimePlan        called once before start
        ↓
AtmosphericRuntimeComposition
  ├ AtmosphericWaterForcing
  └ optional WeatherLookup
        ↓
AtmosphericWaterForcingSystem
        ↓
Evaporation / Precipitation
        ↓
Water / Soil
```

`AtmosphericWaterForcingSystem` consumes one `AtmosphericWaterForcing` contract and never branches on the concrete model.

Built-in plans currently include:

- disabled atmosphere;
- historical direct ClimateNormals forcing for compatibility;
- calm current `WeatherState` forcing.

Applications may supply another `AtmosphericRuntimePlan` without editing a central enum or forcing consumer. `AtmosphericForcingPolicy` is retained only as a compatibility selector for old callers.

Current weather is externally observable through read-only `WeatherLookup`; mutable `WeatherState` remains an implementation detail of simulation-owned weather evolution.

## Eventful weather

`WeatherDriver` evolves `WeatherState`. `AlternatingRainfallPulseDriver` is one replaceable deterministic rainfall algorithm, not a universal definition of rain.

Changing physical rates are integrated interval-by-interval through exact rational carry. A rain event beginning late in simulation therefore cannot retroactively accumulate rainfall for earlier dry ticks.

Low-level rainfall pulse parameters are not authored world intent. They are intended to be compiled from future algorithm-independent calibrated rainfall-regime data.

## Calibration boundary

Calibration belongs before the start boundary. Domain calibrators create immutable prepared parameters and disappear before simulation begins.

They must be narrow, typed and independently replaceable; there is no global Balancer and no generic calibration service registry. See [Decision 020](../decisions/020-world-preparation-and-calibration-boundary.md).

## Algorithm composition

World generation uses explicit typed algorithm contracts. `WorldGenerationAlgorithms` groups replaceable algorithms without constructor explosion or a generic service locator.

Execution order follows real dependencies; no fake dependency is added merely to create a visually linear pipeline.

## Diagnostics and warmup

Diagnostics observe the ordinary started `SimulationRuntime`. Warmup advances that runtime through the same scheduler and does not re-enter generation or calibration.

A generated-world audit may compare prepared facts and runtime state, but it does not grant preparation code authority over the running world.

See [World Atlas](world-atlas.md), [World Materialization](world-materialization.md), [Generated World Warmup](generated-world-warmup.md), [Decision 018](../decisions/018-generated-world-runtime-bootstrap.md), [Decision 019](../decisions/019-generated-world-warmup-is-explicit-observation.md), and [Decision 020](../decisions/020-world-preparation-and-calibration-boundary.md).

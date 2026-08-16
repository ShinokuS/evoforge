# Generated World Runtime

## Purpose

Provide one production path from immutable generated-world provenance to an ordinary started `SimulationRuntime` without giving generation ownership of lived Terrain, Soil or Water state.

## Bootstrap path

```text
WorldSpec + seed
      ↓
WorldGenesis
      ↓
GeneratedWorldBootstrap
      ↓
WorldAtlasGenerator
      ↓
WorldAtlas
  ├─ ElevationField
  ├─ DrainageField
  └─ HydroClimateField
      ↓
configured SimulationAssembly + TerrainMaterialResolver
      ↓
materialize generated Terrain
      ↓
attach generated HydroClimate
      ↓
SimulationAssembly.start()
      ↓
SimulationRuntime
```

The assembly passed to bootstrap is deliberately still content-configurable. Definitions such as movement capabilities, terrain materials or future plants/animals are prepared by normal content composition before bootstrap. Generated-world orchestration therefore does not contain a second content registry or material-name switch.

## Terrain setup

`GeneratedWorldBootstrap` first configures runtime `WorldBounds` from `WorldGenesis`, then asks `SimulationAssembly.materializeGeneratedTerrain(...)` to translate Atlas elevation through the existing `WorldTerrainMaterializer`.

The assembly method is only a narrow access seam to Assembly-owned definitions and Landscape mutations. Materialization behavior itself remains in the `world.materialization` subsystem.

The supplied `TerrainMaterialResolver` remains pure and replaceable. The current vertical slice may explicitly use a uniform material; future geology/material fields can provide another resolver without changing bootstrap.

## Generated hydrologic climate

`SimulationAssembly.generatedHydroClimate(...)` accepts an immutable `HydroClimateField` whose bounds exactly match the runtime world.

Generated climate is mutually exclusive with the older hand-authored periodic precipitation/evaporation schedules. A runtime therefore has one atmospheric baseline source, not two independently configured sources that accidentally duplicate Water mass.

At runtime:

```text
HydroClimateField
      ↓
HydroClimateForcingSystem
      ↓
HydroClimateForcingProcess
      ↓ Scheduler, once per advanced tick
EvaporationSystem + SkyPrecipitationSystem
      ↓
existing Soil / Water / Liquid Flow owners
```

`HydroClimateForcingProcess` owns cadence metadata only. It does not own weather, Water or Soil state. It schedules the exact forcing system for each positive absolute simulation tick. When free surface Water changes, existing liquid flow is reactivated through its normal process boundary.

The process deliberately does not invent storms, seasons or random event timing. It realizes the current baseline climate normals exactly. Eventful weather remains a later layer that must preserve long-term climate semantics.

## Bootstrap result

`GeneratedWorldRuntime` carries:

- immutable `WorldAtlas` provenance/facts;
- `TerrainMaterializationResult` initialization accounting;
- the started ordinary `SimulationRuntime`.

It is a pairing/result object, not a new mutable world owner. Runtime stepping still happens through `SimulationRuntime.stepper()` and observation still happens through `SimulationView` and generated-world diagnostics.

## Diagnostics and warmup

Immediately after bootstrap, `GeneratedWorldDiagnosticsProbe` can capture a `tick=0` audit. `GeneratedWorldWarmup` advances this same production runtime to explicitly requested absolute checkpoint ticks and captures further diagnostics through the same probe.

Warmup has no implicit equilibrium condition and no universal duration. The regular CI matrix currently uses small verification checkpoints to exercise determinism across several seeds; longer developer audits are opt-in through `:simulation:generatedWorldAudit`.

Balance/viability interpretation remains separate from runtime and warmup. The checkpoint trace is evidence for a future evaluator, not a hidden verdict.

## Current acceptance

Headless integration verifies that:

- an unforced generated world starts through this single path and creates no Water from nothing;
- Atlas-driven HydroClimate precipitation is executed by the production scheduler;
- generated Terrain remains aligned with Atlas surface facts when no terrain-changing runtime mechanic exists;
- same seed + same content setup + same ticks yields identical diagnostics across replay;
- generated climate cannot be combined with legacy periodic atmospheric schedules;
- deterministic warmup checkpoints can be reproduced across a representative seed/climate matrix.

See [Generated World Warmup](generated-world-warmup.md), [World Atlas](world-atlas.md), [World Materialization](world-materialization.md), [Generated World Diagnostics](generated-world-diagnostics.md), [Surface Hydrology](hydrology.md), [Decision 018](../decisions/018-generated-world-runtime-bootstrap.md), and [Decision 019](../decisions/019-generated-world-warmup-is-explicit-observation.md).

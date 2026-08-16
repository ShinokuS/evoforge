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

Immediately after bootstrap, `GeneratedWorldDiagnosticsProbe` can capture a `tick=0` audit. Future deterministic warmup tooling will advance this same production runtime and capture later checkpoints; it will not create a separate simulation loop with alternate physical rules.

No warmup duration or balance verdict is defined here. Those policies require representative generated-world runs first.

## Current acceptance

Headless integration verifies that:

- an unforced generated world starts through this single path and creates no Water from nothing;
- Atlas-driven HydroClimate precipitation is executed by the production scheduler;
- generated Terrain remains aligned with Atlas surface facts when no terrain-changing runtime mechanic exists;
- same seed + same content setup + same ticks produces an identical complete diagnostic snapshot;
- generated climate cannot be combined with legacy periodic atmospheric schedules.

See [World Atlas](world-atlas.md), [World Materialization](world-materialization.md), [Generated World Diagnostics](generated-world-diagnostics.md), [Surface Hydrology](hydrology.md), and [Decision 018](../decisions/018-generated-world-runtime-bootstrap.md).

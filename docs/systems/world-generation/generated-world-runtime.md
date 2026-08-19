# Generated World Runtime

## In plain language

Generated-world runtime is the **handoff from construction to life**.

Before startup, generation/calibration may calculate immutable facts: surface height, generated rock identity, material profiles, ramps, Soil hydraulic profiles and an initial Water condition. At startup those facts are copied/compiled into the same ordinary runtime systems used by hand-authored scenarios.

After that handoff, the generator is finished. If rain falls, Water flows or Terrain later changes, ordinary runtime systems own those changes.

```text
prepare world
    ↓
START BOUNDARY
    ↓
live simulation
```

There is no separate “generated-world simulation”.

## Current status

Stage 0 makes preparation and runtime bootstrap explicit and one-way:

```text
WorldGenesis
    ↓
GeneratedWorldPreparation        no SimulationRuntime
    ↓
PreparedGeneratedWorld
    ↓
GeneratedWorldRuntimeBootstrap   no generators/calibrators
    ↓
SimulationAssembly.start()
    ↓
SimulationRuntime
```

`GeneratedWorldBootstrap` remains a convenience facade for older one-call composition, but the architecture is the two phases above.

## Preparation phase

`GeneratedWorldPreparation` first generates a `WorldAtlas`, then applies independently replaceable preparation algorithms from `WorldPreparationAlgorithms`:

```text
WorldAtlas.elevation
    ↓
SurfaceMorphologyGenerator
    ↓
SurfaceMorphologyField

GenerationRevision + ElevationField
    ↓
TerrainShapeGenerator
    ↓
TerrainShapeField

Elevation + Geology + Drainage + SurfaceHydrology + Morphology
    ↓
TerrainMaterialGenerator
    ↓
TerrainMaterialField

(optional semantic Soil bindings)
TerrainMaterialField + Morphology + Drainage
    ↓
SoilFormationGenerator
    ↓
SoilHydraulicProfileField
```

The standard preparation algorithm bundle currently contains:

```text
SurfaceMorphologyGenerationStage
TerrainShapeGenerationStage.standard()
TerrainMaterialGenerationStage
SoilFormationGenerationStage.standard()
```

Any one may be replaced without adding a central type switch.

Preparation validates non-null outputs and matching world bounds. It creates no scheduler and does not mutate runtime Water/Soil/Terrain.

## What `PreparedGeneratedWorld` represents

Conceptually it packages:

```text
WorldAtlas
TerrainMaterialField
TerrainShapeField
GeneratedLandscapeProperties
```

`GeneratedLandscapeProperties` may contain generated/calibrated physical properties such as spatial Soil hydraulic profiles.

This is immutable preparation data, not live world ownership.

## Start boundary in exact order

`GeneratedWorldRuntimeBootstrap.start(...)` currently performs these responsibilities:

1. validate bootstrap inputs and generated-field bounds;
2. configure `SimulationAssembly.worldBounds(...)` from Genesis bounds;
3. if `PhysicalSpaceScale` exists, configure physical cell volume;
4. if generated Soil hydraulic profiles exist, require both physical-space and simulation-time scales and compile them into runtime Soil properties;
5. resolve stable generated `TerrainMaterialKey`s through `TerrainMaterialBindings` to runtime Landscape definitions;
6. materialize generated solid Terrain from `ElevationField`;
7. apply prepared non-default surface Shape overrides at each generated surface cell;
8. materialize the current compatibility `SurfaceHydrologyField` as finite initial Water one cell above terrain;
9. compose the chosen runtime atmosphere plan once from immutable Atlas facts;
10. register atmospheric Water forcing when present;
11. call `SimulationAssembly.start()`;
12. return `GeneratedWorldRuntime`, retaining Atlas as immutable provenance/diagnostic context beside the started runtime.

This exact ordering matters because generated Soil/shape/material data must be configured before runtime starts, while runtime processes must not execute during generation.

## Initial Water: current implementation versus canonical future stage

The current bootstrap **does** materialize `SurfaceHydrologyField.initialWaterVolumeAt(x,y)` before `SimulationAssembly.start()`.

For a positive amount:

```text
waterZ = discrete terrain surfaceZ + 1
```

If that coordinate lies above `WorldBounds.maxZ`, bootstrap fails instead of silently discarding/generated-clipping Water.

This is current compatibility behavior. The world-generation roadmap deliberately keeps the final **canonical** initial-Water design as Stage 7, after the completed dry terrain/hydrography/geology/cave/material world passes Stage 6 acceptance.

Until Stage 7, do not mistake existing initial-Water infrastructure for proof that the final generation ordering has already been implemented.

## Terrain material identity boundary

Generated materials use stable semantic keys such as `TerrainMaterialKey`, not runtime-local integer IDs.

```text
TerrainMaterialField
    stable semantic key
        ↓
TerrainMaterialBindings
        ↓
TerrainMaterialResolver
        ↓
LandscapeDefinitionId
        ↓
runtime Landscape
```

This prevents generation output from depending on the accidental order in which runtime definitions were registered.

## Generated surface Shape boundary

Prepared terrain shapes are immutable column facts. During bootstrap:

```text
for each (x,y):
    override = TerrainShapeField.shapeOverrideAt(x,y)
    if override exists:
        surfaceZ = atlas.elevation().elevationAt(x,y)
        assembly.setShape(x,y,surfaceZ,override)
```

The resulting Shape is then ordinary runtime Geometry/Landscape state. The generator does not keep controlling it.

## Generated Soil boundary

When spatial Soil hydraulic profiles are present, bootstrap requires:

- `PhysicalSpaceScale` from Genesis;
- `SimulationTimeScale` supplied to bootstrap.

`SoilHydraulicRuntimeFieldCompiler` converts the pre-runtime physical profile to the exact runtime Soil property representation before startup.

This preserves the architectural distinction:

```text
authored Soil meaning
        ↓
pre-runtime formation/calibration
        ↓
physical generated profile
        ↓
runtime-unit compilation
        ↓
ordinary Soil runtime mechanics
```

A generated Soil calibrator is not a runtime controller.

## Runtime atmosphere composition

Atmosphere is selected through the replaceable `AtmosphericRuntimePlan` seam.

```text
WorldAtlas + optional SimulationTimeScale
        ↓
AtmosphericRuntimePlan.compose(...)
        ↓
AtmosphericRuntimeComposition
  ├─ optional AtmosphericWaterForcing
  └─ optional WeatherLookup
```

Built-in plans include disabled atmosphere, climate-normal compatibility forcing and current Weather-state composition. Old `AtmosphericForcingPolicy` callers are compatibility paths, not the preferred extensibility surface.

The runtime forcing system consumes its narrow contract; it does not branch on the concrete weather/climate implementation.

## Generated facts versus live state

Examples:

| Prepared/generated | Live runtime owner |
|---|---|
| `ElevationField` | Landscape/Geometry after Terrain materialization |
| `TerrainMaterialField` | Landscape material identity after binding/materialization |
| `TerrainShapeField` | runtime Geometry after shape override application |
| `SoilHydraulicProfileField` | runtime Soil properties after compilation |
| `SurfaceHydrologyField` initial amount | Liquid/Water store after initial placement |
| `ClimateNormalsField` | not mutable weather; used to compose runtime atmosphere |

Atlas/prepared fields can remain available for provenance and diagnostics, but they do not mirror later runtime mutation.

## Diagnostics and warm-up

Generated-world diagnostics observe the started runtime and compare it with immutable prepared/Atlas facts where useful.

Warm-up advances the **ordinary** runtime through its normal stepper/scheduler to requested checkpoints. It does not call generation again and does not have a secret “fast-forward generated world” ruleset.

See [Generated World Warm-up](../tooling/generated-world-warmup.md) and [Generated World Diagnostics](../tooling/generated-world-diagnostics.md).

## Invariants

- Preparation has no started runtime/scheduler dependency.
- Runtime bootstrap has no generator/calibrator dependency.
- Prepared values flow one way into runtime ownership.
- Material IDs are resolved only at composition/materialization.
- Generated Shape/Soil/Water initialization happens before runtime start.
- After start, ordinary domain systems are the only mutable owners.
- Atlas is not continuously synchronized to lived Terrain/Water/Soil.
- Runtime atmospheric composition is replaceable and happens once at bootstrap.

## Current limitations

The current bootstrap still carries historical initial-surface-Water compatibility that Stage 7 will reposition/refine after dry-world acceptance.

Persistence/load semantics are not defined here: loading a lived save is not “regenerate from seed and bootstrap again”.

The architecture also does not yet define chunk streaming, partial materialization or authoritative multithreaded startup.

## Code and tests

Primary implementation:

```text
world/preparation/GeneratedWorldPreparation.java
world/preparation/WorldPreparationAlgorithms.java
world/preparation/PreparedGeneratedWorld.java
world/bootstrap/GeneratedWorldRuntimeBootstrap.java
world/bootstrap/GeneratedWorldBootstrap.java
world/bootstrap/GeneratedWorldRuntime.java
```

Integration tests cover generation-to-runtime materialization, physical scale requirements, atmosphere plans, Soil formation and deterministic generated-world warm-up/audits.

## Sources

**Internal EvoForge design:** the preparation/start ownership split is a project architecture rather than a published generation algorithm.

See [World Generation](overview.md), [World Atlas](world-atlas.md), [World Materialization](world-materialization.md), [ADR-018](../../decisions/018-generated-world-runtime-bootstrap.md), [ADR-019](../../decisions/019-generated-world-warmup-is-explicit-observation.md), and [ADR-021](../../decisions/021-world-preparation-and-calibration-boundary.md).

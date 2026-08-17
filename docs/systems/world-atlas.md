# World Atlas

## Purpose

`WorldAtlas` is the immutable durable fact set generated from `WorldGenesis`. It describes what the world was prepared to be before runtime mutation begins.

Atlas is **not** Terrain storage, WeatherState, Water, Soil, a scheduler, a runtime service container, or a source of continuing simulation control.

## Current composition

```text
WorldAtlas
├─ WorldGenesis
├─ ElevationField
├─ GeologyField
├─ ClimateNormalsField
├─ DrainageField
├─ HydrographyField
└─ SurfaceHydrologyField
```

Every field covers the same `WorldBounds` as genesis.

The layers have different causal roles:

- `ElevationField` — durable macro surface height;
- `GeologyField` — durable geological identity/stratification facts;
- `ClimateNormalsField` — long-term climate normals, not current weather;
- `DrainageField` — topological downstream routing and contributing area;
- `HydrographyField` — durable channel network derived from terrain/drainage;
- `SurfaceHydrologyField` — finite generated initial surface-Water condition and shoreline facts.

A channel may exist while containing no runtime Water. Climate normals may indicate a wet climate while no rain is currently falling.

## Generation dependencies

`WorldAtlasGenerator` is a thin typed orchestration boundary. Current dependencies are explicit:

```text
WorldGenesis
   ├──────────────→ Elevation
   ├──────────────→ Geology
   └──────┐
          └────────→ ClimateNormals
Elevation ─────────→ Drainage
Elevation + Drainage ─→ Hydrography
Elevation + Drainage + Hydrography + ClimateNormals
                    └→ SurfaceHydrology
```

Execution order follows these real dependencies. No stage receives another stage merely to force a convenient linear pipeline.

## Algorithm substitution

Each generation layer has a narrow typed generator contract. The canonical replacement surface is `WorldGenerationAlgorithms`:

```text
WorldGenerationAlgorithms
├ elevation       : ElevationGenerator
├ geology         : GeologyGenerator
├ climate         : ClimateNormalsGenerator
├ drainage        : DrainageGenerator
├ hydrography     : HydrographyGenerator
└ surfaceHydrology: SurfaceHydrologyGenerator
```

The standard bundle supplies production algorithms. `withElevation(...)`, `withClimate(...)` and the other typed replacement methods allow any combination to be substituted without creating constructor overloads or a generic service registry.

A generic `Map<String,Object>` / `Map<Class<?>,Object>` generation context is deliberately rejected: dependencies and ownership remain visible in the type system.

## Climate facts are not atmosphere execution

`ClimateNormalsField` contains durable long-term facts. Depending on generation revision, historical worlds may retain legacy cell-relative water normals while V8+ can use physical water-depth-per-time normals.

Neither representation is a current rain event.

Current atmosphere belongs to runtime:

```text
ClimateNormalsField      durable prepared fact
        ↓ initialization/calibration input
WeatherState             mutable runtime state
        ↓
AtmosphericWaterForcing  runtime process contract
        ↓
Water / Soil
```

No runtime atmospheric interface belongs in `world.atlas`.

## Drainage, hydrography and Water ownership

`DrainageField` is topology, not Water quantity.

`HydrographyField` describes channel structure, not an infinite river source.

`SurfaceHydrologyField` can provide finite tick-zero Water. Runtime bootstrap materializes that initial condition exactly once. After start, the ordinary runtime Water/Soil/liquid systems are the only owners of lived water state.

This preserves scenarios such as:

```text
channel exists
+ currently dry weather
→ dry channel

later rain/runoff
→ Water enters channel through runtime physics
```

without `DryRiver`/`WetRiver` terrain categories.

## Material identity and materialization

Generated terrain composition chooses stable semantic material keys. Runtime content bindings resolve those keys to runtime `LandscapeDefinitionId` values only at the materialization boundary.

```text
Atlas + TerrainMaterialField
          ↓
PreparedGeneratedWorld
          ↓
--------- runtime start boundary ---------
          ↓
TerrainMaterialBindings
          ↓
WorldTerrainMaterializer
          ↓
LandscapeSystem owns lived Terrain
```

Atlas does not remain synchronized with later Terrain mutation. It remains provenance/durable generated context.

## Preparation boundary

The canonical path is:

```text
WorldGenesis
    ↓
GeneratedWorldPreparation
    ↓
WorldAtlas + stable material field
    ↓
PreparedGeneratedWorld
    ↓
GeneratedWorldRuntimeBootstrap
    ↓
SimulationRuntime
```

Generation and future calibration end before runtime starts. A running simulation does not call WorldAtlas generators or calibrators.

See [Generated World Runtime](generated-world-runtime.md), [World Materialization](world-materialization.md), [Decision 010](../decisions/010-world-atlas-generated-facts.md), [Decision 011](../decisions/011-world-generation-algorithm-contracts.md), [Decision 016](../decisions/016-atlas-terrain-materialization.md), and [Decision 020](../decisions/020-world-preparation-and-calibration-boundary.md).

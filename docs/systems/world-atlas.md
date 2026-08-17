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

## Ocean-first elevation in V9

V9 is the first generation revision where macro land/ocean shape is controlled by explicit high-level `WorldGenerationIntent` rather than emerging accidentally from a fixed elevation-noise threshold.

The current intent coordinates are:

```text
landCoverage
landmassScale
fragmentation
```

V9 elevation uses global sea level `z = 0` as its macro datum. Vertical `WorldBounds` must therefore include valid space below and above zero.

The generator first constructs a deterministic spatial land-potential field. `landmassScale` controls the coherent scale of that field and `fragmentation` blends in finer structure. The field is then ranked deterministically. `landCoverage` selects the nearest representable number of columns that must lie above sea level, giving one-column calibration precision instead of exposing an implementation-specific threshold.

```text
seed + spatial intent
        ↓
land-potential field
        ↓
deterministic rank
        ↓
requested land-column count
        ↓
positive land / negative ocean elevation around z=0
```

Tie-breaking is stable by cell index, so identical genesis input produces identical output. The ranking implementation uses primitive data and does not require boxed per-cell objects.

V1-V8 keep their historical elevation behavior and ignore `WorldGenerationIntent`. V9 changes only the elevation semantics; the other existing Atlas stages retain their V8 behavior while accepting the new overall revision.

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

`ClimateNormalsField` contains durable long-term facts. Historical V1-V7 worlds use the legacy cell-relative atmospheric-water representation. V8 and V9 full-Atlas generation require physical water-depth-per-time climate normals.

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

Generation and calibration end before runtime starts. A running simulation does not call WorldAtlas generators or calibrators.

See [World Genesis](world-genesis.md), [Generated World Runtime](generated-world-runtime.md), [World Materialization](world-materialization.md), [Decision 010](../decisions/010-world-atlas-generated-facts.md), [Decision 011](../decisions/011-world-generation-algorithm-contracts.md), [Decision 016](../decisions/016-atlas-terrain-materialization.md), and [Decision 020](../decisions/020-world-preparation-and-calibration-boundary.md).

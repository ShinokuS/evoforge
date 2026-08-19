# World Atlas

## In plain language

`WorldAtlas` is the immutable **pre-runtime map of generated facts**. It is the place where generation says things such as “the ground surface is here”, “this rock identity exists here”, “water would drain this way”, or “this is the generated initial surface-Water condition”.

It is not the living world itself. Once Terrain and Water have been materialized into runtime systems, later runtime changes do not rewrite the Atlas.

Think of it as the construction survey used to build the initial world, not the building after people start living in it.

## Current status

The current `WorldAtlas` contains seven required components and validates that every field uses the same `WorldBounds` as Genesis:

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

The fields are stable typed boundaries. Some algorithms behind them are deliberately provisional and will be replaced in later world-generation stages.

## What each field means

### `ElevationField`

The precise generated height of the surface for every horizontal `(x,y)` column. It supports precise sub-cell elevation plus a discrete cell projection used by runtime terrain materialization.

### `GeologyField`

Generated geological material identity in solid volume. The field contract is useful today, but the current production geology algorithm is a placeholder until Stage 3.

### `ClimateNormalsField`

Long-term climate normals used as prepared environmental facts. These are not current weather and not a statement that rain is falling now.

### `DrainageField`

Analytical topological facts: downstream routing, contributing area and terminal destination/basin. It does not own Water and is not accepted erosion geometry.

### `HydrographyField`

A derived channel/network footprint from current elevation + drainage. The current threshold-style algorithm is provisional; Stage 2 owns final river hierarchy/carving.

### `SurfaceHydrologyField`

Historical generated initial surface-Water/shoreline facts. The typed field is useful compatibility infrastructure, but the canonical future pipeline moves/refines initial Water after the complete dry world has been accepted.

## Generation dependency graph

`WorldAtlasGenerator` is intentionally a thin orchestrator. Its actual current execution/dependencies are:

```text
WorldGenesis
   │
   ├──────────────→ ElevationGenerator ──────────→ ElevationField
   ├──────────────→ GeologyGenerator ────────────→ GeologyField
   │
   └─────────────────────────────────────────────┐
                                                 │
WorldGenesis + ElevationField ─→ ClimateNormalsGenerator
                                   ↓
                           ClimateNormalsField

ElevationField ───────────────→ DrainageGenerator
                                   ↓
                              DrainageField

WorldGenesis + ElevationField + DrainageField
        ───────────────────────→ HydrographyGenerator
                                   ↓
                              HydrographyField

WorldGenesis + ElevationField + DrainageField
+ HydrographyField + ClimateNormalsField
        ───────────────────────→ SurfaceHydrologyGenerator
                                   ↓
                           SurfaceHydrologyField
```

`WorldAtlasGenerator.generate(...)` rejects null stage outputs immediately. The final `WorldAtlas` constructor then checks all field bounds against `genesis.spec().bounds()`.

This explicit dependency graph is preferable to handing every algorithm a generic mutable context that contains everything “just in case”.

## Algorithm substitution

Current generation algorithms are grouped by `WorldGenerationAlgorithms`:

```text
ElevationGenerator
GeologyGenerator
ClimateNormalsGenerator
DrainageGenerator
HydrographyGenerator
SurfaceHydrologyGenerator
```

The standard bundle supplies production implementations. `withElevation(...)`, `withGeology(...)`, `withClimate(...)`, `withDrainage(...)`, `withHydrography(...)` and `withSurfaceHydrology(...)` replace one typed algorithm while keeping the other contracts unchanged.

This is heavily useful for tests: a test can provide a tiny deterministic elevation field without teaching all downstream systems about the concrete test class.

## V12 in the Atlas

V12 changes the `ElevationField` algorithm while leaving the Atlas field boundary intact.

That is the intended extensibility:

```text
V9/V10/V11/V12/future elevation algorithm
                    ↓
              ElevationField
                    ↓
same typed downstream contracts
```

Downstream stages do not inspect the concrete elevation algorithm to decide how to behave.

The exact V12 model is documented in [Terrain Generation](terrain-generation.md).

## Climate facts versus weather

A climate normal is a long-term environmental fact. Weather is current runtime state/forcing.

```text
ClimateNormalsField
      ↓ prepared/calibrated runtime input
AtmosphericRuntimePlan
      ↓
Weather / AtmosphericWaterForcing
      ↓
precipitation + evaporation
      ↓
Water / Soil runtime state
```

The Atlas therefore does not contain runtime `WeatherState`, scheduler processes or current rain events.

## Drainage, hydrography and Water are different facts

A dry river channel must be possible. Therefore:

```text
DrainageField       = where/how topological flow routes
HydrographyField    = channel/network structure
SurfaceHydrology    = generated initial Water compatibility fact
runtime Liquid/Water= finite lived Water after materialization
```

None of the first two implies an infinite source of Water.

In the final Stage 2/7 design, dry river/lake geometry is accepted before finite initial Water is added.

## Preparation after the Atlas

The Atlas is not the end of generation preparation.

Current `GeneratedWorldPreparation` uses the Atlas to derive additional immutable prepared facts:

```text
WorldAtlas.elevation
        ↓
SurfaceMorphologyGenerator
        ↓
SurfaceMorphologyField

WorldAtlas.elevation + GenerationRevision
        ↓
TerrainShapeGenerator
        ↓
TerrainShapeField

Elevation + Geology + Drainage + SurfaceHydrology + Morphology
        ↓
TerrainMaterialGenerator + CompiledTerrainProfile
        ↓
TerrainMaterialField
```

When semantic Soil archetypes are supplied, the Soil formation generator additionally resolves spatial hydraulic profiles from material + morphology + drainage before runtime starts.

These facts are packaged in `PreparedGeneratedWorld`; they do not become mutable runtime owners.

## Invariants

- Every Atlas field uses exactly the Genesis bounds.
- Atlas fields are immutable/read-only generated facts.
- Stage algorithms are independently replaceable behind typed contracts.
- The Atlas does not contain runtime services or scheduler processes.
- Drainage/hydrography do not own Water quantity.
- A running simulation does not regenerate the Atlas to update lived state.
- Algorithm revision changes do not silently reinterpret historical Genesis.

## Current limitations

The current Atlas shape is useful but not necessarily the final long-term list of generated facts. Stages 1–5 may introduce new typed mountain, carved-morphology, stratigraphy, cave or depositional facts if real downstream consumers require them.

Do not add a universal `Map<String,Object>` generated-fact bag. New durable facts should have explicit types and consumers.

## Code and tests

Primary implementation:

```text
world/atlas/WorldAtlas.java
world/atlas/WorldAtlasGenerator.java
world/atlas/WorldGenerationAlgorithms.java
world/atlas/*Generator.java
```

`WorldAtlasAlgorithmContractTest` protects substitution/bounds/null-output behavior. Generated-world audit tests exercise representative complete Atlases across deterministic seeds/revisions.

## Sources

**Internal EvoForge architecture:** the typed Atlas and algorithm-bundle composition are project-specific ownership/composition choices.

The terrain algorithms supplying `ElevationField` have their own source classification in [Terrain Generation](terrain-generation.md).

See [World Genesis](world-genesis.md), [World Generation](overview.md), [Generated World Runtime](generated-world-runtime.md), [ADR-010](../../decisions/010-world-atlas-generated-facts.md), and [ADR-011](../../decisions/011-world-generation-algorithm-contracts.md).

# World Atlas

## In plain language

`WorldAtlas` is the immutable **pre-runtime map of generated facts**. It records facts such as the generated ground surface, rock identity, climate normals and analytical drainage topology.

It is not the living world. Once those facts are materialized/prepared into runtime systems, later Terrain/Water/Soil changes do not rewrite the Atlas.

## Current status

The current Atlas contains:

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

All fields use the same `WorldBounds` as Genesis.

V13 mountains are represented in the same precise `ElevationField` used by ordinary terrain. There is intentionally no separate `MountainField` merely to label feature names: no current downstream owner needs an independent mountain fact beyond final morphology.

## What each field means

### `ElevationField`

Precise generated surface height for each horizontal `(x,y)` column, including the accepted V12 base morphology and, for `GenerationRevision.V13`, dedicated mountain uplift. It also exposes the discrete cell projection used by runtime terrain materialization.

### `GeologyField`

Generated geological material identity in solid volume. The typed contract is useful; the current algorithm remains provisional until Stage 3.

### `ClimateNormalsField`

Long-term generated climate normals. These are prepared environmental facts, not current runtime weather.

### `DrainageField`

Analytical downstream routing, contributing area and terminal destination/basin. It does not own Water and does not imply accepted river carving.

### `HydrographyField`

Current derived channel/network footprint. Its threshold-style algorithm is provisional; Stage 2 owns final dry hierarchy and carving.

### `SurfaceHydrologyField`

Historical generated initial surface-Water/shoreline compatibility facts. The canonical milestone keeps finite initial Water after complete dry-world acceptance.

## Generation dependency graph

`WorldAtlasGenerator` is intentionally a thin typed orchestrator:

```text
WorldGenesis
   ├─→ ElevationGenerator ─────────→ ElevationField
   └─→ GeologyGenerator ───────────→ GeologyField

WorldGenesis + ElevationField
   └─→ ClimateNormalsGenerator ────→ ClimateNormalsField

ElevationField
   └─→ DrainageGenerator ──────────→ DrainageField

WorldGenesis + ElevationField + DrainageField
   └─→ HydrographyGenerator ───────→ HydrographyField

WorldGenesis + ElevationField + DrainageField
+ HydrographyField + ClimateNormalsField
   └─→ SurfaceHydrologyGenerator ──→ SurfaceHydrologyField
```

Null stage outputs are rejected immediately and the final Atlas constructor validates bounds consistency.

## Algorithm substitution

`WorldGenerationAlgorithms` groups typed contracts:

```text
ElevationGenerator
GeologyGenerator
ClimateNormalsGenerator
DrainageGenerator
HydrographyGenerator
SurfaceHydrologyGenerator
```

Each can be replaced independently for tests/experiments while downstream consumers continue reading the same fact interfaces.

## V12 and V13 inside `ElevationGenerator`

The Atlas does not branch downstream based on which elevation implementation produced the surface:

```text
V9 / V10 / V11 / V12 / V13 / test generator
                    ↓
              ElevationField
                    ↓
          same downstream contracts
```

V12 composes semantic ordinary-landscape intent through its calibrator/recipe/spatial algorithm. V13 composes that capped V12 base with a separate `MountainCalibrator + MountainRecipe + MountainElevationAlgorithm` pipeline behind the same `ElevationGenerator` boundary.

See [Terrain Generation](terrain-generation.md) and [V13 Mountain Generation](mountain-generation.md).

## Climate facts versus weather

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

The Atlas does not contain runtime `WeatherState`, scheduler processes or current rain events.

## Drainage, hydrography and Water remain different facts

```text
DrainageField        analytical routing/topology
HydrographyField     provisional channel/network structure
SurfaceHydrology     generated initial-Water compatibility fact
runtime Liquid/Water finite lived Water after materialization
```

Stage 2 will replace/refine the dry network/carving model from the accepted V13 dry elevation. Stage 7 owns final finite initial Water placement.

## Preparation after the Atlas

Additional immutable preparation derives from Atlas facts:

```text
ElevationField
   ↓
SurfaceMorphologyField

ElevationField + GenerationRevision
   ↓
TerrainShapeField

Elevation + Geology + Drainage + SurfaceHydrology + Morphology
   ↓
TerrainMaterialField
```

V13 shape preparation remains generic: it consumes `ElevationField + GenerationRevision`, not a mountain-specific concrete class. The sparse V13 transition policy is a geometry fitting policy, not part of the mountain generator.

These prepared facts are packaged in `PreparedGeneratedWorld`; they still are not mutable runtime owners.

## Invariants

- Every Atlas field uses Genesis bounds.
- Atlas fields are immutable/read-only generated facts.
- Stage algorithms are independently replaceable behind typed contracts.
- V13 mountains do not require a separate fact type when elevation is the only durable downstream fact.
- Downstream consumers do not inspect concrete elevation algorithm classes.
- The Atlas does not contain runtime services/processes.
- Drainage/hydrography do not own Water quantity.
- Running simulation does not regenerate the Atlas to update lived state.
- Generation revision changes do not silently reinterpret historical Genesis.

## Current limitations / next changes

Stage 2 may replace/refine drainage/hydrography and may introduce new typed dry-carving facts only when real downstream consumers require them. Later stages may similarly introduce explicit stratigraphy, cave or depositional facts.

Do not add a universal generated-fact bag such as `Map<String,Object>`. New durable facts require explicit types and consumers.

## Code and tests

Primary implementation:

```text
world/atlas/WorldAtlas.java
world/atlas/WorldAtlasGenerator.java
world/atlas/WorldGenerationAlgorithms.java
world/atlas/*Generator.java
```

`WorldAtlasAlgorithmContractTest` protects substitution/bounds/null-output behavior. Generated-world audits exercise representative complete Atlases.

See [World Genesis](world-genesis.md), [World Generation](overview.md), [Terrain Generation](terrain-generation.md), [V13 Mountain Generation](mountain-generation.md), [Generated World Runtime](generated-world-runtime.md), [ADR-010](../../decisions/010-world-atlas-generated-facts.md), and [ADR-011](../../decisions/011-world-generation-algorithm-contracts.md).

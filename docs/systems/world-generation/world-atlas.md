# World Atlas

## In plain language

`WorldAtlas` is the immutable **pre-runtime map of generated facts**. It records facts such as the generated ground surface, rock identity, climate normals and analytical drainage topology.

It is not the living world. Once those facts are materialized/prepared into runtime systems, later Terrain/Water/Soil changes do not rewrite the Atlas.

## Current status

The current Atlas contract contains:

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

V13 mountains and accepted V14 standing-water bathymetry are represented in the same precise `ElevationField` used by ordinary terrain. There is intentionally no separate `MountainField` or `BathymetryField` merely to label feature names: no current downstream owner needs those morphologies as independent facts beyond final elevation.

Important revision boundary: the explicit elevation router supports accepted V14, but that does **not** automatically mean every standard downstream Atlas stage has been promoted to V14. `WorldGenesis.current()` remains the compatibility V7 path, and climate/hydrography/initial-Water revision support is widened only at its owning milestone.

## What each field means

### `ElevationField`

Precise generated surface height for each horizontal `(x,y)` column. Depending on the explicit generation revision it can include:

- accepted V12 ordinary base morphology;
- V13 dedicated mountain uplift;
- V14 standing-water bathymetry while preserving V13 land and submerged membership.

It also exposes the discrete cell projection used by runtime terrain materialization.

### `GeologyField`

Generated geological material identity in solid volume. The typed contract is useful; the current algorithm remains provisional until Stage 3.

### `ClimateNormalsField`

Long-term generated climate normals. These are prepared environmental facts, not current runtime weather.

### `DrainageField`

Analytical downstream routing, contributing area and terminal destination/basin. It does not own Water and does not imply accepted Stage 2B drainage/basin topology yet.

### `HydrographyField`

Current derived channel/network footprint. Its threshold-style algorithm is provisional; Stage 2C owns final dry river-network semantics.

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

This graph is a composition contract, not a claim that every historical/new `GenerationRevision` is currently supported by every standard implementation in the graph.

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

## V12, V13 and V14 inside `ElevationGenerator`

Downstream elevation consumers do not inspect which concrete elevation implementation produced the surface:

```text
V9 / V10 / V11 / V12 / V13 / V14 / test generator
                         ↓
                   ElevationField
```

- V12 composes semantic ordinary-landscape intent through its calibrator/recipe/spatial algorithm.
- V13 composes a capped V12 base with `MountainCalibrator + MountainRecipe + MountainElevationAlgorithm`.
- V14 composes accepted V13 land/submerged membership with `BathymetryCalibrator + BathymetryRecipe + BathymetryElevationAlgorithm`; the standard bathymetry algorithm itself composes accepted coast morphology with an independent deep-interior structure pass.

V14 preserves exact V13 land elevation and submerged membership while re-authoring underwater Z. It does not create Water or a separate waterbody fact.

See [Terrain Generation](terrain-generation.md), [V13 Mountain Generation](mountain-generation.md) and [V14 Standing-Water Bathymetry](bathymetry-generation.md).

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

Stage 2A standing-water bathymetry is already accepted inside elevation. Stage 2B next owns final drainage/basin topology starting from accepted V14 elevation. Stage 2C owns the river network and Stage 2D owns dry river/valley carving. Stage 7 owns final finite initial Water placement.

Neither provisional drainage nor hydrography is permission to rewrite accepted V14 lake/sea/ocean footprint or bottom geometry.

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

V13/V14 shape preparation remains generic: it consumes `ElevationField + GenerationRevision`, not mountain- or bathymetry-specific concrete classes. The sparse coherent transition policy is geometry fitting policy, not part of either morphology generator.

These prepared facts are packaged in `PreparedGeneratedWorld`; they still are not mutable runtime owners.

## Invariants

- Every Atlas field uses Genesis bounds.
- Atlas fields are immutable/read-only generated facts.
- Stage algorithms are independently replaceable behind typed contracts.
- V13 mountains and V14 bathymetry do not require separate fact types when elevation is the only durable downstream fact.
- Downstream consumers do not inspect concrete elevation algorithm classes.
- Explicit V14 elevation support does not silently promote unrelated standard Atlas-stage revision support.
- The Atlas does not contain runtime services/processes.
- Drainage/hydrography do not own Water quantity.
- Running simulation does not regenerate the Atlas to update lived state.
- Generation revision changes do not silently reinterpret historical Genesis.

## Current limitations / next changes

Stage 2B may replace/refine drainage topology and introduce a new typed basin/topology fact only if a real downstream consumer requires one. Stage 2C/2D may similarly refine hydrography/carving behind narrow contracts. Later stages may introduce explicit stratigraphy, cave or depositional facts.

Accepted V14 bathymetry is protected input for those later concerns rather than provisional data to be freely repaired downstream.

Do not add a universal generated-fact bag such as `Map<String,Object>`. New durable facts require explicit types and consumers.

## Code and tests

Primary implementation:

```text
world/atlas/WorldAtlas.java
world/atlas/WorldAtlasGenerator.java
world/atlas/WorldGenerationAlgorithms.java
world/atlas/*Generator.java
```

`WorldAtlasAlgorithmContractTest` protects substitution/bounds/null-output behavior. Generated-world audits exercise representative complete Atlases on revisions supported by the corresponding standard stage implementations.

See [World Genesis](world-genesis.md), [World Generation](overview.md), [Terrain Generation](terrain-generation.md), [V13 Mountain Generation](mountain-generation.md), [V14 Standing-Water Bathymetry](bathymetry-generation.md), [Generated World Runtime](generated-world-runtime.md), [ADR-010](../../decisions/010-world-atlas-generated-facts.md), and [ADR-011](../../decisions/011-world-generation-algorithm-contracts.md).

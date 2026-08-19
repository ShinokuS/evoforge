# Terrain Generation

## In plain language

Terrain generation answers three different questions:

1. **Where is the ground surface?** — precise elevation and morphology.
2. **What material occupies the solid column?** — surface/subsurface material, sediment or geology.
3. **Which discrete runtime shape represents the surface cell?** — geometry fitting.

EvoForge keeps those questions separate. Mountains do not imply granite, shores do not imply sand, and the elevation generator does not choose `RampShape` or repair navigation.

The current accepted dry elevation stack is:

```text
V12 ordinary base morphology
        ↓
V13 dedicated mountain morphology
        ↓
precise ElevationField
        ↓
generic surface-shape fitting
```

V12 and V13 have both completed deterministic checks and manual visual acceptance. Dry river/lake carving, final geology, caves and final causal surface materials remain later stages.

## Current status

### Accepted

- deterministic V1–V13 elevation revision compatibility;
- manually accepted V12 ordinary-landscape appearance;
- manually accepted V13 structural-mountain appearance;
- V12 semantic intent → calibration → recipe → spatial algorithm split;
- V13 semantic mountain intent → calibration → recipe → replaceable mountain algorithm split;
- precise sub-cell elevation plus discrete `elevationAt` projection;
- local morphology derivation (slope/convexity/concavity);
- generic shape-template fitting independent from concrete Shape classes;
- deterministic diagnostics, generated-world audits and 2D/3D preview.

### Explicitly provisional

- current geology generation;
- current drainage/hydrography algorithms before Stage 2 carving;
- current slope/concavity/drainage terrain-material model;
- historical generated initial-Water ordering.

Typed seams may survive later stages even when their algorithms are replaced.

## Part I — V12 ordinary base morphology

V12 owns ordinary land/ocean shape and non-mountain relief.

### Architecture

```text
WorldGenerationIntent
        ↓
V12LandformCalibrator
        ↓
V12LandformCalibration
        +
V12LandformRecipe
        ↓
V12LandformElevationAlgorithm
        ↓
ElevationField
```

`V12BaseTerrainGenerator` composes those pieces behind `ElevationGenerator`. The spatial algorithm does not read semantic intent directly.

### Land/ocean membership

V12 blends a coherent landmass field with a finer fragmentation field, ranks every horizontal column deterministically and selects the calibrated land count:

```text
landCount = round(horizontalArea * landCoverage)
```

Later relief controls shape the selected land but do not change land/ocean membership.

Sea level for ocean-first revisions is precise elevation `0`.

### Coast interiority

A deterministic cardinal distance transform measures distance from ocean, capped at the V12 transition length of 12 cells. Cubic smoothstep converts the distance to an interiority coordinate from coast to inland.

Interiority controls baseline land height and suppresses ordinary relief near the shore without changing the coastline mask.

### Spatial scales

Ordinary V12 landforms are primarily measured in terrain cells rather than stretching directly with total world size. The balanced recipe keeps typical landform spacing in the range `20..64` cells.

V12 combines:

- broad coherent uplift;
- explicit deterministic rounded hills/depressions;
- rugged ridge signal;
- rolling primary/detail relief.

The important model choices live in `V12LandformRecipe`, not as user controls or scattered literals inside orchestration.

### Bounded ordinary slopes

V12 finishes with deterministic cardinal slope relaxation. Semantic ruggedness calibrates the maximum readable land-land step from roughly `0.18` to `0.60` terrain cell per horizontal cell.

This is a synthesis/readability constraint, not physical erosion.

### V12 boundary

V12 deliberately does **not** own dedicated mountain abundance/height/elongation, river carving, geology, caves or final material synthesis. V13 adds mountains as a separate elevation stage instead of growing more mountain-specific policy inside V12.

## Part II — V13 structural mountains

V13 first generates the accepted V12 base inside a capped positive range, then applies dedicated mountain uplift on V12 land only.

```text
V13 WorldGenesis
      ↓
V12 base Genesis with positive ceiling +12
      ↓
V12BaseTerrainGenerator
      ↓
base ElevationField
      +
V13 mountain stage
      ↓
V13 ElevationField
```

The mountain architecture is independently replaceable:

```text
WorldGenerationIntent.mountains
        ↓
MountainCalibrator
        ↓
MountainCalibration
        +
MountainRecipe
        ↓
MountainElevationAlgorithm
        ↓
ElevationField
```

`V13MountainTerrainGenerator` is composition only. The standard spatial implementation is `MountainMorphologyAlgorithm`.

### Semantic ownership

- **Abundance** owns the expected fraction of V12 land occupied by dedicated mountain structures.
- **Height** owns desired vertical prominence but is bounded by world size, vertical headroom, readable slope and the Scale-authored footprint.
- **Scale** owns individual transverse structure size and source-lattice spacing.
- **Chaininess** stretches the same structure along its long axis.
- **Peak sharpness** calibrates geometric slope character; it does not select concrete runtime Shapes.
- **Plateau enable/probability** controls whether an individual source uses the plateau variant of the same bounded profile.

Scale/Height/Chaininess must not silently take ownership of global mountain coverage.

### Source structure

Each mountain source is one asymmetric elongated hill. Deterministic source parameters independently vary:

- orientation and bounded center jitter;
- left/right transverse width;
- positive/negative long-axis length;
- height;
- plateau choice.

Sources are ranked deterministically from a Scale-owned lattice. Source count is derived from Abundance's target land coverage and nominal structure footprint. Well-separated sources are preferred; overlap composes with `max`, not addition.

### Height and readable Z bands

The mountain profile uses an eased summit, a long near-linear middle slope and an eased foot. The derivative bounds are recipe-owned and shared by calibration and synthesis.

Balanced peak sharpness calibrates maximum cardinal mountain rise between roughly:

```text
0.22 .. 0.38 vertical cell / horizontal cell
```

Therefore broad discrete Z bands are a property of the generated source profile. The accepted algorithm has no post-generation terrace repair/smoothing pass.

Height can broaden the authored Scale footprint only within a bounded multiplier. If the footprint cannot support the requested height at the readable slope, realized height is capped rather than expanding one mountain across most of the world.

### Coast interaction

V13 preserves V12 land/ocean membership exactly. Near water/world edges, dedicated mountain uplift is limited by a cardinal-Lipschitz coastal height cap:

```text
final mountain uplift = min(raw mountain, coastal cap)
```

The cap uses the same local-rise budget, so coast handling does not introduce a second uncontrolled gradient. V12 base morphology may still contain ordinary coastal cliffs.

For the full model, exact semantics and acceptance evidence see [V13 Mountain Generation](mountain-generation.md).

## Part III — local surface morphology

`world.terrain.surface` derives local geometric facts from precise elevation:

- maximum cardinal-neighbor slope;
- convexity;
- concavity.

These are derived Terrain facts, not another elevation owner. A uniform vertical translation preserves them because they depend on local differences.

## Part IV — generic runtime surface shape

Precise elevation is fitted to the discrete Terrain shapes available to runtime Geometry:

```text
precise ElevationField
      ↓
local surface patch
      ↓
generic template fit
      ↓
TerrainShapeField
      ↓
materialization
```

Generic fitting compares represented surface geometry rather than branching on `RampShape`, `FullShape` or future concrete classes. The palette/adaptor is the only layer that binds available runtime Shapes to surface templates.

V13 uses a sparse coherent transition policy. A locally suitable transition must have readable neighboring elevation and a coherent band at least three cells wide; only irregular deterministic patches are then retained. Hash selection includes XY, discrete Z layer and direction so neighboring levels do not repeat one mechanical interval.

Poor fits remain ordinary full-cell terrain. Mountains are not required to be globally traversable and generation does not create transition shapes merely to repair Navigation connectivity.

## Part V — current material preparation

Material identity remains downstream from morphology.

Current preparation uses compact semantic material roles (`surface`, `subsurface`, `sediment`, `bedrock`), generated geology and derived morphology/drainage/hydrology facts to build `TerrainMaterialField` before runtime starts.

The existing slope/deposition/shoreline rules are an early causal slice and remain provisional until Stage 5. In particular, generic code must not grow permanent shortcuts such as:

```text
mountain -> granite
river    -> sand
shore    -> sand
```

Final material synthesis must use completed morphology, hydrographic/depositional facts, geology and calibrated semantic material/Soil definitions.

## Ownership

```text
WorldGenesis / intent               authored generation meaning
V12 calibrator + recipe             ordinary base model
Mountain calibrator + recipe        V13 mountain model
ElevationField                      immutable generated surface fact
SurfaceMorphologyField              derived local geometry fact
TerrainShapeField                   immutable discrete shape-preparation fact
GeologyField                        immutable generated rock identity fact
TerrainMaterialField                immutable generated material-preparation fact
        ↓
materialization
        ↓
Landscape / Geometry runtime owners
```

Generated/prepared fields are not synchronized backward from later runtime Terrain mutation.

## Invariants

- Same Genesis/revision reproduces the same precise elevation.
- V12 land coverage is rank-calibrated rather than a hidden noise threshold.
- V13 zero mountain abundance preserves its V12 base exactly.
- V13 cannot create/delete coastline cells.
- Abundance owns mountain coverage; Scale owns individual size; Height owns bounded prominence; Chaininess owns elongation.
- Mountain synthesis and material synthesis do not branch on concrete runtime Shape classes.
- Shape fitting remains generic and may intentionally leave mountains partially or wholly impassable.
- Material composition depends on causal generated/local facts rather than feature-name special cases.

## Tests and acceptance

Primary coverage includes:

```text
V12LandformCalibrationTest
V12LandformElevationGenerationTest
V12BaseTerrainGeneratorTest
MountainArchitectureTest
MountainAbundanceCoverageTest
MountainMorphologyElevationGenerationTest
V13MountainTerrainGeneratorCompositionTest
V13SparseShapeGenerationTest
WorldGenerationPreviewSettingsTest
Generated World Audit
```

V12 and V13 visible morphology additionally require manual 2D/3D preview acceptance; numerical invariants do not substitute for visual judgement.

## Current limitations / next stage

The accepted terrain stack still does not implement:

- final dry river hierarchy, valleys, channels and lake-bowl carving (**Stage 2 — next**);
- final coherent geology/stratigraphy;
- caves/open underground volumes;
- final causal sediment/soil/exposed-bedrock synthesis;
- runtime erosion;
- biome authority;
- navigation connectivity repair.

## Code

Primary elevation implementation:

```text
simulation/.../world/atlas/V12BaseTerrainGenerator.java
simulation/.../world/atlas/V12LandformCalibrator.java
simulation/.../world/atlas/V12LandformRecipe.java
simulation/.../world/atlas/V12LandformElevationAlgorithm.java
simulation/.../world/atlas/V13MountainTerrainGenerator.java
simulation/.../world/atlas/MountainCalibrator.java
simulation/.../world/atlas/MountainRecipe.java
simulation/.../world/atlas/MountainElevationAlgorithm.java
simulation/.../world/atlas/MountainMorphologyAlgorithm.java
simulation/.../world/terrain/shape/*
```

See [V13 Mountain Generation](mountain-generation.md), [World Generation](overview.md), [World Genesis](world-genesis.md), [World Atlas](world-atlas.md), [ADR-011](../../decisions/011-world-generation-algorithm-contracts.md) and [ADR-021](../../decisions/021-world-preparation-and-calibration-boundary.md).

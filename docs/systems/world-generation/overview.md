# World Generation

## In plain language

World generation decides **what exists when a new world begins**. Runtime simulation decides **what happens after that world begins living**.

EvoForge also separates **what a human asks for** from **how an algorithm produces it**. Authored controls express world character; calibrators and versioned model recipes convert that meaning into exact values used by replaceable deterministic algorithms.

## Current status

**Stage 0 — architecture stabilization / V12 normalization is complete.**  
**Stage 1 — V13 Mountain Systems is complete and manually accepted.**

The next implementation stage is:

> **Stage 2 — Dry hydrography and carving**

Current code still contains useful typed seams with provisional drainage, hydrography, geology, initial-Water and material algorithms. Later stages replace/refine those algorithms behind their existing contracts where appropriate rather than accumulating feature-specific special cases.

## The central generation law

```text
human-authored semantic meaning
            ↓
validate / compile meaning
            ↓
world-specific domain calibration
            ↓
versioned model recipe (when needed)
            ↓
replaceable generation algorithm
            ↓
immutable typed generated facts
            ↓
preparation / materialization
            ↓
ordinary SimulationRuntime ownership
```

### Definitions and intent describe character

Authored data says things like:

```text
land coverage = medium
ordinary landforms = large
ruggedness = moderate
mountains = sparse, tall and elongated
```

Those values may be normalized semantic coordinates internally. They are not exact frequencies, slope limits, solver constants or rock identities.

### Calibration creates exact operating values

A domain calibrator combines semantic intent with world/environment context and resolves the exact values needed by one model.

Examples:

- V12 converts `landformScale` into exact ordinary-feature spacing and `ruggedness` into an exact local slope limit;
- V13 converts mountain `Scale`, `Height`, `Chaininess` and `Peak sharpness` into exact structure widths/lengths, height limits and cardinal rise budgets appropriate for the world dimensions.

Calibration is domain-owned. There is no universal GodCalibrator.

### Recipes own versioned model policy

Values that are neither authored meaning nor world-specific calibration live in immutable model recipes.

Examples:

```text
V12LandformRecipe
MountainRecipe
```

This keeps model choices out of spatial loops and lets another revision/model replace them without redefining user intent accidentally.

### Algorithms create typed facts

Downstream consumers see typed facts such as `ElevationField`. They do not need to know whether the surface came from V12, V13 or a test algorithm.

## Deterministic provenance

`WorldGenesis` is the immutable generation birth certificate:

```text
WorldSpec
masterSeed
GenerationRevision
RngRevision
WorldGenerationIntent
```

Generation randomness is call-order-independent and addressed by semantic stage/purpose + scope coordinates + ordinal. Historical revisions remain executable; intentional changes to durable generation semantics require explicit revision handling.

See [World Genesis](world-genesis.md).

## World Atlas: typed pre-runtime facts

The current `WorldAtlas` contains:

```text
WorldGenesis
ElevationField
GeologyField
ClimateNormalsField
DrainageField
HydrographyField
SurfaceHydrologyField
```

V13 mountains remain part of the precise `ElevationField`; there is no separate mountain fact merely for naming the feature because no independent downstream owner currently requires one.

The Atlas is not live Terrain, Water, Soil, weather or a scheduler.

See [World Atlas](world-atlas.md).

## Stage 0 — V12 ordinary base morphology **[COMPLETE]**

The accepted V12 path is:

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

V12 supplies:

- rank-calibrated land/ocean membership;
- coherent landmasses and fragmentation;
- coast interiority/gating;
- broad uplift;
- explicit ordinary hills/depressions;
- rolling relief;
- rugged ridge crests;
- bounded readable ordinary slopes.

It remains the ordinary-landscape base, not the dedicated mountain system.

## Stage 1 — V13 Mountain Systems **[COMPLETE]**

V13 composes a capped V12 base with a separate mountain stage:

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

The standard implementation generates deterministic asymmetric elongated structures. The important semantic ownership is:

```text
Abundance  -> expected mountain coverage on V12 land
Scale      -> individual transverse size / source lattice
Height     -> bounded vertical prominence
Chaininess -> long-axis elongation
Sharpness  -> readable geometric slope character
```

Height is constrained by vertical headroom, horizontal world size, Scale and the slope budget. If a requested height cannot fit a readable authored footprint, height is capped instead of inflating one structure across a continent.

The standard profile is constructed to produce broad discrete Z bands directly. Coast handling uses a compatible local-rise cap, and overlapping structures compose with `max` rather than additive spikes.

Mountain synthesis knows nothing about concrete Terrain Shapes or rock identity. Generic shape fitting runs later and deliberately uses sparse irregular coherent transitions rather than making every mountain fully traversable.

See [V13 Mountain Generation](mountain-generation.md) and [Terrain Generation](terrain-generation.md).

## Causal separation rules

### Mountains are geometry first

```text
mountain generation
        ↓
elevation geometry

geology
        ↓
rock identity

surface synthesis
        ↓
soil / sediment / exposed rock
```

Therefore generic code must not contain `if mountain -> granite`.

### Rivers and lakes are dry geometry before Water

The canonical order is:

```text
accepted mountain-bearing dry morphology
        ↓
drainage / watersheds / basins
        ↓
river hierarchy + outlets
        ↓
dry valley/channel/lake-bowl carving
        ↓
depositional/erosional context
        ↓
surface-material synthesis
        ↓
ONLY THEN finite initial Water
```

A river must still exist as geometry when Water rendering/quantity is absent.

### Shore does not mean sand

A shore may expose sand, gravel, silt, soil or rock according to completed morphology, geology and depositional causes. A coast/river/lake label alone does not own material selection.

### Puddles are runtime consequences

Puddles arise from terrain geometry, Soil hydraulics, precipitation and finite runtime Water. They are not painted world-generation objects.

## Canonical milestone sequence

### Stage 0 — Architecture stabilization and V12 normalization **[COMPLETE]**

- generation ownership audited;
- semantic intent separated from calibration/model/spatial synthesis;
- accepted V12 appearance protected;
- algorithms exposed through typed replaceable seams;
- preparation/runtime handoff documented and tested.

### Stage 1 — V13 Mountain Systems **[COMPLETE]**

- dedicated semantic `MountainIntent`;
- replaceable `MountainCalibrator` and `MountainElevationAlgorithm`;
- immutable `MountainCalibration` and versioned `MountainRecipe`;
- deterministic asymmetric mountain structures with bounded coverage/height/slope semantics;
- V12 land/ocean mask preserved;
- generic sparse shape-fitting remains independent;
- headless tests, Generated World Audit and manual 2D/3D acceptance completed.

### Stage 2 — Dry hydrography and carving **[NEXT]**

Starting from the accepted V13 dry surface:

- derive/reconcile drainage, watersheds and basins;
- produce real river hierarchy and outlets;
- carve readable dry valleys/channels;
- carve lake bowls and shore forms;
- remain completely dry.

Typed topology/morphology tests and manual dry-geometry acceptance are required.

### Stage 3 — Coherent geology

Replace placeholder geology with coherent formations/strata and only the deposit bodies required by the real model. Geology answers which material occupies underground volume, not where mountains should be.

### Stage 4 — Caves

Generate coherent underground voids through a separate replaceable algorithm using the geology/morphology causes available at that stage.

### Stage 5 — Causal surface layers and materials

Combine completed dry morphology, hydrographic/depositional facts, geology and calibrated semantic material/Soil definitions to synthesize surface/subsurface/sediment/exposed-bedrock composition.

### Stage 6 — Complete dry-world acceptance

Accept land/ocean morphology, mountains, dry river/lake geometry, geology/deposits, caves and surface/subsurface material structure before initial Water exists.

### Stage 7 — Finite initial Water fill

Put finite Water into already prepared oceans/lakes/channels. Generation owns initial placement only; ordinary runtime liquid/hydrology systems own later behavior.

### Stage 8 — Runtime handoff audit

Verify no generator, preparation helper or bootstrap remains a second source of truth after runtime starts.

## Provisional components

- current drainage provides useful topology but Stage 2 owns final dry network/carving;
- current threshold hydrography is provisional;
- current geology algorithm is provisional until Stage 3;
- current surface-hydrology/initial-Water ordering is historical compatibility infrastructure;
- current slope/concavity/drainage/shoreline material logic is an early slice until Stage 5.

## Structural authoring rule

Future channels, strata, deposits or caves follow the same law:

```text
simple semantic character
        ↓
domain calibration
        ↓
exact model input
```

Do not build a universal formation framework until multiple real consumers prove the common concept exists.

## Development protocol

World-generation work follows the repository-wide [Green Checkpoint Development](../../decisions/022-green-checkpoint-development.md) rule:

```text
one contract
   ↓
one independently meaningful component
   ↓
focused evidence/check
   ↓
green commit
   ↓
next block
```

A stage that changes visible facts remains Draft until required manual visual acceptance. An unexplained red checkpoint blocks further semantic work; diagnostic hypotheses are isolated instead of being accumulated into the production PR.

## Anti-patterns

Do not introduce:

- one giant generator/calibrator for unrelated domains;
- mutable universal generation contexts/service locators;
- material/rock/soil-name branches in generic orchestration;
- coordinate-random material placement as a substitute for causal structure;
- exact-physics authoring knobs where semantic coordinates are sufficient;
- scattered tunable magic constants inside spatial loops;
- parallel duplicate river/geology/formation frameworks beside existing typed seams;
- Water before dry channels/lakes/surface structure are accepted;
- visual-only fake rivers/lakes not backed by generated facts;
- downstream repair passes before proving which upstream stage first emits the wrong fact;
- a generator that continues controlling the world after runtime starts.

## Sources

The generation architecture is project-owned. External terrain research informs later physical models but V12/V13 are not claimed as direct implementations of a tectonic or erosion paper.

See [References](../../references.md), [Project Context](../../project-context.md), [Roadmap](../../roadmap.md), [World Genesis](world-genesis.md), [World Atlas](world-atlas.md), [Terrain Generation](terrain-generation.md), [V13 Mountain Generation](mountain-generation.md), [ADR-011](../../decisions/011-world-generation-algorithm-contracts.md) and [ADR-022](../../decisions/022-green-checkpoint-development.md).

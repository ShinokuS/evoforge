# World Generation

## In plain language

World generation decides **what exists when a new world begins**. Runtime simulation decides **what happens after that world begins living**.

That distinction sounds simple, but it prevents many architectural mistakes. A generator may create a dry river channel and an initial amount of Water; after startup, however, ordinary Terrain/Water/Soil systems own the changing world. The generator does not keep secretly steering rivers or repairing terrain behind the simulation's back.

EvoForge also separates **what a human asks for** from **how an algorithm produces it**. A player/content author can ask for “more rugged land” or “larger landforms”; they should not need to know noise frequencies, integer slope limits or erosion solver coefficients.

## Current status

**Stage 0 — architecture stabilization and V12 normalization is complete.**

The accepted V12 base terrain is the protected starting point for the next stages. The next implementation stage is:

> **Stage 1 — Mountain Systems**

Current code still contains several older/provisional Atlas algorithms (drainage, hydrography, geology, generated initial Water and early material placement). Their typed contracts are useful; their algorithms are not automatically accepted as the final versions for later stages.

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

Every arrow exists for a reason.

### Definitions and intent describe character

Authored data should say things like:

```text
land coverage = medium
landforms = large
ruggedness = moderate
soil = relatively fine and organic
```

Internally those may be normalized `0..1` coordinates. They are **not** exact physics and are not an invitation to expose every implementation constant as content JSON.

### Calibration creates exact operating values

A domain calibrator combines semantic intent with world/environment context and decides the exact values the chosen model needs.

For V12, for example, normalized `landformScale` becomes an exact feature spacing between 20 and 64 terrain cells, and normalized `ruggedness` becomes an exact maximum readable cardinal slope limit.

Calibration is domain-owned. EvoForge deliberately has no universal “GodCalibrator” that knows every Soil, Rock, climate, mountain and Water rule.

### A recipe versions algorithm policy

Some values are neither authored meaning nor world-dependent calibration; they are simply the choices that define one particular algorithm revision.

V12 stores those in `V12LandformRecipe`: coast transition length, component weights, feature-kernel ranges, slope-relaxation passes, warp policy and similar constants.

A future V13 could implement a completely different model behind the same `ElevationGenerator` contract.

### Algorithms create typed facts

The orchestrator knows contracts and dependencies, not concrete material/rock names or algorithm internals.

A downstream consumer sees an `ElevationField`; it should not need to know whether that field came from V12, a future tectonic model or a tiny test generator.

## Deterministic provenance

`WorldGenesis` is the immutable generation birth certificate:

```text
WorldSpec
masterSeed
GenerationRevision
RngRevision
WorldGenerationIntent
```

Generation randomness is call-order-independent: samples are addressed by semantic stage/purpose, scope coordinates and ordinal. This allows unrelated generation work to be added without shifting every later random value merely because the call sequence changed.

Historical revisions remain executable; intentional world-fact changes require explicit revision handling.

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

These are immutable generated facts. Some algorithms behind them are provisional, but the typed fact boundaries are already valuable.

The Atlas is not live Terrain, Water, Soil, current Weather or a scheduler.

See [World Atlas](world-atlas.md).

## Stage 0 V12 base morphology

The accepted base-terrain path is:

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

V12 provides:

- exact rank-calibrated land coverage;
- coherent landmass/fragment fields;
- coast interiority and coast relief gating;
- broad uplift;
- explicit balanced hills/depressions with cell-scale feature radii;
- rolling primary/detail relief;
- rugged ridge crests;
- deterministic bounded cardinal slope relaxation.

It intentionally does **not** define final mountain ranges, rivers, geology, caves or surface materials.

See [Terrain Generation](terrain-generation.md) for the equations and exact balanced recipe.

## Causal separation rules

### Mountains are geometry first

A mountain primarily changes morphology/elevation. It is not a material name.

```text
mountain generation
        ↓
high-relief/range geometry

geology
        ↓
what rock occupies that volume

surface synthesis
        ↓
soil / sediment / exposed rock

future climate/atmosphere
        ↓
altitude effects
```

Therefore generic code must not contain `if mountain -> granite`.

### Rivers and lakes are dry geometry before Water

A river is not a blue texture and a lake is not a magic Water object.

The canonical order is:

```text
final dry morphology
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

When Water rendering is disabled, accepted channels and lake bowls must still exist and make physical sense.

### Shore does not mean sand

A shore may expose sand, gravel, silt, soil or rock depending on the final morphology, geology and depositional conditions. A river/lake/coast label alone is not enough to choose material.

### Puddles are runtime consequences

Puddles arise from terrain geometry, Soil hydraulics, actual precipitation and finite runtime Water. They are not world-generation objects painted into the Atlas.

## Canonical milestone sequence

### Stage 0 — Architecture stabilization and V12 normalization **[COMPLETE]**

Completed responsibilities:

- audit the generation/preparation ownership path;
- protect the accepted V12 visual baseline;
- separate semantic intent, world calibration, V12 recipe and spatial algorithm;
- keep algorithms replaceable through typed seams;
- make generated-world preparation compositional;
- identify provisional code rather than quietly extending it;
- reconcile package ownership and the canonical pipeline;
- complete the context/documentation closeout represented by this documentation set.

### Stage 1 — Mountain Systems **[NEXT]**

Purpose:

- generate sparse coherent mountain provinces/ranges;
- organize foothills, ridges and peaks above the accepted ordinary landscape;
- keep mountain geometry separate from rock identity;
- introduce only semantic authored controls and calibrate them into exact model parameters;
- make the result directly observable in a dedicated preview;
- preserve ordinary V12 character when mountain intent is zero/low.

The exact mountain model is **not yet decided by this page**. Stage 1 begins with research + contract design and should choose the smallest model that satisfies those observable requirements.

### Stage 2 — Dry hydrography and carving

From the mountain-bearing dry surface:

- derive flow/drainage structure;
- identify watersheds and basins;
- derive river hierarchy and outlets;
- carve readable valleys/channels;
- carve lake bowls/shore forms;
- remain completely dry.

Typed topology tests must cover network/basin continuity; visual acceptance must prove the dry geometry is plausible.

### Stage 3 — Coherent geology

Replace placeholder geology with coherent formations/strata and the deposits actually required by the chosen geology model.

Geology answers **which material occupies underground volume**, not where mountains should be.

### Stage 4 — Caves

Generate coherent underground voids through a separate replaceable algorithm using the geology/morphology causes available at that stage.

### Stage 5 — Causal surface layers and materials

Combine completed dry morphology, hydrographic/depositional facts, geology and calibrated semantic material/Soil definitions to synthesize surface/subsurface/sediment/exposed-bedrock composition.

### Stage 6 — Complete dry-world acceptance

Before initial Water exists, accept the full physical dry world:

```text
land/ocean base
mountains
dry rivers/lakes
geology/deposits
caves
surface/subsurface materials
```

This includes deterministic audits, visual acceptance and representative performance profiling.

### Stage 7 — Finite initial Water fill

Put finite Water into already prepared oceans/lakes/channels. Generation owns only the initial quantity/placement; ordinary runtime liquid/hydrology systems own later Water behavior.

### Stage 8 — Runtime handoff audit

Verify no generator, preparation helper or bootstrap remains a second source of truth after runtime starts. This closes the world-generation milestone.

## Provisional components in current code

### Drainage

Current drainage provides deterministic downstream topology, terminal basins and contributing area. It is useful analytical infrastructure. It is **not** accepted fluvial erosion or final valley/channel geometry.

### Hydrography

Current hydrography is a threshold-style derived footprint. Stage 2 owns the real dry network/hierarchy/carving model.

### Geology

Current geology has typed fields/profile loading and deterministic placeholder placement. Stage 3 owns final coherent geology.

### Surface hydrology / generated Water

The existing Atlas path can describe initial Water/shoreline facts and bootstrap them. Canonical Stage 7 will reposition/refine this after the dry world is complete.

### Terrain materials

Current slope/concavity/drainage/shoreline material logic is an early causal slice. Stage 5 owns the final synthesis and must not inherit provisional feature-label shortcuts as permanent rules.

## Structural authoring rule

Future mountain ranges, strata, deposits, channels or caves may need higher-level authored pattern descriptions. They follow the same law:

```text
simple semantic character
        ↓
domain calibration
        ↓
exact model input
```

Do not build a universal “formation framework” before two or more real consumers prove the same structure exists.

## Development protocol

One world-generation stage uses one PR. Inside that PR, prefer understandable internal steps:

```text
audit / research
    ↓
contracts + authored semantics
    ↓
calibration / model recipe
    ↓
algorithm
    ↓
diagnostics / preview
    ↓
tests + performance
    ↓
documentation / cleanup
```

A stage that changes visible generated facts remains Draft until manual visual acceptance when appropriate.

If implementation proves the documented pipeline wrong:

1. identify the concrete contradiction;
2. change the smallest owning contract;
3. update this page in the same PR;
4. record durable rationale in an ADR when needed;
5. update tests/diagnostics/acceptance;
6. continue from the documented baseline.

Do not silently drift.

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
- a background generator that continues controlling the world after runtime starts.

## Sources

**Internal EvoForge architecture:** semantic definition → calibration → typed replaceable algorithm → generated fact → runtime handoff is the project's current architectural contract.

**Conceptual terrain research:** Génevaux et al. (2013) demonstrates procedural terrain organized around hydrologic structure; Cordonnier et al. (2016) separates tectonic uplift and fluvial erosion at large scale. These sources inform later Stage 1/2 research direction, but current V12 is not a direct implementation of either model.

See [References](../../references.md), [Project Context](../../project-context.md), [Roadmap](../../roadmap.md), [World Genesis](world-genesis.md), [World Atlas](world-atlas.md), [Terrain Generation](terrain-generation.md), and [ADR-011](../../decisions/011-world-generation-algorithm-contracts.md).

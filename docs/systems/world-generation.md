# World generation

This document is the **canonical source of truth** for the current world-generation milestone. If implementation work changes the pipeline, this document changes in the same PR. Chat history, old notes and old prototypes do not override it.

## Current status

- **Stage 0 — architecture stabilization:** in progress in PR #108.
- Accepted V12 terrain appearance is a protected baseline.
- The current V12 implementation is being separated into semantic intent → calibration → algorithm recipe → spatial synthesis without changing the accepted terrain.
- Mountains, carved river/lake geometry, final geology, caves and final surface-material synthesis are not yet accepted implementations.
- Initial generated Water remains a later stage. River/lake geometry is generated dry first.

## Core ownership law

Generation answers **what exists at world start**. Runtime Simulation owns **what happens afterwards**.

The generated world is one continuous XYZ space. Internal dense/sparse/chunked representations are implementation details; consumers depend on typed world facts, not storage layout.

A generated fact must have exactly one clear owner, an explicit consumer and an observable acceptance test.

## Authoring law: semantic definitions, generated complexity

Human-authored definitions describe **character**, not exact physics.

Example:

```json
{
  "key": "core:some_soil",
  "aspects": {
    "soil": {
      "absorbency": 0.8,
      "fineness": 0.55,
      "organicMatter": 0.7,
      "compactness": 0.25
    }
  }
}
```

`absorbency = 0.8` means “strongly absorbent on the authored semantic scale”. It does not mean a specific hydraulic conductivity, pore volume or water capacity.

The canonical conversion is:

```text
human-authored Definition JSON
(normalized semantic coordinates)
              ↓
semantic Definition compiler
(validate + compile meaning only)
              ↓
compiled semantic definition
              ↓
      + world/environment context
      + generated local causes
              ↓
domain calibrator / resolver
              ↓
exact world-specific generated profile
(physical / operational values)
              ↓
spatial generation / runtime consumer
```

### Non-negotiable consequences

- Definitions do not expose dozens of low-level physical coefficients merely because the engine uses them internally.
- Definition compilers do not secretly perform world-dependent physics.
- Calibration is domain-owned. There is no `GodCalibrator` that knows Soil, Rock, Climate, Water, ecology and everything else.
- Generated complexity may be large. Authoring complexity must remain minimal and semantic.
- New content that uses known mechanics should normally be data-only.
- A genuinely new mechanic introduces a narrow local contract, compiler/calibrator if required, generated facts and tests.

## Algorithm law: replaceable stages

Every serious generation stage follows this shape:

```text
semantic/calibrated inputs
          ↓
      stage contract
          ↓
   ┌──────┴──────┐
   ↓             ↓
algorithm A   algorithm B
   │             │
   └──────┬──────┘
          ↓
 immutable typed facts
```

The orchestrator knows contracts and dependencies. It does not branch on concrete material keys, rock names, soil names or implementation revisions except where explicit compatibility routing is required.

A downstream stage must not know whether elevation came from V12, a future tectonic model or a test generator.

### No scattered policy hardcode

World/model policy must not live as unexplained magic literals inside spatial loops.

The separation is:

```text
Authored semantic intent
        ↓
Calibration
        ↓
exact world-specific operating values
        +
versioned algorithm recipe/model parameters
        ↓
spatial algorithm
```

Pure mathematical identities and representation constants may remain inside an implementation. Tunable model choices belong in an explicit immutable recipe/configuration. World-dependent operating values belong in calibration.

This is the rule used by the V12 Stage-0 refactor.

## Current V12 boundary

The accepted V12 terrain remains the ordinary-landscape baseline: coherent land/ocean membership, coast transition, broad uplift, explicit hills/depressions, rolling relief and rugged ridges.

V12 is **base morphology**, not final geology and not a mountain/geology/river object model.

The Stage-0 structural boundary is:

```text
WorldGenesis
  └─ normalized WorldGenerationIntent
              ↓
     V12LandformCalibrator
              ↓
     V12LandformCalibration
       exact per-world values
              +
       V12LandformRecipe
       versioned model choices
              ↓
  V12LandformElevationAlgorithm
              ↓
        ElevationField
```

`V12BaseTerrainGenerator` is a normal `ElevationGenerator`, so it can be replaced without changing consumers. Legacy revision routing remains only as a compatibility facade.

The accepted V12 visual output is protected during this refactor. Later mountain and channel stages extend/transform the typed morphology explicitly; they do not silently retune V12 until it becomes a different algorithm.

## Existing reusable architecture

The repository already contains useful seams that must be preserved rather than duplicated:

- `WorldGenesis` + `GenerationRandom` — deterministic provenance and sampling root;
- `WorldGenerationAlgorithms` — replaceable atlas algorithm composition;
- `WorldAtlasGenerator` — orchestration of typed atlas facts;
- `ElevationGenerator`, `GeologyGenerator`, `ClimateNormalsGenerator`, `DrainageGenerator`, `HydrographyGenerator`, `SurfaceHydrologyGenerator` — existing typed algorithm boundaries;
- `SurfaceMorphologyField` — reusable derived local topology facts;
- terrain profile/material-role JSON — authored palette/role selection, not a universal placement engine;
- Soil semantic compilation + domain calibration — existing example of the desired Definition → calibration separation;
- `GeneratedWorldPreparation` — current generated-facts-to-prepared-world boundary;
- runtime `LiquidSystem` / Water ownership — authoritative post-generation finite-water behavior.

Existing code is not accepted merely because it exists. Provisional algorithms are replaced behind their contracts when their real stage is reached.

## Causal separation laws

### Mountains

A mountain is primarily generated **geometry/morphology**, not a hard-coded rock or terrain type.

```text
mountain/range generation
        ↓
high relief / ridge / exposed geometry

geology
        ↓
which rock actually occupies that volume

surface synthesis
        ↓
soil / sediment / exposed bedrock at the surface

future climate/atmosphere
        ↓
altitude-dependent temperature / pressure / snow potential
```

Elevation must remain a usable environmental cause so future climate/atmosphere mechanics can consume it. Snow caps, thin air and altitude physiology are deliberately deferred; the architecture must not block them.

### Rivers and lakes

A river is not a blue overlay and a lake is not a predeclared Water object.

```text
final dry morphology
      ↓
drainage / watersheds
      ↓
basins + outlets + river hierarchy
      ↓
dry channel / valley / lake-bowl carving
      ↓
deposition / erosion context
      ↓
surface-material synthesis
      ↓
ONLY THEN finite initial Water
```

Channel and lake geometry must remain visible and valid when Water rendering is disabled.

### Shores and sediment

“Shore = sand” is forbidden as a universal rule. A coast, lake edge or river bank may expose sand, silt, gravel, soil or rock depending on morphology, geology, depositional conditions and calibrated material properties.

Generic consumers use semantic roles/typed facts. They do not contain `if (river) sand` or `if (mountain) granite` branches.

### Puddles

Puddles are runtime consequences of geometry + calibrated soil + actual precipitation + finite Water. They are not worldgen objects and local hydraulic variation must not be invented from coordinate noise merely to look natural.

## Structural/pattern authoring

Some future generators may need authored structural descriptions: mountain-range character, stratigraphic character, deposit shape, channel character, cave character, and similar patterns.

Those descriptions follow the same authoring law: simple normalized semantic coordinates, not exact implementation knobs.

Do **not** create a universal formation/pattern framework in advance. The first real consumer introduces the smallest typed pattern contract it needs. Shared abstractions are extracted only after multiple real consumers prove the common structure.

## Canonical milestone pipeline

This sequence is locked. A change requires an explicit reason and an update to this document in the same PR.

### Stage 0 — Architecture stabilization and V12 normalization **[ACTIVE]**

Purpose:

- audit the authoritative worldgen/preparation path;
- eliminate contradictory ownership/documentation;
- make accepted V12 a clean replaceable `ElevationGenerator` unit;
- restore explicit semantic intent → calibration → recipe → algorithm separation;
- keep all V12 terrain behavior visually unchanged;
- record provisional components that will be replaced later rather than extending them accidentally.

Acceptance:

- existing V12 visual acceptance laws still pass;
- deterministic outputs remain stable;
- calibration is deterministic and seed-independent for identical semantic/world policy;
- the V12 revision route and the replaceable V12 generator are bit-identical;
- generic worldgen composition can substitute algorithms through contracts;
- canonical docs and PR scope agree.

### Stage 1 — Mountain systems

Purpose:

- extend the accepted base morphology with sparse coherent mountain provinces/ranges, foothills, ridges and peaks;
- keep mountain geometry independent from rock identity;
- ensure world vertical capacity can represent mountains and later underground geology without globally inflating ordinary V12 relief.

Inputs: accepted base elevation + calibrated mountain intent/pattern.

Output: mountain-bearing dry morphology / elevation facts.

Acceptance: dedicated preview layer and visual acceptance at several world sizes/seeds; zero/low mountain intent preserves ordinary V12 character.

### Stage 2 — Dry hydrography and carving

Purpose:

- derive watersheds, flow accumulation, depression/basin analysis, lake basins/outlets and river hierarchy from the mountain-bearing surface;
- carve physically readable valleys, river channels, lake bowls and shore forms;
- keep the world completely dry.

Outputs are typed hydrographic and morphology facts, not runtime Water.

Acceptance: with Water hidden, river channels and lake bowls are visible, connected and plausible; deterministic topology tests cover outlets/basins/network continuity.

### Stage 3 — Coherent geology

Purpose:

- replace the provisional geology implementation with authored/calibrated rock definitions placed as coherent geology;
- generate meaningful layered strata and coherent formations;
- support distinct deposits, lenses, veins/seams or other bodies only as required by the real geology model;
- expose rock identity through typed geology fields for later caves and surface synthesis.

Acceptance: geology slice/cross-section visualization, deterministic continuity/invariant tests, no independent per-cell rock lottery.

### Stage 4 — Caves

Purpose:

- generate coherent underground void systems using geology and morphology/hydrological causes available at that point;
- keep cave algorithm replaceable and separate from geology ownership.

Acceptance: underground slice/3D visualization, connectivity/volume/bounds tests, no generic noise-only cave field accepted without causal/visual evidence.

### Stage 5 — Surface layers and material synthesis

Purpose:

Combine:

- final dry morphology;
- dry hydrographic/depositional facts;
- geology;
- climate/environment where currently meaningful;
- authored semantic material/soil/rock definitions;
- world-specific calibrated physical profiles.

Produce coherent surface/subsurface/sediment/exposed-bedrock composition. Sand/silt/gravel/soil/rock placement follows causes, not hard-coded feature labels.

This is where existing terrain material roles and Soil semantic/physical calibration are reused/refined.

Acceptance: material/geology preview modes plus tests for causal placement and typed-role independence.

### Stage 6 — Dry-world acceptance gate

At this point the world must be complete **without Water**:

- land/ocean morphology;
- mountains;
- dry river channels and lake bowls;
- geology/strata/deposits;
- caves;
- surface soils/sediments/exposed rock.

Perform visual acceptance, deterministic audits and performance profiling before adding Water.

### Stage 7 — Initial Water fill

Fill already prepared oceans, lakes and river channels with finite initial Water using the existing authoritative liquid boundary.

Generation owns only the initial quantity/placement. After bootstrap, normal runtime liquid/hydrology systems own Water.

Acceptance: correct initial fill/conservation and no hidden generation-time controller that continues steering Water after tick 0.

### Stage 8 — Runtime handoff audit and milestone close

Verify generated facts materialize once, runtime owns subsequent state, and no generator remains a second source of truth.

This closes the current **world-generation** milestone. Flora, fauna, settlements, agents, economy and broader ecology require a separate plan.

## Current provisional components

These are useful typed seams but **not final algorithms**:

- current `GeologyGenerationStage` — deterministic placeholder geology;
- current threshold-style hydrography — analytical footprint, not final river/lake morphology;
- current `SurfaceHydrologyGenerationStage` — existing initial-water path that must be repositioned after dry-world completion before final acceptance;
- current `TerrainMaterialGenerationStage` slope/deposition rules — early vertical slice, not final causal surface synthesis;
- current generated-preparation ordering — useful boundary but will evolve as real mountain/hydro/geology/surface stages acquire typed dependencies.

Do not improve these placeholders by accumulating special cases. Replace/narrow them in their assigned stage.

## Development discipline

### One stage = one PR

- Reuse the current stage PR until that stage is accepted.
- Do not open parallel worldgen PRs for pieces of the same stage.
- Before starting the next stage, merge/close obsolete worldgen PRs and ensure `develop` is the single baseline.

### Small internal steps inside a stage

Each stage PR should progress through reviewable commits:

1. audit/contracts;
2. calibration/data boundary;
3. algorithm implementation;
4. diagnostics/preview;
5. tests/performance;
6. cleanup/documentation.

A stage may be large, but its internal changes remain understandable.

### Visualization gate

Every stage that changes generated world facts must expose those facts in developer visualization before merge. Visual acceptance is separate from unit tests.

Preview remains observer/tooling only. It never becomes an alternate simulation truth or influences generation results.

### Change protocol

When implementation proves this plan incomplete:

1. identify the concrete contradiction/requirement;
2. change the smallest owning contract;
3. update this document in the same PR;
4. record the reason in the stage report/audit;
5. update tests/preview acceptance;
6. continue from the new documented baseline.

Do not silently drift from the plan.

## Explicit anti-patterns

Do not introduce:

- giant generators/calibrators that own unrelated domains;
- concrete material/rock/soil branching in generic orchestration;
- coordinate-random material selection as a substitute for causal structure;
- duplicated river/geology/formation frameworks alongside existing typed seams;
- authored exact-physics JSON where normalized semantic character is sufficient;
- hidden world policy embedded as magic constants throughout spatial loops;
- future-facing abstractions with no real consumer;
- generated Water before dry channel/lake/surface structure is accepted;
- visual-only fake rivers/lakes that are not backed by authoritative generated facts.

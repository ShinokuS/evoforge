# World-generation pipeline audit — 2026-08

This audit accompanies Stage 0 of the world-generation rebuild. The accepted V12 terrain looks good; the problem is architectural: several generations of experiments left semantic authoring, calibration, spatial generation, provisional geology/hydrology and material preparation easy to conflate.

The goal of Stage 0 is **not** to change the world visually. It is to establish one documented pipeline and make the accepted terrain a clean replaceable unit before mountains, dry channels/lakes and geology are implemented.

The canonical plan is [`../systems/world-generation.md`](../systems/world-generation.md). This note records what currently exists and what must happen to it.

## Audit rubric

- **KEEP** — correct owner/contract and active implementation;
- **KEEP / NARROW** — useful and active, but its responsibility is smaller than its name/history may suggest;
- **REFACTOR NOW** — conflicts with Stage-0 architecture and must be corrected without changing accepted behavior;
- **REPLACE IN ASSIGNED STAGE** — typed seam is useful, current algorithm is provisional;
- **DEFER** — do not build before the canonical stage/real consumer.

## Findings

| Area | Verdict | Finding |
|---|---|---|
| `WorldGenesis` / deterministic RNG | KEEP | Correct provenance root. Human semantic intent and deterministic sampling are already separate concepts. |
| `WorldGenerationAlgorithms` | KEEP | Good replaceable atlas composition seam. Extend through contracts; do not replace with a giant world generator. |
| `WorldAtlasGenerator` | KEEP / NARROW | Good orchestrator for current typed atlas facts. Its dependency graph will evolve when real mountain/channel facts exist. |
| V12 elevation | REFACTOR NOW | Visual behavior is accepted, but semantic intent, world calibration and algorithm policy were mixed inside one large spatial implementation. Stage 0 separates these without changing output. |
| legacy V1-V11 elevation revisions | KEEP / FREEZE | Compatibility history. Do not refactor their outputs incidentally while normalizing V12. |
| Drainage | KEEP / NARROW | Useful analytical topology. It is not erosion/carving and must be recomputed/consumed from the correct final dry morphology when Stage 2 arrives. |
| Hydrography | REPLACE IN STAGE 2 | Current threshold channel footprint is useful test infrastructure, not the final basin/lake/river hierarchy or channel morphology. |
| Surface hydrology / generated Water | KEEP SEAM / MOVE LATER | Existing finite-water handoff is valuable, but canonical milestone ordering now puts initial Water after the complete dry world. Do not let present ordering dictate future architecture. |
| Climate normals | KEEP / NARROW | Generated environmental fact. Elevation remains available for future altitude consequences; this milestone does not implement snow/thin-air physiology. |
| Geology field/profile contracts | KEEP | Typed geology identity and authored profile loading are useful. |
| Current geology algorithm | REPLACE IN STAGE 3 | Coarse deterministic provinces/strata are a placeholder, not final layered geology. Do not accumulate more random rock cases. |
| Surface morphology | KEEP | Useful derived local slope/convexity/concavity facts. Must derive from the appropriate final dry morphology at the point of consumption. |
| Terrain shape | KEEP / NARROW | Geometry/presentation preparation remains separate from material identity. |
| Terrain profile/material-role JSON | KEEP / NARROW | Role palette (`surface`, `subsurface`, `sediment`, `bedrock`), not a universal spatial distribution engine. |
| `TerrainMaterialGenerationStage` | REPLACE/REFINE IN STAGE 5 | Current fixed slope/deposition-depth policy is an early causal slice. It must not grow `river => sand`, `mountain => granite` special cases. |
| Soil semantic compiler/calibration | KEEP | Strong existing example of semantic Definition → generated physical calibration. Reuse this architecture. |
| `GeneratedWorldPreparation` | KEEP / EVOLVE | Useful explicit generated-fact → prepared-world boundary, but current ordering is historical and will change as canonical dry stages become real. |
| runtime Water/Liquid system | KEEP | Correct post-bootstrap owner. Worldgen must not invent a second Water engine. |
| 2D/3D preview | KEEP / EXPAND PER STAGE | Required observer/acceptance tool. Add layers only for real generated facts. |

## Primary Stage-0 defect: V12 mixed three responsibilities

Before this refactor, `V12LandformElevationGenerator` directly did all of the following:

1. read normalized `WorldGenerationIntent`;
2. convert it to exact operating scales/limits;
3. own V12 model constants/weights;
4. perform spatial synthesis.

That made the accepted terrain harder to reason about and encouraged future tuning by adding more constants to a large implementation.

Stage 0 introduces the explicit boundary:

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

`V12BaseTerrainGenerator` implements the existing `ElevationGenerator` contract and composes those pieces. The old class name remains only as a revision-router compatibility facade.

The balanced recipe contains the exact model choices that produced the accepted V12 appearance. The calibrator converts semantic world intent and world bounds into exact per-world values. The spatial algorithm no longer owns those policies.

## Existing Definition/calibration architecture is not to be discarded

The repository already contains the correct architectural idea in the Soil path:

- authored Soil values are normalized semantic character;
- `SoilDefinitionCompiler` compiles semantic facts rather than hydraulic numbers;
- domain calibration resolves physical properties later using generated/world context.

Worldgen should converge on this pattern rather than invent a second authoring philosophy for rocks, terrain or hydrology.

A future rock Definition may expose semantic characteristics suitable for authoring. The geology/calibration domain may derive many exact physical values. Authors do not manually enter every simulation coefficient unless it is a genuine semantic override.

## Geology finding

The current geology implementation is deterministic and typed, but its model is provisional. Coarse horizontal provinces plus fixed-height/randomly selected strata do not yet express the layered/coherent geology required by the canonical milestone.

Keep the contracts/loaders/keys that remain useful. Replace the algorithm in Stage 3 using the smallest real set of formation/strata/deposit abstractions required by the implemented geology and its visualization.

Do not build a universal Spatial Formation framework now.

## Hydrology finding

The repository already has drainage, hydrography and initial-water code. This code is not “dead”, but its current presence must not force the final pipeline to fill Water too early.

Canonical ownership is now:

```text
mountain-bearing dry morphology
        ↓
watersheds / basins / outlets / river hierarchy
        ↓
dry channel + valley + lake-bowl carving
        ↓
geology + caves + surface-material synthesis
        ↓
dry-world acceptance
        ↓
finite initial Water
        ↓
runtime LiquidSystem ownership
```

Stage 2 should reuse correct analytical drainage ideas and replace/narrow simplistic channel logic rather than adding an unrelated second river subsystem.

## Surface-material finding

Current terrain material generation already separates material roles from geology identity, which is useful. However its present slope/deposition constants and forced minimum deposition on shoreline cells are not a final geomorphological model.

The final Stage-5 system must decide surface/subsurface/sediment/exposed-rock composition from morphology + hydrographic/depositional facts + geology + calibrated semantic material definitions.

This is where ocean shores can legitimately become sand in some places and exposed rock/cliffs elsewhere without feature-name special cases.

## God-object / dependency audit

The top-level atlas path is better than feared: replaceable interfaces already exist and `WorldGenerationAlgorithms` is a composition bundle rather than one giant implementation.

The main risk is **policy accumulation inside concrete stages**, not the need to rewrite every worldgen class from zero.

Rules from this audit:

- orchestrators coordinate only;
- domain calibrators calibrate only their domain;
- spatial algorithms consume typed/calibrated inputs;
- generated fields carry immutable facts;
- materialization/runtime consume facts without reverse-owning generation;
- compatibility routers do not become homes for new production policy.

## What Stage 0 intentionally does not implement

- mountain provinces/ranges;
- river hierarchy, lake basins or carving;
- final layered geology;
- deposits/veins;
- caves;
- final soil/sediment/bedrock placement;
- snow caps, atmospheric pressure or altitude physiology;
- a generic pattern/formation framework;
- new Water behavior.

Those belong to their documented stages and will be visualized/tested there.

## Stage-0 acceptance checklist

- [x] one canonical pipeline document exists;
- [x] existing worldgen ownership audited;
- [x] V12 semantic calibration extracted from the spatial algorithm;
- [x] V12 model constants collected into an immutable versioned recipe;
- [x] V12 exposed as a normal replaceable `ElevationGenerator` unit;
- [x] legacy revision routing retained as compatibility only;
- [x] calibration contract tests added;
- [x] direct V12 generator and revision route asserted bit-identical;
- [ ] full repository CI green on final PR head;
- [ ] manual V12 preview smoke check confirms accepted visual baseline remains unchanged;
- [ ] final PR audit/cleanup complete and obsolete parallel worldgen PRs absent.

When these final checks pass, merge Stage 0 before starting Stage 1.

# V12 base-terrain visual acceptance baseline

- Type: Acceptance
- Status: Current acceptance baseline
- Date: 2026-08-18
- Normative: No

## Context

V12 was introduced to replace two visibly unacceptable terrain-generation failure modes: small worlds that looked like cell-to-cell elevation noise, and larger worlds that stretched into giant featureless plateaus.

The goal was a deterministic **base morphology** with readable coasts, ordinary hills/depressions, rolling relief and stronger ridge structures whose feature scale remains stable in terrain cells as the world becomes larger.

This acceptance happened before Stage 1 Mountain Systems. V12 is intentionally not the final mountain/river/geology model.

## What was observed

The accepted V12 implementation produced:

- coherent land/ocean masses rather than per-cell noise;
- readable ordinary hills and depressions at compact world sizes;
- more landforms, rather than proportionally stretched landforms, as world dimensions grow;
- rolling local relief subordinate to the larger landform structure;
- stronger rugged ridge features controlled independently by semantic ruggedness;
- coast transitions that remain visually readable instead of carrying full inland relief directly to the shoreline;
- bounded cardinal slope transitions that are legible in the discrete Terrain representation.

The user manually inspected the generated result and explicitly described the generation as visually excellent after the final V12 redesign.

## Outcome

The V12 appearance became a protected Stage 0 baseline. The subsequent Stage 0 architecture refactor was required to remain bit-identical/deterministically equivalent to that accepted output while splitting responsibilities into semantic intent, calibration, recipe and spatial synthesis.

Two preview-only polish issues were then fixed and smoke-checked:

1. 2D detailed rendering switches to coarser LOD before the former expensive near-x2 boundary so preview FPS does not collapse while still drawing too many exact cells.
2. `Random seed on Generate` chooses a new 64-bit seed when enabled but writes the exact selected value into the normal visible/copyable Seed field before generation, preserving reproducibility.

These changes affect developer presentation/workflow only; they do not change V12 terrain facts for a given Genesis.

## What became canonical

The accepted model is documented in:

- [Terrain Generation](../../systems/world-generation/terrain-generation.md)
- [World Genesis](../../systems/world-generation/world-genesis.md)
- [World Generation](../../systems/world-generation/overview.md)
- [Visualizer](../../systems/tooling/visualizer.md)

The protected Stage 0 architecture is:

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

Stage 1 Mountain Systems must build over this baseline. It must not quietly turn V12 into a mountain generator by adding more undifferentiated noise/tuning.

## Links forward

- [Project Context](../../project-context.md)
- [Roadmap](../../roadmap.md)
- [Stage 0 World-generation Pipeline Audit](../audits/2026-08-world-generation-pipeline.md)

# V14 standing-water bathymetry visual acceptance

- Date: 2026-08-20
- Scope: World-generation Stage 2A — standing-water bathymetry
- Status: Accepted

## What was accepted

The current V14 result was manually reviewed after the owning deterministic tests and generated-world audit checkpoints were green.

The accepted visual and semantic character is:

- the existing V13 lake/sea/ocean footprint remains the standing-water footprint; V14 does not create or delete water bodies;
- land and shoreline membership remain unchanged;
- near-shore bottom morphology descends smoothly and reads naturally rather than as a fixed-depth cutout;
- broad adjacent coastal land relief can influence ocean-connected descent without creating noisy local spikes;
- competing coasts blend through bays without the sharp nearest-owner wedge artifact rejected during development;
- narrow water remains shallow when there is not enough horizontal room for a clean deep profile;
- large/deep water bodies contain broad local depressions, highs and saddles rather than resolving into one uniform center bowl;
- the deep interior does not look like cell-scale noise or a repeated egg-carton pattern;
- depth and structural relief remain readable at terrain-cell scale;
- the final preview applies `Z contrast` to negative Z as depth-dependent darkening while preserving the established positive-Z mountain/land behavior.

The final user acceptance explicitly closed the question of seabed morphology and the current lake/sea/ocean shapes.

## Rejected directions during development

Several intermediate implementations were intentionally rejected and removed from branch history. The durable lessons are:

1. Direct nearest-coast ownership can create visible Voronoi/wedge seams where coast influences compete. The accepted coast blends broad causal context through connected water instead.
2. A coastal fall budget that permits visually active one-cell Z bands is too steep for the intended terrain presentation. The accepted coast stays below half a cell of cardinal fall per XY step.
3. Adding deep structural perturbations on top of the accepted bowl can sum gradients and violate the total slope budget. The accepted deep model composes independent slope-bounded surfaces through `max/min` instead.
4. A mathematically safe deep surface that is too weak to cross the old bowl is not useful merely because it passes slope tests. The accepted model is required to create real broad regions both deeper and shallower than the old equal-clearance baseline.
5. High-frequency noise is not a substitute for large-scale bottom structure.

## Automated evidence paired with the manual review

The accepted implementation is covered at the owning boundaries by:

- `BathymetryArchitectureTest`;
- `BathymetryMorphologyElevationGenerationTest`;
- `DeepBathymetryStructureGenerationTest`;
- `V14BathymetryTerrainGeneratorCompositionTest`;
- `V14BathymetryElevationGenerationTest`;
- `WorldGenerationElevationTintTest`;
- repository CI;
- Generated World Audit.

The final presentation checkpoint passed CI #1809 and Generated World Audit #534 before this acceptance record was written.

## Acceptance boundary

This acceptance applies to **Stage 2A standing-water footprint preservation, shore/coastal bathymetry, deep standing-water bottom morphology and its preview depth contrast**.

It does not accept drainage topology, river networks, river/valley carving, geology, caves, final materials or finite initial Water. Those remain separate later concerns.

Later Stage 2 work must treat the accepted V14 standing-water geometry as protected input. Reopening the lake/sea/ocean footprint or bathymetry requires a new explicit contract rather than an incidental downstream change.

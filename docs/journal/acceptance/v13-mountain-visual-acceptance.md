# V13 mountain visual acceptance

- Date: 2026-08-19
- Scope: World-generation Stage 1 — V13 structural mountains
- Status: Accepted for final integration

## What was accepted

The final V13 mountain result was manually reviewed in both 2D and 3D preview after deterministic tests and generated-world audits were already green.

The accepted visual character is:

- mountains grow out of the V12 landscape as broad smooth landforms rather than abrupt walls;
- structures are clearly larger/taller than ordinary V12 hills while remaining readable at terrain-cell scale;
- chaininess produces elongated mountain forms without reverting to noisy repeated ridge/peak bands;
- discrete elevation bands are broad enough to avoid one-cell contour chatter on normal slopes;
- coast contact is allowed, including ordinary cliff-like situations, without dedicated mountain uplift producing extreme shoreline cutoffs;
- world-size calibration prevents small worlds from receiving absurdly tall structures while allowing large worlds to use substantially more vertical headroom;
- sparse generic transition-shape fitting does not create a regular mechanical ramp pattern and is not required to make mountains globally traversable.

The final user acceptance statement for the resulting preview was that the result was ideal and ready to be fixed as the Stage 1 baseline.

## Rejected visual directions during development

Historical prototypes are not part of the accepted model, but two lessons are durable:

1. A repeated ridge/peak/saddle composition produced noisy 2D structure and abrupt high walls. The accepted model instead uses one bounded asymmetric elongated hill profile per source.
2. Post-generation smoothing/repair can hide the real source of bad geometry and flatten intended height. The accepted model creates readable slope/Z-band behavior directly through calibrated source geometry and derivative bounds.

These lessons are now reflected in the normative [V13 Mountain Generation](../../systems/world-generation/mountain-generation.md) page rather than being encoded as downstream visual repairs.

## Automated evidence paired with the manual review

The accepted implementation is covered at the owning boundaries by:

- `MountainArchitectureTest`;
- `MountainAbundanceCoverageTest`;
- `MountainMorphologyElevationGenerationTest`;
- `V13MountainTerrainGeneratorCompositionTest`;
- `V13SparseShapeGenerationTest`;
- preview/settings tests;
- repository CI;
- Generated World Audit.

The final documentation closeout additionally requires the Docs Site workflow to remain green.

## Acceptance boundary

This acceptance applies to **Stage 1 mountain morphology and its generic V13 surface-shape presentation**. It does not accept future river/lake carving, geology, caves, final surface materials, snow/altitude effects or navigation connectivity. Those remain owned by later stages.

Stage 2 must treat the accepted V13 `ElevationField` as its dry-morphology input and must not reopen Stage 1 behavior incidentally.

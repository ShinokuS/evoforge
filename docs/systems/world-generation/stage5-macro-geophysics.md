# Stage 5 — Macro Ocean + Geophysical Skeleton

## Status

**Complete and manually accepted in PR #135. Stage 6 is the next allowed checkpoint and has not started.**

## Goal

Stage 5 creates the first real large-scale geography in the Continuum line.

For fixed seed, model revision, authored macro-geophysics definition and coordinates, the world exposes one continuous macro-geophysical elevation fact. A shared sea datum divides that same fact into ocean and land. Large ocean basins, continental-scale support and broad geophysical structure therefore have one causal source instead of being painted independently.

This is a configurable macro skeleton, not final terrain height.

## Semantic ownership

The independent world concept is:

```text
world/geophysics
```

It owns the meaning, authored macro-world character and replaceable algorithm for the macro-geophysical skeleton.

It does **not** belong to `world/continuum`: Continuum remains neutral addressing, bounded materialization, multi-resolution sampling and map infrastructure.

It also does **not** extend the existing `world/geology` vocabulary. Current Geology code represents authored geological profile/unit/material semantics. Macro crustal support can exist and be queried without those authored profiles, so it is a separate concept under ADR-026's consumer-independence rule.

Dependency direction remains one-way:

```text
Geophysics -> Continuum scalar contract
Continuum  -X-> Geophysics
```

The adapter is read-only. Map/cache/camera state never feeds back into geophysical truth.

## Authoritative contract

`MacroGeophysicalField` exposes signed dimensionless macro elevation in `[-1, 1]`.

The sea datum is zero at this stage:

```text
elevation < 0  -> ocean
elevation >= 0 -> land
```

Ocean is therefore a derived classification of the same macro elevation fact. There is no separate `oceanNoise`, coastline painter or ocean raster.

The value is intentionally dimensionless. Converting this macro support into continuous evolved surface height belongs to Stage 6 and later refinement. Converting that surface to exact integer XYZ material belongs to Stage 10.

## Authored macro-world controls

`MacroGeophysicsDefinition` is the stable semantic input to Stage 5. It exposes normalized `0..1` controls whose meaning survives replacement of the current algorithm:

- `oceanPrevalence` — tendency toward more ocean versus more exposed land;
- `continentalScale` — characteristic scale of the broadest land/ocean structures;
- `landmassCohesion` — resistance of broad land/ocean support to being broken near the sea datum;
- `fragmentation` — tendency for coherent regional structures to split broad masses into island groups, straits and separated regions;
- `macroVariation` — strength of large regional variation/deformation.

These are authored world semantics. They are intentionally different from solver details such as lattice spans, hash salts, interpolation equations and blend coefficients, which remain private implementation policy.

`MacroGeophysicsPreset` supplies convenience profiles (`SUPERCONTINENT`, `BALANCED`, `ARCHIPELAGO`, `OCEANIC`) over the same definition contract. Presets are not a second configuration system: arbitrary custom definitions remain valid.

`ARCHIPELAGO` and `OCEANIC` intentionally describe different world character. Archipelago uses high fragmentation to produce many coherent island groups and chains. Oceanic uses very high ocean prevalence with low fragmentation, producing large open-ocean regions and comparatively few isolated landmasses.

No setting promises an exact global topology such as "exactly four continents". Such a guarantee would require whole-world connectivity analysis and would conflict with the current local addressable generation model. Stage 5 controls world character rather than globally optimizing a finished raster.

## Current deterministic model

The current implementation is hidden behind `MacroGeophysics.create(...) -> MacroGeophysicalField` and may be replaced without changing consumers.

The implementation is deliberately low-frequency and separates macro shape from later terrain detail:

1. a broad gradient field establishes continent/ocean-basin scale support;
2. two progressively smaller, still macro-scale gradient octaves break simple blob symmetry without introducing fine coastline noise;
3. very broad deterministic domain warping bends those structures so the hidden sampling lattice does not become visible geography;
4. landmass cohesion stabilizes broad interiors away from the sea datum;
5. regional support is strongest only in a broad coastal transition band, so fragmentation can create bays, straits and islands without perforating deep continental interiors or deep ocean basins;
6. at high fragmentation, narrow zero-crossing ridges of a separate regional field may be lifted into coherent island chains/arcs inside that same transition band;
7. ocean prevalence shifts the one shared result relative to the fixed sea datum;
8. sea datum derives ocean versus land.

A high fragmentation value is **not permission for high-frequency noise**. Every structural layer has a macro-scale lower bound, and regional influence is spatially constrained to the coastal transition. An archipelago profile should read as groups of substantial islands and channels, not as sample-scale speckle or checkerboard perforation.

This model aims for plausible large-scale geography, not final geomorphology. Detailed coastlines, erosion, mountain systems, drainage and surface evolution remain later causal stages; they must refine this macro skeleton rather than reveal hidden high-frequency Stage 5 noise.

## Continuum reuse

Stage 5 reuses the accepted Continuum foundation instead of creating another world representation:

- `ContinuumScalarField` is the neutral coordinate-addressed scalar view;
- `ContinuumMaterializer` materializes only requested bounded windows;
- multi-resolution pages sample the same field directly at their requested lattice;
- `ContinuumScalarMapTileGenerator` reads the same field for the map;
- existing bounded async map/cache/parent-fallback behavior remains presentation infrastructure only.

`MacroGeophysicalContinuumField` is the narrow read-only adapter from signed macro elevation to the normalized `[0, 1]` scalar range expected by the existing map pipeline.

No global Stage 5 raster is stored.

## Determinism and seams

Required invariant:

```text
same seed + model revision + definition + coordinates = same macro elevation
```

independent of:

- query order;
- unrelated queries;
- page/window boundaries;
- resolution level at shared coordinates;
- cache residency/eviction;
- camera position;
- thread scheduling in map preparation.

The field is evaluated directly from coordinates. Page and map boundaries therefore cannot be physical boundaries of the geography.

Both horizontal and vertical overlapping-window tests prove the world field itself is continuous across representation boundaries. The map presentation must preserve the same row/column orientation when converting a tile to a GPU texture; a tile-local flip is a presentation bug, not geography.

The archipelago quality regression compares coastline transition density across observation resolutions. Refining the observation lattice must expose coherent boundaries rather than reveal a hidden checkerboard of tiny alternating land/ocean samples.

A second profile regression requires Archipelago and Oceanic to remain structurally distinct at the same seed: Archipelago must expose substantially more macro coastline while Oceanic remains more strongly dominated by open ocean.

## Boundedness and scale

One sample performs a fixed amount of local mathematical work. It does not enumerate continents, pages or the logical world.

The Stage 5 scale profile materializes the same `128 x 128` requested sample window in logical worlds with sides:

- `16,000,000`;
- `1,000,000,000`;
- `1,000,000,000,000`.

Each case must perform exactly `16,384` field samples. Logical world area is therefore not part of the work count.

Timings are diagnostic evidence with a deliberately generous safety gate; the architectural invariant is the constant requested-work count.

## F2 manual inspection and settings UI

The existing Continuum map viewer renders the real Stage 5 field. The standard inspection domain is `16,000,000 x 16,000,000` logical units so `Home` shows several macro-scale structures.

A dedicated **WORLD GENERATION** panel is separate from the technical map HUD. It contains:

- the active profile name;
- an editable world seed field with explicit `Apply` action;
- a `Random seed` action which chooses and immediately applies a new seed;
- buttons for Supercontinent, Balanced, Archipelago and Oceanic presets;
- sliders for all five `MacroGeophysicsDefinition` controls;
- an explicit `Apply custom` action.

Decimal signed `long` seeds and `0x...` hexadecimal seeds are accepted. The selected seed is always displayed, so a randomly generated world can be reproduced exactly later.

Preset buttons apply immediately. Moving sliders only edits the pending custom values; the world source is regenerated when `Apply custom` is pressed, avoiding a burst of expensive tile regeneration while a slider is being dragged.

Changing either the definition or the seed invalidates the derived map source and GPU textures while preserving the current map center and zoom. Changing definition does not change world identity; changing seed changes world identity while preserving the active profile/settings. Neither operation turns camera state into generation input.

Keyboard shortcuts `1` through `4` remain optional fast preset selection.

Map controls:

```text
Left mouse drag  pan
Mouse wheel      zoom around cursor
Home             whole logical world
1..4             quick preset selection
G                tile/cache diagnostics
Esc              back
```

Visual meaning:

- blue = ocean-side macro elevation;
- green through brown = land-side macro elevation;
- orange tile border with diagnostics = temporary coarser parent fallback;
- green tile border with diagnostics = requested map detail is ready.

The colors and the settings panel are presentation only. They expose/select authored inputs but never derive authoritative geography from the camera or screen.

With diagnostics hidden and `temporary coarse = 0`, tile boundaries must be visually indistinguishable from any other sample boundary. Large straight horizontal/vertical discontinuities are a failed visual check even when the underlying field tests are green.

## Explicit Stage 5 boundary

Stage 5 does **not** implement:

- continuous surface evolution or erosion;
- detailed mountain/valley morphology;
- drainage topology;
- rivers;
- lakes;
- climate;
- sediment transport;
- soil;
- exact XYZ terrain materialization;
- runtime mutable surface processes.

Those remain in their fixed later stages.

## Acceptance result

All Stage 5 gates passed on the accepted PR head:

- focused geophysics correctness/determinism tests;
- semantic world-character controls and profile distinction tests;
- Archipelago/Oceanic separation and anti-noise regression;
- shared-coordinate multi-resolution and horizontal/vertical overlap/seam tests;
- map texture orientation regression;
- seed/custom-definition inspection workflow;
- architecture fitness and ArchUnit;
- full Gradle tests and JaCoCo coverage;
- Continuum scale profile;
- Docs Site.

The user manually inspected the F2 macro map across profiles, custom settings and multiple seeds and explicitly accepted the resulting Stage 5 geography.

**Stage 5 is complete. Stage 6 may begin; Stage 7 remains blocked until Stage 6 is separately accepted.**

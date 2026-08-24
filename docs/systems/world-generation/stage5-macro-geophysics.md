# Stage 5 — Macro Ocean + Geophysical Skeleton

## Status

**Implementation in progress in PR #135. Manual acceptance is still required. Stage 6 has not started.**

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

No setting promises an exact global topology such as "exactly four continents". Such a guarantee would require whole-world connectivity analysis and would conflict with the current local addressable generation model. Stage 5 controls world character rather than globally optimizing a finished raster.

## Current deterministic model

The current implementation is hidden behind `MacroGeophysics.create(...) -> MacroGeophysicalField` and may be replaced without changing consumers.

It evaluates two nested spatial scales of one crustal-support process:

1. broad continental support establishes continent/ocean-basin sized tendencies;
2. a regional **macro** support field bends that broad support into provinces and island groups;
3. landmass cohesion stabilizes broad support away from the sea datum;
4. fragmentation controls how strongly coherent regional support may interrupt the broad support and, within a bounded range, how much smaller the regional structural scale is;
5. macro variation controls bounded signed deformation between the two support scales;
6. ocean prevalence shifts the shared result relative to the fixed sea datum;
7. sea datum derives ocean versus land.

A high fragmentation value is **not permission for high-frequency noise**. The regional structural span has a macro-scale lower bound and its influence is bounded. An archipelago profile should read as groups of substantial islands and channels, not as sample-scale speckle or checkerboard perforation.

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

The archipelago quality regression also compares coastline transition density across observation resolutions. Refining the observation lattice must expose coherent boundaries rather than reveal a hidden checkerboard of tiny alternating land/ocean samples.

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
- buttons for Supercontinent, Balanced, Archipelago and Oceanic presets;
- sliders for all five `MacroGeophysicsDefinition` controls;
- an explicit `Apply custom` action.

Preset buttons apply immediately. Moving sliders only edits the pending custom values; the world source is regenerated when `Apply custom` is pressed, avoiding a burst of expensive tile regeneration while a slider is being dragged.

Changing the definition invalidates the derived map source and GPU textures but preserves the current map center and zoom. It does not change the world seed or turn camera state into generation input.

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

## Acceptance gate

Stage 5 may be marked complete only after all of the following are true on the final PR head:

- focused geophysics correctness/determinism tests pass;
- authored controls produce distinct deterministic world character without exposing solver knobs;
- the archipelago profile remains macro-structured rather than sample-scale noisy;
- shared-coordinate multi-resolution and horizontal/vertical overlap/seam tests pass;
- map texture conversion preserves tile row/column orientation;
- the dedicated settings panel can select presets and apply arbitrary custom definitions without resetting map navigation;
- architecture fitness and ArchUnit remain green without weakened rules;
- full Gradle tests and JaCoCo coverage gate pass;
- Continuum scale profile including the Stage 5 workload passes;
- Docs Site builds;
- the user manually inspects the F2 macro map across contrasting/custom settings and accepts the geography behavior.

Until that explicit manual acceptance, Stage 5 remains in progress and Stage 6 must not begin.

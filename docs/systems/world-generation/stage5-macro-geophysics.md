# Stage 5 — Macro Ocean + Geophysical Skeleton

## Status

**Implementation in progress in PR #135. Manual acceptance is still required. Stage 6 has not started.**

## Goal

Stage 5 creates the first real large-scale geography in the Continuum line.

For fixed seed, model revision and coordinates, the world exposes one continuous macro-geophysical elevation fact. A shared sea datum divides that same fact into ocean and land. Large ocean basins, continental-scale support and broad geophysical structure therefore have one causal source instead of being painted independently.

This is a macro skeleton, not final terrain height.

## Semantic ownership

The new independent world concept is:

```text
world/geophysics
```

It owns the meaning and algorithm for the macro-geophysical skeleton.

It does **not** belong to `world/continuum`: Continuum remains neutral addressing, bounded materialization, multi-resolution sampling and map infrastructure.

It also does **not** extend the existing `world/geology` vocabulary. Current Geology code represents authored geological profile/unit/material semantics. Macro crustal support can exist and be queried without those authored profiles, so it is a separate concept under ADR-026's consumer-independence rule.

Dependency direction is deliberately one-way:

```text
Geophysics -> Continuum scalar contract
Continuum  -X-> Geophysics
```

The adapter is read-only. Map/cache/camera state never feeds back into geophysical truth.

## Authoritative contract

`MacroGeophysicalField` exposes signed dimensionless macro elevation in `[-1, 1]`:

```text
elevationAt(seed, revision, x, y)
```

The sea datum is zero at this stage:

```text
elevation < 0  -> ocean
elevation >= 0 -> land
```

Ocean is therefore a derived classification of the same macro elevation fact. There is no separate `oceanNoise`, coastline painter or ocean raster.

The value is intentionally dimensionless. Converting this macro support into continuous evolved surface height belongs to Stage 6 and later refinement. Converting that surface to exact integer XYZ material belongs to Stage 10.

## Current deterministic model

`DeterministicMacroGeophysicalField` is a replaceable Stage 5 algorithm, not a permanent claim that the world uses literal plate tectonics.

The implementation evaluates two nested spatial scales of one **crustal-support process**:

1. broad continental support establishes continent/ocean-basin sized tendencies;
2. regional province support perturbs the same underlying support at a smaller macro scale;
3. disagreement between the two scales contributes a bounded deformation term;
4. the combined support produces signed macro elevation;
5. sea datum derives ocean versus land.

Internal spans, weights, salts and interpolation constants are algorithm tuning. They are intentionally not authored Definitions.

This gives later stages a coherent replaceable cause without pretending to simulate full plate tectonics before the project needs it.

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

Required invariants:

```text
same seed + revision + coordinates = same macro elevation
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

## Boundedness and scale

One sample performs a fixed amount of local mathematical work. It does not enumerate continents, pages or the logical world.

The Stage 5 scale profile materializes the same `128 x 128` requested sample window in logical worlds with sides:

- `16,000,000`;
- `1,000,000,000`;
- `1,000,000,000,000`.

Each case must perform exactly `16,384` field samples. Logical world area is therefore not part of the work count.

Timings are recorded as diagnostic evidence with a deliberately generous safety gate; the architectural invariant is the constant requested-work count.

## F2 manual inspection

The existing Continuum map viewer now renders the Stage 5 field instead of the Stage 4 synthetic sine/cosine diagnostic field.

The standard inspection domain is `16,000,000 x 16,000,000` logical units so `Home` shows several macro-scale structures.

```text
Left mouse drag  pan
Mouse wheel      zoom around cursor
Home             whole logical world
G                tile/cache diagnostics
Esc              back
```

Visual meaning:

- blue = ocean-side macro elevation;
- green through brown = land-side macro elevation;
- orange tile border with diagnostics = temporary coarser parent fallback;
- green tile border with diagnostics = requested map detail is ready.

The colors are a presentation palette only. They do not create ocean or land.

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
- shared-coordinate multi-resolution and overlap/seam tests pass;
- architecture fitness and ArchUnit remain green without weakened rules;
- full Gradle tests and JaCoCo coverage gate pass;
- Continuum scale profile including the Stage 5 workload passes;
- Docs Site builds;
- the user manually inspects the F2 macro map and accepts the geography behavior.

Until that explicit manual acceptance, Stage 5 remains in progress and Stage 6 must not begin.

# Stage 5 — Macro Ocean + Geophysical Skeleton

## Status

**Implementation in progress in PR #135. Manual acceptance is still required. Stage 6 has not started.**

## Goal

Stage 5 creates the first real large-scale geography in the Continuum line.

For fixed seed, model revision, macro-geophysics definition and coordinates, the world exposes one continuous macro-geophysical elevation fact. A shared sea datum divides that same fact into ocean and land. Large ocean basins, continental-scale support and broad geophysical structure therefore have one causal source instead of being painted independently.

This is a macro skeleton, not final terrain height.

## Semantic ownership

The independent world concept is:

```text
world/geophysics
```

It owns the meaning and replaceable algorithm for the macro-geophysical skeleton.

It does **not** belong to `world/continuum`: Continuum remains neutral addressing, bounded materialization, multi-resolution sampling and map infrastructure.

It also does **not** extend the existing `world/geology` vocabulary. Current Geology code represents authored geological profile/unit/material semantics. Macro crustal support can exist and be queried without those authored profiles, so it is a separate concept under ADR-026's consumer-independence rule.

Dependency direction is deliberately one-way:

```text
Geophysics -> Continuum scalar contract
Continuum  -X-> Geophysics
```

The adapter is read-only. Map/cache/camera state never feeds back into geophysical truth.

## Authoritative contract

`MacroGeophysicalField` exposes signed dimensionless macro elevation in `[-1, 1]`.

For fixed authoritative Genesis inputs:

```text
macroElevation = f(seed, modelRevision, MacroGeophysicsDefinition, x, y)
```

The sea datum is zero at this stage:

```text
elevation < 0  -> ocean
elevation >= 0 -> land
```

Ocean is therefore a derived classification of the same macro elevation fact. There is no separate `oceanNoise`, coastline painter or ocean raster.

The value is intentionally dimensionless. Converting this macro support into continuous evolved surface height belongs to Stage 6 and later refinement. Converting that surface to exact integer XYZ material belongs to Stage 10.

## Authored macro-geography controls

Stage 5 must not hard-code one accidental world character as the only valid geography. The public `MacroGeophysicsDefinition` therefore exposes five normalized semantic controls:

| Control | Meaning |
|---|---|
| `oceanPrevalence` | tendency toward more ocean versus more exposed land |
| `continentalScale` | characteristic scale of the largest continental/ocean-basin structures |
| `landmassCohesion` | tendency for broad land support to remain spatially coherent |
| `fragmentation` | how readily regional structure breaks broad land into islands, straits and separated masses |
| `macroVariation` | strength of regional macro-geophysical variation/deformation |

All controls use authored `NormalizedValue` values in `0..1`.

These are semantic controls, not direct solver knobs. The public definition deliberately does **not** expose lattice spans, interpolation exponents, salts, blend weights, thresholds or internal deformation coefficients.

### Ocean prevalence is a tendency, not an exact percentage

`oceanPrevalence = 0.70` does not promise that exactly 70% of every finite inspection rectangle is ocean. An exact area quota would require global topology/area analysis and would undermine local coordinate-addressed generation.

Instead the control monotonically shifts the shared elevation field relative to the fixed sea datum. Higher values therefore make ocean more prevalent for otherwise identical inputs without introducing a second ocean authority.

### No exact continent-count setting yet

Stage 5 intentionally does not expose `continentCount = N`.

An exact connected-component count is a global topological constraint rather than a local semantic tendency. If EvoForge later needs exact continent-count authoring, it requires an explicit design that preserves determinism, bounded work and Continuum locality rather than a hidden whole-world repair pass.

## Presets are conveniences, not the contract

The visualizer and quick setup provide deliberately contrasting presets over the same `MacroGeophysicsDefinition`:

```text
SUPERCONTINENT
BALANCED
ARCHIPELAGO
OCEANIC
```

They exist to make the supported behavioral range obvious and manually inspectable. A custom definition is equally valid and does not require adding another preset or changing the algorithm API.

`SUPERCONTINENT` means a strong tendency toward one dominant coherent macro landmass; it is not an exact connected-component guarantee. `ARCHIPELAGO` deliberately increases regional fragmentation. `OCEANIC` shifts the same field toward ocean-dominated geography.

## Replaceable deterministic model

`MacroGeophysics.create(...)` is the public creation boundary. Presentation and other consumers depend on `MacroGeophysicalField` and semantic definitions rather than constructing the current concrete algorithm directly.

The current hidden deterministic implementation evaluates two nested spatial scales of one **crustal-support process**:

1. authored continental scale selects the characteristic broad support scale;
2. broad support establishes continent/ocean-basin sized tendencies;
3. landmass cohesion stabilizes broad support around its existing sign without painting features;
4. regional province support perturbs that same underlying support at a smaller macro scale;
5. fragmentation controls how strongly regional support can interrupt broad support;
6. macro variation controls bounded signed regional deformation;
7. ocean prevalence shifts the resulting shared field relative to the fixed sea datum;
8. sea datum derives ocean versus land.

Internal spans, weights, salts, interpolation functions and coefficients are algorithm tuning. They are intentionally not authored Definitions.

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
same seed + model revision + macro definition + coordinates = same macro elevation
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

Both horizontal and vertical overlapping-window regression tests enforce the world-field seam invariant. Presentation also preserves raster row orientation and clamps texture edges so rendering cannot manufacture false geographic seams.

## Boundedness and scale

One sample performs a fixed amount of local mathematical work. It does not enumerate continents, pages or the logical world.

The Stage 5 scale profile materializes the same `128 x 128` requested sample window in logical worlds with sides:

- `16,000,000`;
- `1,000,000,000`;
- `1,000,000,000,000`.

Each case must perform exactly `16,384` field samples. Logical world area is therefore not part of the work count.

Timings are recorded as diagnostic evidence with a deliberately generous safety gate; the architectural invariant is the constant requested-work count.

## F2 manual inspection

The existing Continuum map viewer renders the Stage 5 field instead of the Stage 4 synthetic sine/cosine diagnostic field.

The standard inspection domain is `16,000,000 x 16,000,000` logical units so `Home` shows several macro-scale structures.

```text
Left mouse drag  pan
Mouse wheel      zoom around cursor
Home             whole logical world
1                SUPERCONTINENT profile
2                BALANCED profile
3                ARCHIPELAGO profile
4                OCEANIC profile
G                tile/cache diagnostics
Esc              back
```

Switching profiles rebuilds only the derived inspection pipeline around the same fixed seed/model revision and clears presentation textures. Profile selection is an inspection/Genesis-input choice; camera state still never changes geophysical truth.

The overlay displays the selected profile plus all five normalized semantic controls so visual differences are attributable to explicit authored intent.

Visual meaning:

- blue = ocean-side macro elevation;
- green through brown = land-side macro elevation;
- orange tile border with diagnostics = temporary coarser parent fallback;
- green tile border with diagnostics = requested map detail is ready.

The colors are a presentation palette only. They do not create ocean or land.

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
- semantic macro settings demonstrably change the intended world character;
- shared-coordinate multi-resolution and horizontal/vertical overlap seam tests pass;
- map texture conversion preserves tile row/column orientation;
- architecture fitness and ArchUnit remain green without weakened rules;
- full Gradle tests and JaCoCo coverage gate pass;
- Continuum scale profile including the Stage 5 workload passes;
- Docs Site builds;
- the user manually inspects contrasting F2 profiles and accepts the macro-geography behavior.

Until that explicit manual acceptance, Stage 5 remains in progress and Stage 6 must not begin.

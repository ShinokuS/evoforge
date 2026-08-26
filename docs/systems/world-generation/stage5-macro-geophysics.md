# Stage 5 — Macro Ocean + Geophysical Skeleton

## Status

**Original Stage 5 macro elevation was complete and manually accepted in PR #135.**

A separate Stage 5 follow-up prepares later structure-first terrain generation by adding a deterministic structural geophysical read capability while preserving the accepted macro-elevation output.

Stage 6 terrain, drainage, rivers/lakes and runtime physics remain out of scope for this follow-up.

## Goal

Stage 5 creates the first real world-scale geophysical cause.

For fixed seed, model revision, authored macro-geophysics definition and coordinates, the world exposes:

1. the already-accepted signed macro elevation used for broad land/ocean support;
2. a new macro structural context describing broad continental support, margin influence and the local relationship of stable structural regions.

These are geophysical causes, not final Terrain height.

## Semantic ownership

The independent concept remains:

```text
world/geophysics
```

Continuum remains neutral addressing/materialization/cache/map infrastructure and does not own natural geography.

Dependency direction remains one-way:

```text
Geophysics -> neutral Continuum adapters where needed
Continuum  -X-> Geophysics
```

Camera/cache/render state never feeds back into geophysical truth.

## Public contracts

### Accepted macro elevation

`MacroGeophysicalField` remains the narrow elevation capability:

```text
double elevationAt(long x, long y)
```

The value is signed and dimensionless in `[-1, 1]` around the shared sea datum `0`:

```text
elevation < 0  -> macro ocean side
elevation >= 0 -> macro land side
```

This remains unchanged by the structural-preparation follow-up.

### Structural geophysical context

`MacroGeophysicalStructureField` adds:

```text
MacroGeophysicalStructure structureAt(long x, long y)
```

`MacroGeophysicalModel` combines both read capabilities and is returned by `MacroGeophysics.create(...)`.

The structural sample contains:

- `continentalSupport` — broad stabilized continental/deep-ocean support before later Terrain shaping;
- `marginInfluence` — how strongly the coordinate lies in the macro continental-margin transition;
- `primaryRegion` / `secondaryRegion` — stable opaque identities of the nearest two natural macro structural regions;
- `boundaryInfluence` — proximity/influence of their shared structural boundary;
- `boundaryRegime` — `INTERIOR`, `CONVERGENT`, `DIVERGENT` or `TRANSFORM` based on deterministic relative macro motion;
- `boundaryStrength` — normalized strength of the active relative-motion component;
- `boundaryNormalX/Y` — unit normal from the primary toward the secondary structural region.

These values are consumer-neutral geophysical facts. They do not mention Terrain mountains, rivers, rendering or another current consumer.

## Why this preparation exists

The accepted Stage 5 API originally handed Stage 6 only one scalar macro elevation. That is enough to classify broad land/ocean tendency, but not enough to answer structure-first questions such as:

- is this coordinate deep continental interior or near a macro margin?
- are neighboring macro structural regions moving toward or away from one another?
- what orientation does their shared boundary have?
- can two independently requested areas recognize that they are observing the same structural region/boundary?

Without such cause, later terrain tends to invent mountain systems from unrelated noise. The structural capability exists specifically to prevent that architectural gap.

## Structural reconstruction model

The current hidden implementation uses locally reconstructable jittered macro structural sites.

For one query:

1. coordinates are warped by the same very broad accepted Stage 5 deformation used by the macro model;
2. a fixed `5 x 5` neighborhood of candidate structural sites is reconstructed from seed/revision/definition and macro lattice address;
3. the nearest and second-nearest sites identify the local natural structural regions;
4. the distance difference determines bounded shared-boundary influence;
5. each region has a deterministic broad motion vector;
6. relative motion decomposed along/across the shared boundary determines convergent/divergent/transform character.

The exact site spacing, jitter, salts, thresholds and motion equations are replaceable solver policy. They are not authored Definition fields.

No full-world plate/region raster or adjacency graph is allocated. One structural sample performs fixed local work independent of logical world area.

The structural regions are **causes**, not final visible polygon geography. Stage 6 may derive/refine natural mountain/coast/basin structures from the context without drawing the hidden reconstruction lattice directly.

## Authored macro-world controls

`MacroGeophysicsDefinition` remains unchanged and continues to expose:

- `oceanPrevalence`;
- `continentalScale`;
- `landmassCohesion`;
- `fragmentation`;
- `macroVariation`.

The follow-up does not add solver-specific public sliders. Existing settings also influence hidden structural scale/motion where their semantic meaning already applies.

`MacroGeophysicsPreset` remains only a convenience layer over the same definition contract.

## Accepted macro-elevation algorithm remains unchanged

The PR #135 macro-elevation implementation remains the same:

1. broad gradient support establishes continent/ocean-basin scale tendency;
2. secondary/tertiary still-macro fields break simple symmetry;
3. very broad deterministic warp bends those structures;
4. landmass cohesion stabilizes broad interiors;
5. regional influence is concentrated around the macro margin;
6. high fragmentation may create coherent macro island-chain support;
7. ocean prevalence biases the same shared result around sea datum zero.

The preparation PR does not use structural boundaries to change that accepted elevation. This isolates the new contract from the previously accepted visual result.

## Determinism and invariants

Macro elevation retains the accepted invariant:

```text
same seed + revision + definition + coordinates
= same elevation
```

Structural context adds:

```text
same seed + revision + definition + coordinates
= same structural sample / region identities / boundary regime
```

Both are independent of:

- query order;
- unrelated queries;
- cache residency/eviction;
- Continuum page/window boundaries;
- camera position;
- rendering;
- thread scheduling.

Public structural invariants include:

- `continentalSupport` in `[-1,1]`;
- influences/strength in `[0,1]`;
- primary/secondary region ids differ;
- boundary normal is unit length;
- `INTERIOR` is used only below the active boundary-influence threshold;
- active boundary regimes are used only where boundary influence is meaningful.

## Boundedness and scale

`elevationAt` keeps the accepted fixed local work and scale profile from PR #135.

`structureAt` reconstructs exactly a fixed local candidate neighborhood. Its cost therefore does not grow with total logical world area and does not allocate a world-sized region graph.

Later consumers may cache derived structures through Continuum or owner-local projections, but cache/page identity never becomes structural-region identity.

## F2 inspection

The accepted Stage 5 map remains a view of macro elevation. The structural preparation is intentionally not allowed to alter its colors/coastline as proof that elevation compatibility was preserved.

A diagnostic structural overlay may be added as presentation-only evidence where useful, but it must be off by default and must not feed camera state back into geophysical truth.

## Explicit Stage 5 boundary

Stage 5 still does **not** implement:

- continuous Terrain surface evolution;
- mountain belts/ridges;
- plateaus/basins as Terrain structures;
- detailed coastline evolution;
- drainage topology;
- river channels;
- lakes;
- Genesis erosion/valley shaping;
- runtime erosion/landslides;
- climate;
- sediment/soil;
- exact XYZ Terrain materialization.

Those remain later stages/owners.

## Follow-up acceptance requirements

The Stage 5 structural-preparation PR is complete when:

- all original PR #135 macro-elevation regression values remain unchanged;
- original profile/coast/scale tests remain green;
- structural samples are deterministic and query-order independent;
- seed/revision participate in structural identity;
- representative sampling exposes stable interiors and multiple active boundary regimes;
- structural ranges/unit normals/regime semantics are property-tested;
- architecture/JaCoCo/Docs/scale gates remain green;
- canonical roadmap/ADR identifies Stage 6 as the first Terrain consumer of this new cause.

After this preparation is accepted, Stage 6 may use the richer geophysical context without reopening Stage 5 macro elevation semantics.

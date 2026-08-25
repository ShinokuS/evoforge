# Stage 6 — Continuous Surface Evolution Prototype

## Status

**In progress on the Stage 6 feature branch. Not accepted. Stage 7 remains blocked.**

This page is the canonical Stage 6 contract for the current prototype. The implementation may be revised while Stage 6 is unaccepted, but the semantic ownership, determinism, bounded-work and multi-resolution laws below remain mandatory.

## Goal

Stage 6 turns the accepted Stage 5 `MacroGeophysicalField` into one deterministic, coordinate-addressable **continuous Terrain surface** with a readable hierarchy of coast, lowlands, uplands, plateaus, mountain systems and subordinate relief.

Conceptually:

```text
MacroGeophysicalField
        ↓
ContinuousTerrainSurface
        ↓
Stage 7 drainage topology
        ↓
...
        ↓
Stage 10 exact integer XYZ Terrain
```

Stage 5 remains the world-scale geophysical support. Stage 6 develops that support into continuous Z geometry and may refine the local sea crossing inside a bounded coastal band; it does not create an unrelated second world map.

Finer observation must reveal additional deterministic structure in the same already-defined surface rather than camera-created detail.

## Semantic ownership

Stage 6 belongs to:

```text
world/terrain
```

The reusable concept is the Terrain surface itself. It can be consumed by drainage, hydrology, later refinement, exact XYZ materialization and presentation without naming any one consumer.

Ownership and dependency direction are:

```text
world/geophysics
        ↓
world/terrain continuous surface
        ↓
consumer-neutral Continuum adapter
        ↓
Continuum materialization/map infrastructure
```

`world/continuum` remains technical addressing/materialization/cache/LOD infrastructure and does not own Terrain truth.

The runtime `TerrainSystem` remains the authority for exact mutable XYZ Terrain presence/material identity. The integer `TerrainSurfaceLookup` remains a projection of materialized runtime columns. Stage 6 does not write runtime Terrain cells and does not turn that projection into the Genesis source.

Until Stage 10, `ContinuousTerrainSurface` is the canonical generated surface geometry.

## Authoritative continuous contract

The Stage 6 source contract is:

```text
double surfaceZAt(long x, long y)
```

The returned value is continuous Z in logical world-cell coordinates. The shared sea datum is `0.0`:

```text
surfaceZ < 0  -> submerged surface
surfaceZ >= 0 -> exposed surface
```

Submergence is derived from the one surface rather than from an independent ocean mask.

The contract must satisfy:

```text
same seed
+ same geophysics revision
+ same surface revision
+ same macro definition
+ same surface definition
+ same XY
= same continuous Z
```

The result must not depend on:

- camera position or zoom;
- map tile/page/window identity;
- requested LOD except for which coordinates are sampled;
- query order;
- unrelated queries;
- cache residency/eviction/rematerialization;
- thread scheduling in presentation preparation.

Shared coordinates sampled through different Continuum resolutions must therefore return identical source values.

## Stage 6 authored settings

Stage 6 exposes four normalized semantic controls:

- `reliefIntensity` — overall amplitude of Stage 6 relief;
- `regionalRuggedness` — tendency for mountain/upland regions to express stronger relief contrasts and a greater density of active mountain systems;
- `plateauTendency` — tendency for elevated provinces to form broad high surfaces;
- `regionalReliefScale` — characteristic horizontal scale of regional provinces and mountain systems.

These are world semantics, not solver knobs.

The public Definition must not expose implementation details such as octave counts, salts, lattice sizes, blend weights, peak counts, cache/tile sizes or numerical tuning constants.

Changing Stage 6 settings does not change world seed. Changing seed preserves selected Stage 5 and Stage 6 settings.

## Current causal surface model — revision 3

The current bounded model is deliberately not a generic `noise -> heightmap` stack and not a line painter.

### 1. Macro vertical support

Stage 5 signed macro elevation establishes continental and ocean-basin support. Its broad world character remains recognizable at the largest scale.

### 2. Bounded coastal refinement

Stage 6 does **not** freeze the exact Stage-5 zero contour. Doing so produced unnaturally smooth coastlines that could not gain real detail at finer observation.

Instead, only a narrow band around the Stage-5 sea crossing receives additional multi-scale displacement:

```text
broad coast structure
    + medium coast structure
    + fine coast structure
    + micro coast structure
```

The coastal influence fades rapidly with distance from the macro sea crossing. This allows bays, capes, coves and small near-shore islands without allowing local detail to carve arbitrary inland seas or grow very long artificial appendages from deep continental/ocean support.

Deep continental support remains land and deep ocean support remains submerged.

### 3. Broad regional provinces

Independent low-frequency provinces organize ordinary continental interiors into coherent:

- lowlands;
- rolling uplands;
- plateau/highland regions.

These provinces are independent from mountain placement so continents do not become flat planes with isolated decorative ridges.

### 4. Finite mountain systems made from massifs

Mountain systems are **areas**, not zero-crossing contour bands and not single bent centre-lines.

Each active deterministic regional system contains several overlapping two-dimensional massifs with unequal:

- centres;
- longitudinal and transverse radii;
- strengths;
- offsets along the system;
- lateral offsets;
- curvature response.

Some massifs add one short oblique lobe, creating compact side ranges and forks. Because every system is finite and only a fixed neighbourhood can influence a query, a range naturally has beginnings, endings and gaps rather than becoming a world-spanning stripe.

At world scale the overlapping shoulders read as one mountain region or chain. At closer scale the unequal massifs separate into individual high areas.

### 5. Subordinate ridges and local peaks

Within the finite mountain envelope, smaller deterministic fields add:

- secondary ridge structure;
- fine ridge structure;
- local peak bumps.

Ridged structure is therefore **masked by an already finite mountain region**. It is never used globally as the mountain-placement rule, avoiding the worm-like contour networks rejected during Stage 6 iteration.

### 6. Nested ordinary relief

Non-mountain interiors receive progressively smaller continuous rolling/fine/micro/nano relief. The current smallest causal span is still far above a one-cell feature and is continuous rather than sample noise.

Plateau tendency attenuates the small layers without turning plateaus into perfectly flat mathematical shelves.

### 7. Ocean-floor relief

Submerged terrain receives broad bathymetric structure as part of the same surface. It is not a second ocean system.

## Realism requirements

A plausible Stage 6 surface must show a readable hierarchy:

```text
continent / ocean basin
    ↓
coast + regional province
    ↓
lowland / plateau / finite mountain system
    ↓
massif / secondary ridge / local peak
    ↓
small continuous relief
```

Required qualitative properties:

- coastlines contain several scales of shape rather than one overly smooth spline-like boundary;
- coastal detail remains local and does not produce narrow kilometer-scale-looking appendages from deep support;
- mountain systems are finite two-dimensional regions, not stripes drawn over the map;
- individual systems contain multiple massifs and can have short branches, endings and gaps;
- mountains are spatially clustered rather than uniformly salted across all land;
- plateaus are broad regions rather than noisy collections of peaks;
- lowlands/depressions are coherent regions;
- smaller relief is modulated by larger relief;
- zooming reveals additional causal structure instead of only magnifying interpolation;
- no layer introduces checkerboard, stipple, isolated single-cell extrema or alternating Z noise.

## Minimum feature scale and future block stability

Stage 6 must prevent source geometry that would predictably become one-block Z noise during Stage 10 materialization.

The implementation policy remains:

> the smallest causal relief wavelength is many future horizontal cells, never approximately one cell.

Revision 3 extends detail below the old ~1024-cell floor, but the smallest current layer remains on the order of hundreds of cells and uses continuous gradient interpolation with small amplitude. This permits visible nested zoom detail without turning exact XY neighbours into independent random heights.

Automated evidence examines representative windows for:

- local slope;
- second-difference / curvature magnitude;
- isolated quantized extrema;
- checkerboard/corner-supported Z patterns.

Stage 9 must preserve this property while refining. Stage 10 will enforce the exact `>= 2 blocks` natural-feature thickness invariant on materialized XYZ Terrain.

## Continuum and multi-resolution behavior

Stage 6 reuses Continuum rather than allocating a full-world raster.

A read-only adapter exposes the same continuous surface as a `ContinuumScalarField` for map/materialization infrastructure. The adapter is representation only.

No Stage 6 operation may require enumerating the whole logical world.

Cost remains bounded by:

```text
requested region
× requested sample count/resolution
× fixed local deterministic work
```

The massif model examines only a fixed neighbouring set of deterministic regional cells. Local peaks similarly examine a fixed local neighbourhood. There is no precomputed global mountain list.

For nested Continuum levels, a coarse request samples the same `ContinuousTerrainSurface` at coarser coordinates. It does not generate all exact cells and downsample them, and it does not choose a different terrain algorithm.

## Seam contract

Page/tile/window boundaries are never physical Terrain boundaries.

Required evidence includes:

- horizontal overlapping-window equality;
- vertical overlapping-window equality;
- diagonal/corner adjacency continuity;
- arbitrary overlapping-window equality;
- shared-coordinate equality between coarse and fine LOD;
- query-order independence;
- cache eviction/rematerialization independence.

With diagnostic borders hidden, technical tile/page boundaries must not be discoverable from Terrain discontinuities.

## F2 Inspector contract

F2 provides two views of the same Stage 6 surface:

- **2D world map** — pan/zoom, tile fallback/diagnostics, seed controls and Stage 5/6 settings;
- **3D terrain view** — bounded region sampled from the same source with perspective/orbit/pan/zoom.

Camera distance may request different sampling density but cannot modify Terrain truth.

The 2D map must not hide available detail through magnification filtering. Revision 3 therefore uses:

```text
minification: Linear
magnification: Nearest
```

Coarse imagery can still shrink smoothly, while a ready detailed tile is not blurred when enlarged. This is presentation-only and does not alter the generated surface.

## Required automated proof

Stage 6 acceptance requires, where applicable:

- deterministic equality across equivalent fresh sources;
- seed sensitivity;
- surface revision sensitivity;
- Stage 5 Definition sensitivity;
- Stage 6 Definition sensitivity;
- query-order and unrelated-query independence;
- bounded finite output;
- submergence derived only from surface Z and the shared datum;
- coastal zero-band proof showing Stage 6 can refine both sides of the Stage-5 crossing;
- deep-land/deep-ocean proof showing coastal detail cannot flip deep support;
- horizontal/vertical/diagonal/arbitrary overlap seam proofs;
- shared-coordinate multi-resolution equality;
- coarse observation proof that finer sampling exposes real additional structure;
- near-field proof that detail exists below the previous kilocell floor;
- unit-grid slope/curvature and anti-spike/checkerboard properties;
- lazy creation and bounded requested-work behavior;
- representative scale profile with constant work for the same requested sample count across larger logical worlds;
- architecture/ArchUnit gates;
- full Gradle tests and JaCoCo gate;
- Docs Site;
- manual F2 inspection across multiple seeds and materially different settings.

A deliberate source-model replacement increments the Stage 6 revision so derived presentation/cache identity cannot silently reuse a previous terrain model.

## Explicit Stage 6 boundary

Stage 6 does **not** implement:

- Stage 7 drainage/depression topology;
- river networks or lake basins as independent systems;
- Stage 8 coupled rivers/lakes/surface;
- Stage 9 hierarchical regional refinement;
- Stage 10 integer XYZ materialization;
- climate;
- geology/sediment/soil evolution;
- runtime mutable surface evolution;
- global whole-world erosion solves.

No later-stage system may be introduced merely to improve the Stage 6 picture.

## Done when

Stage 6 is complete only when production surface, tests, scale evidence, F2 inspection and canonical documentation are coherent and green on one Stage 6 PR head, followed by explicit manual user acceptance.

Until that acceptance:

**Stage 6 must not be merged and Stage 7 must not begin.**

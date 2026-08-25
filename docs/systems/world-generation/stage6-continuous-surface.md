# Stage 6 — Continuous Surface Evolution Prototype

## Status

**In progress on the Stage 6 feature branch. Not accepted. Stage 7 remains blocked.**

This page is the canonical Stage 6 contract. It exists before the production implementation so the implementation can be reviewed against an explicit semantic boundary.

## Goal

Stage 6 turns the accepted Stage 5 `MacroGeophysicalField` into one deterministic, coordinate-addressable **continuous Terrain surface** with physically meaningful relief hierarchy.

The Stage 5 field remains the world-scale geophysical cause. Stage 6 develops that cause into continuous Z geometry; it does not create a second independent terrain painter.

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

Large-scale ocean basins, exposed land and broad continental character must remain recognizably inherited from Stage 5. Finer observation may reveal additional deterministic relief structure, but it must reveal the same already-defined world rather than camera-created detail.

## Semantic ownership

Stage 6 belongs to the existing semantic module:

```text
world/terrain
```

The reusable concept is the world's Terrain surface itself. It can be consumed by drainage, hydrology, later refinement, exact XYZ materialization and presentation without naming any one of those consumers, so ADR-026 does not permit it to live under Stage 7 drainage, the visualizer, or Continuum.

No new top-level `world/surface` module is introduced.

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

The existing runtime `TerrainSystem` remains the authority for exact mutable XYZ Terrain presence/material identity. The existing integer `TerrainSurfaceLookup` remains a projection of those materialized runtime columns. Stage 6 does **not** write runtime Terrain cells and does not turn that projection into the Genesis source.

Until Stage 10, `ContinuousTerrainSurface` is the canonical generated surface geometry consumed by later Genesis stages. Stage 10 will materialize from this surface rather than create another independent height source.

## Authoritative continuous contract

The public Stage 6 surface contract is conceptually:

```text
double surfaceZAt(long x, long y)
```

The returned value is continuous Z in logical world-cell coordinates. The shared sea datum is `0.0`:

```text
surfaceZ < 0  -> submerged surface
surfaceZ >= 0 -> exposed surface
```

Stage 6 does not classify oceans independently. Submergence is a consequence of the same surface relative to the same datum.

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

Shared coordinates sampled through different Continuum resolutions must therefore return bit-identical Stage 6 source values.

## Stage 6 authored settings

Stage 6 exposes only semantic properties of the world surface. The initial contract contains four normalized `0..1` controls:

- `reliefIntensity` — overall amplitude of Stage 6 relief above/below the broad Stage 5 support while preserving the Stage 5 macro world character;
- `regionalRuggedness` — tendency for uplifted structural regions to express stronger relief contrasts versus broad smoother forms;
- `plateauTendency` — tendency for elevated provinces to form broad high surfaces rather than only narrow ridges;
- `regionalReliefScale` — characteristic horizontal scale of Stage 6 regional relief structures, from smaller regional provinces to broader ones.

These are authored world semantics. They are intentionally not solver controls.

The public Definition must **not** expose:

- octave count;
- lattice/cellular span constants;
- salts/hash constants;
- blend weights;
- internal uplift thresholds;
- erosion iteration counts;
- numerical stability constants;
- cache/page/tile sizes.

Named convenience profiles may be added for inspection, but arbitrary Definitions remain the actual contract.

Changing Stage 6 settings does not change world seed. Changing seed preserves the selected Stage 5 and Stage 6 authored settings.

## Causal surface model

Stage 6 must produce a hierarchy of relief rather than a generic procedural-noise landscape.

The initial bounded model uses these causal layers:

1. **Macro vertical support.** Stage 5 signed macro elevation establishes broad ocean-basin and continental support and remains visible at the largest scale.
2. **Regional structural provinces.** A deterministic, coordinate-addressable low-frequency crustal/province field creates broad uplift and depression tendencies. It is subordinate to macro support rather than an independent replacement map.
3. **Orogenic belts.** Boundaries/transition zones of broad structural provinces create spatially coherent elongated uplift/ridge potential. Mountainous terrain is therefore concentrated in belts instead of being distributed uniformly over land.
4. **Plateau expression.** Elevated province interiors may be lifted and flattened over broad areas according to `plateauTendency`, producing plateau/highland character distinct from narrow ranges.
5. **Subordinate regional relief.** Smaller deterministic deformation adds valleys, shoulders and secondary ridges only where the larger structural context permits it. It cannot overwrite continental/ocean-scale support.

The exact hidden algorithm may change while these semantics remain stable.

Stage 6 may use erosion/weathering-inspired shaping only as a local mathematical deformation of this surface. It must not construct Stage 7 drainage graphs, river networks, lakes, climate, sediment transport or runtime erosion state.

## Realism requirements

A visually plausible Stage 6 surface must show a readable hierarchy:

```text
continent / ocean basin
    ↓
regional uplift / depression province
    ↓
range / plateau / broad lowland
    ↓
subordinate relief
```

Required qualitative properties:

- uplands are spatially clustered and structurally organized, not uniformly salted across all land;
- plateaus are broad regions rather than noisy collections of peaks;
- lowlands/depressions are coherent regions;
- range/ridge structures are elongated and correlated;
- smaller relief is modulated by larger relief;
- zooming reveals real deterministic additional structure rather than denser sampling of a single smooth blob;
- no layer may introduce sample-scale checkerboard, stipple or alternating tiny elevation patches.

## Minimum feature scale and future block stability

Stage 6 must already prevent continuous geometry that would predictably become one-block Z noise during Stage 10 materialization.

The Stage 6 algorithm therefore has a hard implementation policy:

> the smallest causal relief wavelength is many future horizontal cells, never approximately one cell.

The initial implementation keeps the smallest authored-independent structural span well above the future two-block acceptance threshold. Solver constants remain private and may become stricter later.

Automated property evidence must examine the continuous surface without prematurely materializing voxels. Representative windows must bound:

- local slope;
- second-difference / curvature magnitude;
- adjacent integer-Z crossing density;
- isolated one-sample extrema;
- high-frequency sign/change density along X, Y and both diagonals;
- 8-neighbour/corner zig-zag behavior.

The purpose is not to prove Stage 10's exact voxel invariant early. It is to prove that Stage 6 is not creating source geometry whose dominant spatial scale is one future block.

Stage 9 must preserve this property while refining. Stage 10 will enforce the exact `>= 2 blocks` natural-feature thickness invariant on materialized XYZ Terrain, including diagonals and corner connections, without using post-hoc morphological smoothing as the primary fix.

## Continuum and multi-resolution behavior

Stage 6 reuses Continuum rather than creating a giant full-world raster.

A narrow read-only adapter may expose the same continuous surface as a `ContinuumScalarField` for existing map/materialization infrastructure. The adapter is representation only and must not become the owner.

No Stage 6 operation may require enumerating the whole logical world.

Cost must be bounded by:

```text
requested region
× requested sample count/resolution
× fixed local deterministic work
```

If the algorithm needs regional context, that context must be coordinate-addressable, bounded and deterministic. There is no global precomputation of all mountains, all provinces or the full surface.

For nested Continuum levels, a coarse request samples the same `ContinuousTerrainSurface` at coarser coordinates. It does not generate exact fine cells and downsample them, and it does not select a different terrain algorithm.

## Seam contract

Page/tile/window boundaries are never physical Terrain boundaries.

Required evidence includes:

- horizontal overlapping-window equality;
- vertical overlapping-window equality;
- diagonal/corner adjacency continuity;
- arbitrary overlapping-window equality;
- shared-coordinate equality between coarse and fine LOD;
- query-order independence;
- cache eviction/rematerialization independence through Continuum infrastructure.

With diagnostic borders hidden, technical tile/page boundaries must not be visually discoverable from Terrain discontinuities.

## F2 Inspector contract

Stage 6 is the first stage where the generated surface is a real continuous landscape rather than only macro support. Therefore the long-term inspector requirement begins here.

F2 must provide two real views of the same Stage 6 surface:

- **2D world map** — retains current pan/zoom, tile fallback/diagnostics, seed controls and world-generation settings;
- **3D terrain view** — renders a bounded selected/centered region sampled from the same `ContinuousTerrainSurface`, with perspective/orbit/pan/zoom controls sufficient to inspect relief hierarchy.

The 3D view is an observer. Camera distance may request a different sampling density but may not modify surface truth. If a finer mesh is requested, its shared coordinates must agree with the coarse mesh and any newly visible detail must come from deterministic coordinate-addressed Stage 6 structure.

The implementation should remain an inspection tool, not become a dashboard or game renderer.

## Required automated proof

Stage 6 acceptance requires, where applicable:

- deterministic fixed regression samples;
- seed sensitivity;
- surface revision sensitivity;
- Stage 5 macro Definition sensitivity;
- Stage 6 surface Definition sensitivity;
- query-order independence;
- unrelated-query independence;
- horizontal seam proof;
- vertical seam proof;
- diagonal/corner continuity proof;
- overlapping-window proof;
- shared-coordinate multi-resolution consistency;
- bounded output range and finite values;
- proof that increasing observation resolution exposes additional causal structure in representative regions;
- continuous anti-one-block-noise properties described above;
- representative scale profile with constant work for the same requested sample count across progressively larger logical worlds;
- no whole-world materialization;
- architecture/ArchUnit gates;
- full Gradle tests;
- JaCoCo gate;
- Docs Site;
- manual F2 inspection across multiple seeds and materially different Stage 5/Stage 6 settings.

Fixed regression samples lock the accepted model revision intentionally. A deliberate algorithm revision must change the Stage 6 revision and update those samples explicitly.

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

Stage 6 is complete only when its production surface, tests, scale evidence, F2 2D/3D inspection and canonical documentation are coherent and green on one Stage 6 PR head, followed by explicit manual user acceptance.

Until that acceptance:

**Stage 6 must not be merged and Stage 7 must not begin.**

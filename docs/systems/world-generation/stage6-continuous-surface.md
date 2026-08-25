# Stage 6 — Continuous Surface Evolution Prototype

## Status

**In progress on the Stage 6 feature branch. Not accepted. Stage 7 remains blocked.**

This page is the canonical Stage 6 contract. Manual visual acceptance is required before merge.

## Goal

Stage 6 turns the accepted Stage 5 `MacroGeophysicalField` into one deterministic, coordinate-addressable **continuous Terrain surface** with a readable hierarchy of coast, lowlands, uplands, plateaus and mountains.

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

Stage 5 remains the world-scale geophysical cause. Stage 6 develops that cause into continuous Z geometry; it does not create an unrelated second terrain painter.

## Ownership

The authoritative generated surface belongs to:

```text
world/terrain
```

`world/continuum` remains technical addressing/materialization/cache/LOD infrastructure. The visualizer owns only presentation. Runtime `TerrainSystem` remains the authority for materialized mutable XYZ Terrain and is not reused as Genesis surface truth.

No top-level `world/surface` module is introduced.

## Authoritative contract

```text
double surfaceZAt(long x, long y)
```

The returned value is continuous Z in logical world-cell coordinates. Sea datum is `0.0`:

```text
surfaceZ < 0  -> submerged
surfaceZ >= 0 -> exposed
```

For fixed authoritative inputs and XY, the returned Z is fixed. It must not depend on:

- camera position or zoom;
- map tile/page/window identity;
- selected representation LOD;
- request order;
- unrelated queries;
- cache residency or eviction;
- presentation scheduling.

Shared source coordinates therefore remain identical across Continuum resolutions.

## Authored settings

Stage 6 exposes four normalized semantic controls:

- `reliefIntensity` — overall Stage 6 relief amplitude;
- `regionalRuggedness` — mountain/upland contrast and mountain-system activity;
- `plateauTendency` — tendency for elevated provinces to form broad high surfaces;
- `regionalReliefScale` — characteristic scale of provinces and mountain systems.

These are world-character settings. Public Definitions do not expose octaves, salts, lattice spans, peak counts, cache sizes, blend weights or other algorithm tuning.

Changing Stage 6 settings preserves world seed. Changing seed preserves selected Stage 5 and Stage 6 settings.

## Current causal model — surface revision 4

Revision 4 replaces the rejected Stage 6 shoreline and mountain prototypes rather than tuning them cosmetically.

### 1. Macro vertical support

Stage 5 signed macro elevation remains the broad continental/ocean-basin support. Deep continental and deep ocean character is inherited directly from it.

### 2. Coherent coastal refinement by coordinate warp

The rejected revision 3 coast added independent multi-scale height noise around `Z=0`. Although bounded, thresholding that sum produced visibly shredded shorelines and unrealistic short appendages.

Revision 4 does **not** add noise directly to coastal Z.

Instead it bends the Stage 5 field coordinates inside a narrow coastal transition:

```text
Stage-5 macro field
       ↓
bounded broad coordinate displacement
       +
bounded medium coordinate displacement
       +
bounded fine coordinate displacement
       ↓
resample the same Stage-5 field
       ↓
blend only near the original sea crossing
```

The current hidden displacement scales descend only to regional shoreline structure; there is no tiny coastline-noise layer. Far enough inland/offshore the warp weight becomes zero, so deep support cannot be flipped.

The intended result is coherent bays, capes and near-shore variation without a noisy saw-edge.

### 3. Broad regional provinces

Independent low-frequency provinces organize ordinary continental interiors into:

- lowlands;
- rolling uplands;
- plateau/highland regions.

Mountain placement does not own all interior elevation.

### 4. Finite mountain systems

Mountains exist only inside finite deterministic regional systems. A system contains several unequal overlapping two-dimensional massifs with local offsets, curvature and occasional short side branches.

The massif union defines **where a mountain region exists**. It is not itself the final mountain shape.

This prevents both previously rejected failure modes:

- world-spanning zero-crossing ridge networks;
- one smooth painted bent strip masquerading as a mountain range.

### 5. Ridge-dominant mountain interior

Revision 4 deliberately reduces the smooth massif pedestal. Most mountain height now comes from several differently oriented ridged fields inside the finite massif envelope:

```text
finite massif envelope
       ↓
broad ridged relief
 + medium ridged relief
 + fine ridged relief
 + local peaks
       ↓
mountain surface
```

The different ridge scales/orientations break a system into ridges, shoulders, saddles and peaks when observed more closely. Ridged fields never choose global mountain placement; outside a finite mountain envelope they contribute no mountain height.

### 6. Ordinary nested relief

Non-mountain land receives descending rolling/hill/fine/micro relief. Smaller layers have smaller amplitudes so the continuous source does not predict one-cell Z chatter during later integer materialization.

Plateau tendency attenuates smaller relief rather than creating mathematically flat shelves.

### 7. Ocean-floor relief

Broad bathymetric structure remains part of the same continuous surface. It is not a second ocean mask.

## F2 map representation

A major visual failure in the previous prototype was attempting to represent raw Terrain Z using only one globally normalized color value. At useful zoom levels, substantial local relief could collapse to almost the same green byte even though raw Z differed.

Revision 4 therefore separates **Terrain truth** from **map presentation**.

`TerrainSurfaceMapTileGenerator` samples the same authoritative surface on a bounded tile lattice plus one ghost row/column:

```text
128 × 128 displayed samples
129 × 129 raw-Z samples
```

The extra samples are used only to compute a scale-appropriate local normal/hillshade. No additional terrain is generated and no map value feeds back into world truth.

As Continuum requests finer levels, hillshade is recomputed from the finer raw-Z lattice. Finer observation can therefore reveal real slopes/ridges that were invisible in a coarse overview instead of merely stretching one flat color.

The canonical source consistency law still applies to `ContinuousTerrainSurface`; LOD-aware hillshade is explicitly a derived representation.

## Atomic visible LOD promotion

The previous map renderer could mix ready fine tiles with coarse parent fallback tiles in the same viewport frame. With strong terrain contrast this appears as transient square/checkerboard artifacts during pan/zoom.

Revision 4 changes the viewport presentation rule:

> visible map detail is promoted atomically.

Tiles may still generate asynchronously and independently, but while any visible target needs a coarser ancestor, all visible targets are rendered at one common fallback depth. The viewport switches to the finer visible level only when that level is ready coherently.

This is representation policy only and does not affect Terrain truth or cache semantics.

## 3D inspector

F2 retains a real bounded 3D view sampled directly from `ContinuousTerrainSurface`.

The 3D observer may use presentation-only vertical exaggeration and nested sampling density. It may not modify source Z. Shared coordinates remain exact source values.

## Realism requirements

Stage 6 visual acceptance requires a readable hierarchy:

```text
continent / ocean basin
    ↓
coherent coast + regional province
    ↓
lowland / plateau / finite mountain system
    ↓
massif region
    ↓
ridges / saddles / peaks
    ↓
small continuous relief
```

Required qualitative properties:

- coastlines are coherent curves with regional irregularity, not smooth blobs and not noisy saw-edges;
- coast refinement does not create frequent short sign alternation at small horizontal steps;
- deep land/ocean cannot be flipped by shoreline refinement;
- mountain systems are finite areas with beginnings, endings, gaps and possible branches;
- mountains read as ranges/massifs with internal relief rather than smooth stains or single strips;
- zooming reveals deterministic additional relief structure;
- non-mountain interiors are not perfectly flat;
- no sample-scale checkerboard, isolated spike or one-cell stipple appears in continuous Z.

## Future block stability

Stage 6 already guards against source geometry that would predict one-block Z noise at Stage 10.

Automated checks cover representative unit-grid windows for:

- adjacent slope;
- second difference / curvature;
- isolated quantized extrema;
- 2×2 checkerboard alternation.

This does not replace Stage 10's exact voxel-thickness acceptance. It prevents Stage 6 from handing Stage 10 an obviously pathological source.

## Continuum / boundedness

Stage 6 never materializes the whole logical world.

Cost remains bounded by requested coordinates and fixed local deterministic work. Page, tile and cache boundaries are technical only and cannot become Terrain seams.

Required representation/source evidence includes:

- horizontal/vertical/diagonal overlap equality for source sampling;
- arbitrary overlapping-window equality;
- shared-coordinate equality across source resolutions;
- deterministic eviction/rematerialization;
- bounded requested work;
- no whole-world raster;
- coherent visible fallback LOD during asynchronous map preparation.

## Automated proof

Stage 6 currently requires tests for:

- deterministic equivalent-source equality;
- seed sensitivity;
- surface revision sensitivity;
- Stage 5 and Stage 6 Definition sensitivity;
- query-order/unrelated-query independence;
- finite bounded Z;
- coherent bounded coastline refinement;
- deep-land/deep-ocean preservation;
- finer-observation residual structure;
- sub-kilocell causal detail;
- anti-spike/checkerboard source properties;
- terrain-map hillshade contrast and land/ocean palette separation;
- bounded map sampling using one ghost border;
- atomic visible LOD promotion;
- Continuum scale profile;
- full Gradle/JaCoCo/ArchUnit gates;
- Docs Site.

Automated tests do not constitute aesthetic acceptance.

## Explicit Stage 6 boundary

Stage 6 does **not** implement:

- Stage 7 drainage/depression topology;
- rivers or lakes;
- Stage 8 coupled hydrology;
- Stage 9 hierarchical regional refinement;
- Stage 10 integer XYZ materialization;
- climate;
- sediment/soil evolution;
- runtime mutable erosion;
- global whole-world erosion solves.

No later-stage system may be introduced merely to improve the Stage 6 image.

## Done when

Stage 6 is complete only when production surface, bounded map/3D inspection, tests, scale evidence and canonical documentation are coherent and green on one PR head **and** the user explicitly accepts the visual result across multiple seeds/settings/zoom levels.

Until then:

**Stage 6 must not be merged and Stage 7 must not begin.**

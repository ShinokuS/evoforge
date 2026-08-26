# ADR-027 — Hierarchical Geomorphic Geography

Status: proposed / replacement direction after rejected PR #136

## Context

PR #136 proved that a single continuously sampled noise/refinement heightfield is the wrong primary representation for EvoForge Stage 6. It was deterministic and locally addressable, but manual inspection showed scalar-field landmasses, isolated mountain blemishes, poor multi-scale morphology and presentation instability.

The product target is a country/continental-scale logical world containing hundreds of landscape regions comparable in visual information density to a Songs-of-Syx-scale world-map area, while materializing only requested terrain.

The retired V12/V13 line contains one important positive result: **V12 local terrain morphology was visually successful at cell-near scale.** Its balanced hills/depressions, rolling relief, physical feature sizes and explicit slope relaxation produced irregular but readable terrain without one-block Z chatter. V13 also contains useful elongated/asymmetric hill-profile mathematics. Their dense full-world architecture and global placement strategy remain retired.

## Decision

EvoForge terrain generation becomes **structure first, local morphology second**.

```text
Stage 5 macro geophysical causes
        ↓
regional geomorphic structure
        ↓
mountain belts / plateaus / basins / coast context
        ↓
V12-informed local morphology conditioned by those structures
        ↓
continuous Terrain surface
        ↓
Stage 7 drainage topology
        ↓
Stage 8 Genesis rivers/lakes + bounded surface reconciliation
        ↓
later exact XYZ and Runtime mutation
```

### 1. Stage 5 supplies causes, not only one elevation number

The accepted Stage 5 macro elevation remains valid. A separate Stage 5 preparation PR may expand `world/geophysics` with consumer-neutral structural context while preserving accepted elevation output.

The intended facts are broad continental/ocean support, macro-margin influence, stable structural-region identity and local boundary regime/orientation/strength. Stage 5 does not create Terrain mountains/rivers/lakes.

### 2. Major geography is explicit structure

Stage 6 represents major terrain as natural structures rather than thresholded decorative fields:

- finite connected mountain/orogenic belts;
- child ridge families, passes, foothills and branches;
- plateaus/escarpments;
- broad uplands, lowlands and basins;
- calm plains;
- bounded coast/margin refinement.

Technical pages/tiles never own these structures.

### 3. Local terrain reuses the successful V12 principles

The old implementation is not restored, but the local algorithmic lineage is intentionally reused:

- sparse explicit hills and depressions;
- subordinate rolling relief;
- feature scales expressed in physical world/cell units;
- no requirement that every point receive equal-amplitude noise;
- gradient/curvature control that prevents one-block Z chatter;
- deterministic overlap/halo rules when local relaxation is required.

Regional structures answer **where/why** terrain exists; the local synthesizer answers **what the nearby surface looks like**.

### 4. Mountains are belts first, peaks last

Ordinary major mountain geography is not a set of independent circular or elliptical bumps.

A belt has stable identity and finite geometry such as centerline, length, varying width, orientation, uplift/asymmetry and foothill envelope. Child ridges/branches/passes are deterministically derived from the belt. Individual local peaks are subordinate children.

Useful V13 elongated/asymmetric profiles may shape child ridges/landforms, but V13-style independent mountain spots are not the global mountain model.

### 5. Hierarchical refinement is natural, not camera truth

A coarse observation may stop at parent structures. A finer request may deterministically reveal child ridges, local hills or finer coast geometry. Those children are derived from stable feature identities and exist independently of which consumer requested them first.

Camera zoom selects a presentation/request depth only. It never changes generated truth.

### 6. Drainage and rivers are later Genesis solvers, not runtime history

Stage 7 analyzes the accepted surface to produce drainage/watershed/depression/spill topology.

Stage 8 creates river channels, lake basins and bounded surface adjustment. It may use erosion-like or stream-power-like mathematics as a finite **Genesis construction solver**. Those iterations are not simulated years and do not introduce runtime erosion physics.

Real later-time erosion, landslides, digging/construction and water-driven Terrain mutation are later Runtime mechanics.

### 7. Runtime Terrain eventually uses reconstructable Genesis + sparse changes

Genesis terrain remains reconstructable from world identity and algorithms. Future authoritative runtime changes are stored separately and persist independently of caches/presentation.

Conceptually:

```text
Genesis terrain + persistent authoritative changes = current Terrain truth
```

The exact sparse representation is postponed until runtime mutation/persistence work actually needs it.

### 8. Visualization is an acceptance tool

F2 must allow inspection from world scale toward cells and expose causal layers. It must not hide generator quality behind rivers/forests/materials that belong to later stages.

Presentation may use clipmap-like nested resident levels, overscan, asynchronous preparation and bounded caches. However:

- expensive generation does not block the render thread;
- ordinary pan does not alter simulation truth/detail semantics;
- incomplete technical LOD is not shown as checkerboard geography;
- zoom reveals real additional natural structure rather than a stretched raster;
- projected future integer-cell heights can be inspected before Stage 10.

## Required acceptance

Across multiple fixed seeds:

- land/ocean morphology is coherent at country/continental scale;
- mountain systems read as connected ranges rather than spots/lesions;
- broad plains/plateaus/basins remain readable;
- regional zoom reveals deterministic child geography;
- local terrain retains the accepted V12-like balance and avoids one-block Z noise;
- overlapping requested areas reproduce exactly the same structures/heights;
- work/memory depend on requested area/detail and bounded caches rather than total logical world area;
- F2 pan/zoom has no visible incomplete/fallback geography;
- the user manually accepts macro, regional and cell-near views before Stage 7 begins.

## Consequences

PR #136 remains rejected and is not an implementation base.

Reusable foundations survive only where representation-neutral: Continuum addressing/materialization/cache infrastructure, deterministic generation utilities, accepted Stage 5 macro elevation, and the algorithmic ideas from V12/V13 explicitly identified above.

No new V-number whole-generator lineage is introduced.

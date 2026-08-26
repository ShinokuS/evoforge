# ADR-027 — Hierarchical Geomorphic Geography

Status: proposed / Stage 6 replacement direction

## Context

The first Stage 6 experiment (`PR #136`) was rejected after manual visual inspection.

The rejected implementation treated the final map primarily as a continuous scalar height field refined by multiple noise-like layers, coastline warps and tile-level LOD. It preserved determinism and bounded queries, but failed the actual product target:

- landmasses read as scalar-field blobs rather than convincing continental geography;
- mountains read as isolated bumps and mottled height patches rather than long mountain systems;
- zoom mostly exposed a denser sample of the same visual texture instead of genuinely new geographic structure;
- ordinary pan caused visible coarse/fine instability, pixelation, blur and stalls;
- renderer and generator became coupled through symptom-driven LOD fixes.

The visual reference is the world-map morphology of Songs of Syx: coherent coastlines, broad plains, clearly readable mountain ranges and region-scale structure. EvoForge must operate at a much larger logical scale: hundreds of Songs-of-Syx-scale landscape regions may exist inside one country/continental-scale world.

The large-world invariant remains unchanged: the engine must compute and materialize only requested areas. A giant full-world raster is forbidden.

## Decision

EvoForge world geography will be generated **structure first, raster second**.

The authoritative generated world is not defined as `height = sum(noise octaves)`. It is defined by a deterministic hierarchy of sparse geophysical and geomorphic structures. Continuous terrain height is a derived observation of those structures.

### 1. Hierarchical structural model

World geography is described at several semantic scales.

1. **Continental / crustal scale**
   - continental blocks and oceanic basins;
   - large shelf margins;
   - broad plate/crust orientation and motion context;
   - only a small sparse set of descriptors is needed for any queried macro area.

2. **Orogenic / regional scale**
   - convergent boundaries create mountain belts;
   - divergent structures create rifts and basins;
   - interiors can contain plateaus, uplands and large depressions;
   - coast geometry is refined as a boundary curve, not by painting independent coastline noise.

3. **Landscape-region scale**
   - long ridges split into ridge families, passes and foothill systems;
   - plateaus receive escarpments and internal relief;
   - plains receive sparse rolling landforms rather than uniform high-frequency noise;
   - this is approximately the scale at which one Songs-of-Syx-like landscape becomes visually legible.

4. **Local terrain scale**
   - deterministic child structures add local ridges, hills and depressions only when a request needs that physical detail;
   - later stages add drainage, valleys, rivers, lakes, climate, soils and ecology as consequences of the same hierarchy.

### 2. Stable feature identity and lazy refinement

Every structural feature has a deterministic stable id derived from world seed, algorithm revision and its hierarchical address.

A feature owns its geometry and may deterministically produce child features. Child generation does not depend on camera history, query order, cache state or threads.

A bounding-box query at detail level `L` performs only:

- lookup/generation of the sparse parent structures intersecting that box;
- deterministic refinement of those parents down to `L`;
- evaluation/rasterization of only the requested area.

Technical pages and render tiles remain caches only. They are never natural regions and never own geography.

### 3. Continental geometry

Stage 5 scalar macro elevation remains accepted evidence and may be reused as a compatibility input during migration, but it is no longer sufficient as the final shape generator.

The replacement Stage 6 prototype must introduce sparse continental/crustal structures whose geometry is based on deterministic regions and boundaries, not a thresholded low-frequency noise blob.

A practical implementation may use hierarchical weighted Voronoi/power cells or another local reconstructable region graph. Continental family assignment is inherited from a coarser parent level so neighboring cells can form coherent large blocks without whole-world raster generation.

Sea/land still comes from one elevation/surface truth relative to one sea datum. The important change is that the supporting elevation is caused by structural regions and boundary distance, not by arbitrary additive texture.

### 4. Mountain belts, not mountain spots

Mountains are represented as explicit **belt/ridge geometry**.

A mountain belt is a finite spline/polyline corridor with semantic properties such as:

- centerline;
- length;
- variable width;
- orientation;
- uplift strength;
- asymmetry;
- ridge-family count;
- pass frequency;
- foothill width.

The rendered/physical height contribution is derived from distance along/across this geometry and its deterministic child ridges.

The acceptance target is that ordinary mountains are much longer than they are wide and visually read as connected ranges. Circular isolated mountain blobs are not an acceptable default representation.

### 5. Semantic level of detail

LOD becomes **structural truncation**, not merely coarser sampling of the same noisy field.

At far zoom the map observes continental blocks, basins and the largest mountain belts.

At medium zoom it additionally observes regional coast refinement, secondary ridges, plateaus and foothills.

At close zoom it additionally observes local ridges, hills and other small terrain structures.

All levels refer to the same deterministic hierarchy. Camera zoom decides only which already-defined hierarchy depth is useful to observe; it never changes world truth.

This guarantees that zoom reveals new geographic structure rather than enlarging the same pixels.

### 6. Renderer reset

The rejected per-tile LOD-promotion behavior is not carried forward.

The replacement map renderer uses a stable front/back render surface with overscan.

- Pan at constant zoom **cannot change LOD**.
- A resident overscan area is larger than the viewport, so ordinary drag reuses the same ready surface.
- When the camera approaches the overscan edge, the next surface is produced asynchronously off the render thread.
- The old surface remains visible until the replacement coverage is completely ready; then one atomic swap occurs.
- Zoom selects a new semantic detail level only after an explicit zoom threshold with hysteresis.
- While the new zoom representation is generated, the previous complete surface remains visible. No checkerboard parent fallback is allowed.
- Texture uploads are bounded per frame and never block input processing.

The renderer may internally use tiles/pages for cache reuse, but viewport presentation is atomic and stable.

### 7. Map appearance

The Stage 6 inspection map is a geographic observer, not a diagnostic heatmap.

Default output should use:

- continuous terrain tint rather than coarse discrete elevation bands;
- stable hillshade from the observed surface;
- anti-aliased coast boundaries;
- no tile borders or LOD diagnostics unless explicitly enabled;
- no forests, rivers, climate or settlements before their owning stages exist.

The reference target is morphology, not copying Songs of Syx art assets.

## Required prototype gates

The new Stage 6 line is not accepted until all of the following are true.

### Geography

Across multiple fixed seeds:

- landmasses contain recognizable shelves, peninsulas, bays and interior shape rather than mostly rounded noise blobs;
- major mountain systems form long connected belts/ridge families;
- broad plains/plateaus/basins remain visually distinct from mountains;
- no ubiquitous stippled, blistered or lesion-like surface texture;
- far, medium and close views reveal genuinely new structure while preserving the same large features.

### Determinism and seams

- same seed/revision/definitions/address => same structural feature ids and geometry;
- query order and cache history do not change features;
- overlapping requested areas produce exactly identical shared geometry/heights;
- parent/child refinement joins without spatial seams;
- no technical tile boundary is visible in terrain morphology.

### Scale

- requested work grows with requested area and requested structural depth, not total world area;
- a country/continental-scale logical domain can contain hundreds of Songs-of-Syx-scale landscape regions without pre-materializing them;
- memory remains bounded under long pan/zoom stress.

### Renderer

- selected LOD is invariant under pan;
- ordinary drag produces zero coarse/fine oscillation;
- no checkerboard/fallback flash is visible;
- world generation/rasterization performs no expensive work on the render thread;
- input/render frame pacing remains smooth while background geography is being prepared;
- an atomic completed surface replaces the previous completed surface.

### Manual visual acceptance

A fixed seed is captured at at least three scales:

1. country/continental view;
2. several Songs-of-Syx-scale landscape regions;
3. one close landscape region.

The user must accept the morphology before later drainage/ecology work proceeds.

## Consequences

This direction intentionally discards most of PR #136's terrain algorithm and its map-LOD repair work.

Reusable infrastructure may survive only where it is genuinely representation-neutral: deterministic addressing, bounded caches, async execution primitives and diagnostics.

Stage 5 remains the accepted checkpoint on `develop`; the replacement Stage 6 starts from that commit rather than from the rejected branch.

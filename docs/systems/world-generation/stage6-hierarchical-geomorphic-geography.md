# Stage 6 — Hierarchical Geomorphic Geography Prototype

This document is the replacement implementation plan after rejection of PR #136.

The user-facing target is concrete: a large EvoForge world should look like a coherent geographic world map in the spirit of Songs of Syx, but at a scale large enough to contain **hundreds of Songs-of-Syx-scale landscape regions**. The engine must still materialize only what simulation objects or the renderer request.

No forest, river, climate, settlement or ecological painting is part of this prototype. The required visual content is land/ocean shape, plains/uplands/plateaus/basins and convincing mountain systems.

## Core idea

Use a **tiny sparse global blueprint + lazy deterministic regional refinement**.

This is deliberately different from both a giant full-world raster and an infinite stack of coordinate noise.

The sparse blueprint is cheap enough to build once for a finite world because it contains only structural descriptors: tens/hundreds of plate/crust regions and major boundary features, not millions/billions of terrain cells. Dense height samples are still generated only for requested areas.

A representative country/continental world may contain, for example, dozens of root crust/plate sites and hundreds of derived major structural segments while logically containing millions of local terrain cells. Exact descriptor counts are algorithm details, not public semantic controls.

## World representation

```text
World seed + authored definitions
            |
            v
Sparse Geophysical Blueprint
  - crust/plate regions
  - continental families
  - oceanic basins
  - plate motion/orientation
  - shared region boundaries
            |
            +--------------------+
            |                    |
            v                    v
Major structural causes      Coast/shelf geometry
  - convergent belts          - continental margin
  - rifts/basins              - recursive boundary refinement
  - plateaus/uplands
            |                    |
            +----------+---------+
                       v
          Lazy hierarchical refinement
          - ridge families
          - passes / foothills
          - regional relief forms
          - local child structures
                       |
                       v
        Continuous requested terrain surface
                       |
                +------+------+
                |             |
                v             v
        simulation query   map observation
```

The hierarchy is the generated fact. A raster is only an observation/materialization.

## Milestone 6A — Stable renderer before new terrain

Do this first so generator quality can be judged without presentation bugs.

### Remove from the experiment

Do not reuse PR #136's viewport-atomic coarse-parent promotion or its per-drag LOD repair logic.

### New invariant

**Pan at constant zoom never changes semantic detail level.**

LOD/detail selection is a function of zoom scale only. A mouse drag can change requested world bounds, but it cannot promote/demote resolution.

### Stable viewport snapshot

Introduce a render-side `StableMapSnapshot` concept:

- one detail level for the whole snapshot;
- coverage larger than the visible viewport (overscan);
- immutable set of ready map pages/surfaces;
- front snapshot remains displayable while replacement work happens;
- new coverage is generated asynchronously;
- same-LOD pages may extend coverage without changing visual scale;
- a zoom-level replacement is shown only when the new visible coverage is complete.

The technical cache may remain page/tile based. The screen must not expose independent tile LOD decisions.

### Scheduling

- generation/raster work runs only on worker threads;
- GL texture upload is budgeted on the render thread;
- user input never waits for terrain generation;
- camera motion cancels obsolete queued work, but already-complete cache entries remain reusable;
- zoom requests are debounced/hysteretic so wheel noise cannot thrash detail levels;
- ordinary pan prefetches toward the motion direction while preserving current LOD.

### 6A acceptance

Use the accepted Stage 5 macro field as temporary source and prove:

- selected level is bit-for-bit unchanged during long pan-only stress;
- no coarse/fine flashing during pan;
- no blank checkerboard on ordinary drag;
- render-thread frame pacing remains smooth while background requests are active;
- cache and outstanding jobs stay bounded.

Only after this is true should the new geography be judged visually.

## Milestone 6B — Sparse geophysical blueprint

Introduce a new structure source under the existing `world/geophysics` semantic owner rather than a generic `world/geography` umbrella.

Suggested public boundary:

```text
GeophysicalBlueprint.create(seed, revision, definition, domain)
GeophysicalBlueprint.query(bounds, structuralDepth)
```

The exact names may change, but callers must not construct the hidden algorithm directly.

### Root regions

Generate a small deterministic set of root sites in normalized finite-world space using a stable blue-noise / best-candidate placement algorithm.

Build a deterministic neighborhood graph (Voronoi/Delaunay or equivalent). The graph is sparse metadata, not a raster.

Each root region receives structural properties:

- crust class / continentality;
- base elevation tendency;
- orientation;
- motion vector;
- age/strength-like internal parameters where they causally affect later structure.

### Continental families

Do **not** classify every root region independently as land/ocean.

Place a smaller set of continental-family seeds and perform deterministic graph growth so several neighboring crust regions belong to one coherent continental block. This produces large connected masses with internal tectonic structure instead of thresholded noise blobs.

Some root regions remain oceanic and form large basins between continental families.

### Shared boundaries

Every neighboring pair owns one stable shared boundary id. Both sides reference the same geometry.

Boundary type is derived from the relative motion and crust context:

- convergence;
- divergence;
- transform/neutral.

This makes later mountains/rifts consequences of the same structural graph.

## Milestone 6C — Coast and mountain morphology

This milestone must already look meaningfully closer to the visual target before local microdetail exists.

### Coastlines

Continental/oceanic transition creates a shelf/margin corridor.

The root coast is a boundary curve. Finer detail uses deterministic recursive curve subdivision:

1. take one stable parent segment;
2. split by stable child id;
3. offset the midpoint primarily along the local normal;
4. bound the offset as a fraction of parent segment length;
5. preserve endpoints exactly;
6. recurse only to the requested structural depth.

This gives bays, headlands and peninsulas as **refined geometry of one coast**, not additive sign-changing height noise.

Shared parent endpoints make the refinement seam-free and query-order independent.

### Mountain belts

Convergent structural boundaries create explicit `OrogenicBelt` descriptors.

Each belt contains a centerline polycurve plus slowly varying width/uplift/asymmetry. A belt is expected to be substantially longer than it is wide.

At medium structural depth a belt deterministically creates several child ridge families:

- 2–5 roughly parallel/branching ridges;
- variable offsets from centerline;
- varying ridge width and height;
- passes represented as smooth reductions of uplift along arc length;
- foothill envelopes wider than the main ridge core;
- occasional branching at stable structural nodes.

At closer depth each ridge can add local peaks/spurs, but those children remain subordinate to the long-range ridge geometry.

The default world must not be populated by independent circular mountain bumps.

### Plateaus, uplands and basins

Generate sparse regional structures tied to crust regions rather than painting the whole land with local noise.

- plateaus: broad bounded areas with flatter interior and explicit escarpment transition;
- uplands: broad low-amplitude regional elevation support;
- basins: broad depressions, later useful to drainage Stage 7;
- plains: the absence of strong structural relief is a valid and common terrain state.

### Minor relief

Minor rolling relief may use band-limited procedural functions only as a subordinate texture after the structural morphology is already readable.

It cannot determine continent shape or major mountain geography.

## Milestone 6D — Continuous lazy terrain observation

Create the continuous terrain owner under `world/terrain` as a derived evaluator over geophysical structures.

Suggested request contract:

```text
TerrainRegionRequest(bounds, structuralDepth, sampleSpacing)
 -> immutable requested height samples
```

The source remains coordinate-addressable so simulation can ask for an individual point without creating a permanent region raster.

### Evaluation at one point

A point query evaluates only nearby/intersecting structures using a spatial index over the sparse blueprint:

1. base crust/ocean-basin elevation;
2. shelf/coast transition;
3. nearby mountain/rift/plateau/basin contributions;
4. child structures up to requested physical depth;
5. subordinate minor relief where appropriate.

### Hierarchical detail

Far map:

- root continental blocks;
- ocean basins;
- major coast geometry;
- major mountain belts / plateaus.

Medium map:

- refined coast;
- ridge families;
- foothills;
- regional basins/uplands.

Close map:

- local ridge children;
- hills/depressions;
- other Stage-6-local terrain details.

The same stable feature ids cause all views. Coarse observation simply stops at a shallower structural depth.

## Map rendering of Stage 6 terrain

The default F2 map should stop looking like a quantized elevation diagnostic.

Render from continuous floating observations into normal map colors:

- ocean depth uses a smooth restrained blue gradient;
- lowlands/uplands/highlands use continuous hypsometric tint;
- hillshade is a separate multiplicative lighting term;
- mountain hue must come from elevation/material presentation, not shadow alone;
- coastline is anti-aliased in screen space;
- no contour-band staircase by default;
- diagnostics remain behind `G` or another explicit toggle.

No forest, river or settlement symbols are allowed to hide weak terrain morphology.

## Concrete scale model

Do not equate one technical page with one natural region.

For design/testing, think in nested geographic scales instead:

```text
country / continental view
    contains dozens to hundreds of
landscape regions comparable to one Songs-of-Syx world-map area
    each contains many
local terrain neighborhoods
    each later materializes exact XYZ only when needed
```

The actual coordinate sizes remain content/definition choices. The architecture must not depend on one hard-coded number.

## Required visual test sheet

For every candidate algorithm revision, F2 must be able to capture the same fixed seed at three prescribed camera scales and positions.

The review sheet must show:

1. **macro** — whole country/continental area;
2. **regional** — multiple landscape regions with a full mountain belt/coast/plain relationship visible;
3. **local** — one landscape region showing child ridges and local relief.

A revision that improves one screenshot while breaking another is rejected.

At least ten deterministic seeds should be inspected before acceptance. Hand-selecting one lucky seed is not sufficient.

## Automated quality properties

Exact subjective beauty cannot be unit-tested, but obvious failure modes can.

Add property checks for:

- mountain belt length/width ratio above a minimum range-like threshold;
- ridge children remain inside/near their owning belt corridor;
- no high density of isolated near-circular high-elevation components in representative windows;
- coastline child refinement preserves parent endpoints and remains displacement-bounded;
- overlapping structural queries return identical feature ids and geometry;
- pan-only renderer stress never changes detail level;
- all visible pages in one rendered snapshot use one semantic detail level;
- requested structural work is bounded by intersecting feature count and requested depth;
- no expensive geography evaluation occurs on the render thread.

## What is deliberately postponed

This prototype does not need to solve everything at once.

Postpone until their owning stages:

- drainage topology and valley incision;
- rivers and lakes;
- erosion/sediment transport beyond what is strictly necessary to shape the Stage 6 prototype;
- climate;
- geology/material types beyond minimal geophysical causes;
- soil;
- forests/vegetation;
- settlements/roads;
- exact integer XYZ materialization.

The terrain must already look convincing without those layers.

## Done when

Stage 6 is complete only when:

- PR #136 remains rejected and no rejected heightfield algorithm is resurrected by incremental tuning;
- stable renderer milestone passes pan/zoom stress;
- continental shapes no longer read as noise blobs;
- mountains consistently read as connected belts/ranges rather than lesions/bumps;
- zoom demonstrably reveals deterministic child geographic structures;
- country-scale world browsing remains lazy and bounded;
- correctness/architecture/scale gates are green;
- the three-scale multi-seed F2 result is manually accepted.

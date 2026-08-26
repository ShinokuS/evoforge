# Stage 6 — Hierarchical Geomorphic Geography Prototype

This is the replacement implementation plan after rejection of PR #136.

The target is concrete: EvoForge must show convincing landforms at country/continental, regional and cell-near scales while materializing only requested terrain. Stage 6 contains no rivers, lakes, forests, climate, settlements or runtime erosion physics.

A finite EvoForge world is always surrounded by ocean. Stage 6 therefore receives a non-negotiable boundary condition from Stage 5: no generated landform may expose land in the hard outer-ocean belt or be clipped by the logical world edge.

## Core pipeline

```text
Stage 5 macro elevation + structural context + boundary-ocean constraint
        ↓
regional geomorphic structures
        ↓
mountain belts / plateaus / basins / uplands / plains
        ↓
child ridges / foothills / coast refinement
        ↓
V12-informed local terrain synthesis
        ↓
continuous world/terrain surface Z
```

The two middle roles are intentionally different:

- **regional structure** decides where/why a major landform exists and its orientation/extent;
- **local synthesis** decides how the nearby surface varies inside that structure.

No global dense heightmap is created.

## Input contract from Stage 5

Stage 6 consumes the accepted intrinsic macro elevation plus the structural geophysical context and finite-world boundary-ocean influence introduced by the separate Stage 5 preparation PR.

Useful input facts include:

- broad continental versus deep-ocean support;
- macro-margin influence;
- stable structural-region identity;
- local boundary orientation;
- boundary regime/strength;
- `boundaryOceanInfluenceAt(x,y)` and the hard outer-ocean classification.

Stage 6 does not infer all mountain placement from a decorative noise field when a structural cause is available.

The hard outer-ocean constraint has priority over every Stage 6 uplift contribution:

```text
boundaryOceanInfluenceAt(x,y) == 1
        =>
final Stage 6 Terrain remains below sea datum
```

This is not a camera/render mask. It is part of the finite-world geophysical input.

## Regional structures

### Mountain belts

A major mountain system begins as a finite connected belt/corridor rather than an independent peak.

A belt owns stable geometry such as:

- identity;
- centerline/path;
- length;
- varying width;
- orientation;
- uplift support;
- asymmetry;
- foothill envelope.

Convergent-like Stage 5 structural boundaries are a primary cause for major belts. Stage 6 may also support explicitly justified interior uplifts, but they remain regional structures rather than random spots.

A belt deterministically produces subordinate children:

- main ridge family;
- secondary/branch ridges;
- passes/saddles;
- foothills;
- local peak/spur opportunities.

Useful V13 elongated/asymmetric profile mathematics may be reused for individual child ridges. V13's old global placement of independent mountain spots is not reused.

No belt or child may create exposed land through the hard boundary-ocean belt. A regional structure near the edge is naturally submerged/truncated by ocean influence before it can become a visibly clipped landmass.

### Plateaus, uplands, lowlands and basins

These are explicit broad structures with bounded influence. Calm plains are a normal terrain state; every land coordinate is not required to receive visible noise.

Plateau interiors remain relatively calm and transition through an escarpment/edge profile. Basins are broad depressions that later Stage 7 can analyze hydrologically.

All uplift structures obey the same hard outer-ocean condition.

### Coast

Stage 5 defines macro ocean/land support and a guaranteed surrounding-ocean boundary. Stage 6 may refine natural margins at regional/local scales, but it must preserve hierarchical coherence:

- far view keeps the parent coastline shape;
- finer requests add bounded child bays/headlands/peninsulas/islands;
- small forms do not become uniform high-frequency chatter along the entire coast;
- overlapping requests reproduce exactly the same refined boundary;
- every natural coastline closes inside the surrounding ocean; the logical world rectangle is never used as a coastline segment.

The smooth Stage 5 boundary transition exists specifically to prevent the visual signature of a continent cut flat at the world edge.

## V12-informed local synthesis

The retired dense V12 generator is not restored, but its local terrain behavior is an explicit algorithmic reference because it already passed visual acceptance at close scale.

The new local synthesizer should preserve these ideas:

1. **Explicit balanced landforms** — sparse hills and depressions rather than a uniform field of bumps.
2. **Rolling relief** — a smooth subordinate local component.
3. **Physical feature sizes** — characteristic sizes expressed in world/cell units, so a larger world contains more features rather than stretched versions of the same feature.
4. **Context conditioning** — a plain, plateau, foothill and ridge use different local morphology policies.
5. **No one-block Z chatter** — local gradient/curvature remains bounded before eventual integer XYZ materialization.
6. **Boundary obedience** — local hills/ridges cannot cancel the hard Stage 5 outer-ocean constraint.

If a local slope-relaxation pass is needed, it must be request-local with deterministic halo/overlap rules. Whole-world relaxation is forbidden.

## Continuous Terrain owner

The Stage 6 output belongs to `world/terrain` and is a continuous/high-precision physical surface before exact XYZ.

Required invariants:

```text
same world identity + definitions + coordinates
= same Stage 6 surface value
```

and:

```text
Stage5.boundaryOceanInfluenceAt(x,y) == 1
=> Stage6.surfaceZ(x,y) < sea datum
```

independent of:

- request order;
- page boundaries;
- cache eviction/rematerialization;
- camera position;
- render scale;
- worker scheduling.

A bounded area request may materialize a temporary sample window, but the window is not authoritative geography.

## Anti-one-block proof

Stage 6 must make future integer XYZ projection readable at cell scale.

Automated properties must cover representative rugged settings and boundaries:

- bounded adjacent slope;
- bounded second difference/curvature where the model requires it;
- low density of alternating integer-height crossings after diagnostic quantization;
- no isolated single-cell peak/pit generated by the surface model;
- no checkerboard/corner-supported Z pattern;
- exact agreement across overlapping request halos;
- no quantized/projection leak of exposed land into the hard boundary-ocean belt.

The visualizer must be able to show the projected future integer heights before Stage 10 so these failures are obvious during development.

## F2 development inspector

F2 is part of Stage 6 implementation, not a later polish pass.

Required inspection layers:

1. Stage 5 macro elevation;
2. Stage 5 boundary-ocean influence;
3. Stage 5 structural regions/boundaries;
4. Stage 6 major mountain belts / plateaus / basins;
5. ridge children / foothills / local V12-style landforms;
6. final continuous Terrain Z;
7. projected integer-cell Z diagnostic.

Required presentation behavior:

- world scale to cell-near zoom in one viewer;
- whole-world view visibly shows ocean along all four logical edges;
- normal pan/zoom never blocks on terrain generation;
- expensive sampling/materialization stays off the render thread;
- no visible checkerboard or mixed incomplete LOD state;
- zoom reveals actual additional natural structures, not only a magnified texture;
- nearby levels/coverage are prefetched so ordinary interaction does not expose loading transitions;
- diagnostic selection can identify the structural cause of suspicious terrain.

A clipmap-like nested-resident representation is a strong candidate, but presentation implementation is not terrain truth and may be replaced if profiling shows a better solution.

## Scale model

The logical world may contain hundreds of regional landscape areas comparable in information density to a Songs-of-Syx world map.

This does **not** imply pre-generating all of them.

A request evaluates/refines only the geophysical/geomorphic structures intersecting the requested area and the physical detail needed by the consumer. Rebuild after cache eviction must reproduce the same result, including the finite-world ocean boundary.

Technical pages/tiles remain bounded caches only.

## Visual acceptance sheet

Every serious candidate revision is reviewed at the same fixed seeds/positions at three scales:

1. **macro** — whole country/continental area, with visible surrounding ocean on every side and no clipped landmass;
2. **regional** — several landscape regions with complete coast/plain/mountain relationships;
3. **local** — one region close enough to judge the V12-informed surface and projected cell heights.

At least ten deterministic seeds must be inspected before final acceptance. A lucky single seed is insufficient.

## Automated failure-mode gates

Where practical, test:

- all four logical edges and a non-zero outer belt remain below sea datum for representative seeds/profiles/domains;
- mountain belt length/width ratio and connectedness;
- child ridges remain associated with their owner belt;
- representative windows do not contain a high density of isolated circular high-elevation components;
- coast child refinement remains bounded and overlap-stable;
- local feature sizes remain in physical world units rather than scaling with total world size;
- overlapping regional queries return identical structures/heights;
- requested work is bounded by request area/detail, not total logical world area;
- generation is absent from the render thread hot path.

## Explicit boundary

Stage 6 does not implement:

- drainage topology;
- river channels;
- lakes;
- real/runtime erosion history;
- sediment transport;
- climate;
- soil;
- vegetation;
- settlements;
- exact XYZ Terrain.

Stage 7 analyzes drainage/depressions. Stage 8 creates Genesis river/lake geometry and may use a bounded abstract erosion-like solver only to shape the initial terrain. Runtime erosion/landslides/digging are later independent mechanics.

## Done when

- PR #136 remains rejected;
- Stage 5 structural/boundary preparation is available;
- all four world edges and the hard boundary belt are ocean for representative seeds/profiles;
- no continent/island/mountain system terminates because the logical world ended;
- major geography is structure-driven;
- local terrain recovers the accepted strengths of V12 without restoring dense world generation;
- macro/regional/local views are simultaneously convincing;
- projected cell terrain has no one-block noise;
- deterministic/seam/scale gates are green;
- F2 is smooth enough to inspect continuously from world scale to cells;
- the user explicitly accepts Stage 6 before Stage 7 starts.

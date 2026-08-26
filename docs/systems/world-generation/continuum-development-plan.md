# Continuum World Development Plan

This is the canonical executable roadmap for EvoForge Continuum/world generation.

**Stage numbers are immutable.** PR order, implementation accidents or previously completed support work never renumber stages. Development proceeds one stage at a time and the next stage starts only after manual acceptance of the current one.

## Fixed architectural laws

1. One continuous XYZ world; shared coordinates do not imply a giant shared `WorldCell`.
2. No global `WorldFact` store. Each authoritative domain owns its state.
3. Pages/chunks/tiles/caches are technical representation only, never natural geography.
4. Camera/presentation never controls simulation truth or generation semantics.
5. Same seed/version/definitions/coordinates reproduce the same Genesis facts.
6. Query order, cache history and thread order cannot move generated facts.
7. Genesis and mutable Runtime ownership are separate.
8. Continuous/high-precision terrain is formed before exact integer XYZ materialization.
9. Rivers, lakes, coasts, valleys and ecological labels are consequences of causes, not independent painters.
10. Definitions remain semantic and human-readable; numerical solver tuning stays inside implementations unless it is true content semantics.
11. Runtime cost depends on actual requested/scheduled work, not total logical world area.
12. World age alone cannot increase tick cost, scheduler size or RAM.
13. Cross-domain mutation uses explicit coupling/transfer; one mutable state has one owner.
14. No V16/V17/V18 whole-generator lineage.
15. A living/individual simulation object follows the same laws regardless of camera visibility. Optimization may change data layout/scheduling, never world semantics by observation distance.
16. A mathematical erosion/relaxation solver used during Genesis is not runtime physical history. Runtime erosion/landslide/digging mechanics are later independent work.

## Mandatory gate for every stage

A stage is complete only after the **applicable** correctness, property, determinism, order-independence, seam, replaceability, performance, scale, visualization and documentation gates pass.

Tests and performance evidence are part of implementation, not later cleanup.

The long-term `F2` Inspector is a real world-generation development instrument. Once landscape exists, it must support a continuous inspection path from world scale to projected cells, with switchable causal/diagnostic layers. Expensive world generation must not block the render thread and incomplete technical LOD state must not be exposed as geography.

## Canonical Stage 0–20 sequence

| Stage | Name |
|---:|---|
| 0 | Clean Continuum Baseline |
| 1 | Local Query + Shared Region Cache |
| 2 | Infinite-Time Foundation |
| 3 | Multi-Resolution Continuum |
| 4 | Map / Zoom Performance Proof |
| 5 | Macro Ocean + Geophysical Skeleton |
| 6 | Continuous Surface Evolution Prototype |
| 7 | Genesis Drainage + Depression Topology |
| 8 | Coupled Rivers, Lakes and Surface |
| 9 | Hierarchical Regional Refinement + Seam Proof |
| 10 | Exact XYZ Materialization |
| 11 | Climate Coupling |
| 12 | Geology, Sediment and Soil |
| 13 | Genesis -> Runtime Handoff |
| 14 | Generic Runtime Coupling + Flux Framework |
| 15 | Runtime Surface Water |
| 16 | Surface Water + Soil + Subsurface Water |
| 17 | Persistence, Compaction and Long-Time Proof |
| 18 | Underground World |
| 19 | Ecology / Derived Environment |
| 20 | Full-World Scale and Quality Acceptance |

## Current status

- **Stage 0 — complete.** Legacy dense worldgen is retired and the Continuum foundation exists.
- **Stage 1 — complete and manually accepted.** Local overlapping reads share expensive regional work; PR #122.
- **Stage 2 — complete and manually accepted.** Infinite-Time Foundation; PR #123.
- **Stage 3 — complete and manually accepted.** Multi-resolution Continuum; PR #121. Revalidated after Stage 1/2 integration.
- **Stage 4 — complete and manually accepted.** Map / Zoom Performance Proof; PR #125 merged. Revalidated during the architecture reset.
- **Stage 5 — complete and manually accepted.** Macro Ocean + Geophysical Skeleton; PR #135.
- **Stage 5 structural preparation — allowed as a follow-up PR before Stage 6.** It may expand Stage 5's consumer-neutral geophysical read contract while preserving accepted macro elevation; it must not generate terrain/rivers/lakes.
- **Stage 6 — next terrain checkpoint.** PR #136's implementation was rejected and is not a valid base.
- **Stage 7+ — blocked until replacement Stage 6 is explicitly accepted.**

---

# Stage 0–4 — accepted Continuum infrastructure

Stages 0–4 established the large-world substrate and remain accepted:

- deterministic addressable Continuum;
- bounded local queries and shared expensive regional work;
- infinite-time scheduling/compaction foundations;
- direct multi-resolution reads of one logical world;
- bounded asynchronous map/cache infrastructure and scale proof.

These stages are infrastructure. They do not own Terrain/Geophysics/Liquid truth.

A critical lesson from rejected PR #136 is that Stage 4's technical parent-fallback tile behavior is **not** automatically an acceptable final terrain-view presentation strategy. Presentation may be replaced without changing Continuum semantics.

---

# Stage 5 — Macro Ocean + Geophysical Skeleton

## Accepted goal

Create the first real world-scale geophysical cause: one deterministic macro-elevation skeleton from which broad ocean/land support and later surface processes can follow.

`world/geophysics` owns this meaning. Ocean/land is derived from the same signed macro support relative to one sea datum. Continuum only addresses/materializes/projects it.

## Accepted facts

- `MacroGeophysicalField.elevationAt(x,y)` returns signed dimensionless macro elevation;
- fixed seed/revision/definition/coordinates reproduce the same value;
- broad continental/ocean support is low-frequency and bounded-work;
- authored controls describe ocean prevalence, continental scale, cohesion, fragmentation and macro variation;
- no final terrain height, mountains, drainage, rivers/lakes or exact XYZ exist here.

## Stage 5 structural-preparation follow-up

The scalar macro-elevation contract is accepted but insufficient as the **only** cause available to structure-first Stage 6.

A dedicated Stage 5 follow-up PR may add a consumer-neutral structural geophysical read capability while preserving accepted `elevationAt` output. The target information is macro cause, not terrain result:

- broad continental/deep-ocean support before local terrain shaping;
- macro-margin influence;
- deterministic structural-region identity;
- local shared-boundary orientation;
- boundary regime/strength suitable for later interpretation as convergent/divergent/transform-like context.

Requirements:

- bounded fixed local work; no whole-world plate raster/graph is required;
- deterministic by seed/revision/definition/coordinates;
- exact order/cache/thread independence;
- no camera input;
- no mountain/rift/river/lake painting in Stage 5;
- existing accepted macro elevation remains unchanged unless a separately reviewed Stage 5 semantic change is explicitly justified.

This preparation exists so Stage 6 can consume a geophysical cause instead of inventing mountain placement from decorative noise.

---

# Stage 6 — Continuous Surface Evolution Prototype

## Goal

Produce a convincing continuous Terrain surface from Stage 5 causes while remaining lazy, deterministic and independent of technical pages/camera.

The required visual target is simultaneous quality at three scales:

1. **country/continental** — coherent land/ocean shape, broad plains/plateaus/basins and readable major mountain systems;
2. **regional** — several Songs-of-Syx-scale landscape regions with believable relationships between coast, plains, mountains and uplands;
3. **local/cell-near** — balanced irregular terrain with no one-block Z chatter and enough detail to inspect the future cell projection.

## Build 6A — regional structural causes

Stage 6 first converts Stage 5 geophysical causes into explicit geomorphic structures:

- mountain/orogenic belts as finite connected corridors with orientation, length, varying width, asymmetry and uplift;
- ridge-family children, foothill envelopes, passes/branching only as descendants of belts;
- plateaus and escarpment transitions;
- broad uplands/lowlands/basins;
- calm plains as a common valid state;
- bounded coast/margin refinement where Stage 5 macro support leaves room for finer geometry.

Major mountains are **not** independent circular/elliptical spots and are not created by thresholding a global ridged-noise field.

## Build 6B — local morphology, informed by accepted V12 strengths

The retired dense V12 architecture remains retired, but its successful local terrain ideas are deliberately reused as hidden algorithms/policies:

- balanced explicit hills and depressions;
- rolling local relief as a subordinate layer;
- local feature scale expressed in terrain/world-cell units rather than as a fraction of total world size;
- sparse/local features rather than ubiquitous equal-amplitude noise;
- bounded gradient/curvature policy preventing alternating one-block Z changes;
- deterministic local synthesis independent of camera/query order.

Useful V13 elongated/asymmetric profile mathematics may inform individual ridge children, but V13-style independent mountain spots do not define global mountain geography.

The local synthesizer receives the regional structure as context. It does **not** decide where the world's mountain belts, plateaus or basins are.

## Continuous truth

`world/terrain` owns the continuous physical surface. Stage 6 must expose a narrow coordinate/request capability that later Stage 7 and Stage 10 consume.

The same coordinate has one Stage 6 surface value independent of:

- page/tile boundary;
- cache eviction;
- query order;
- render zoom;
- camera position;
- thread scheduling.

## Anti-one-block requirement

Stage 6 must make future integer XYZ projection readable at cell scale.

Automated properties must cover at least:

- adjacent slope bounds;
- second-difference/curvature bounds where applicable;
- low density of alternating integer-Z crossings;
- no isolated single-cell peaks/pits caused by the generator;
- no checkerboard/corner-supported height pattern;
- seam-safe local relaxation/slope control if an explicit relaxation pass is used.

A dense whole-world relaxation pass is forbidden. Any local relaxation must use deterministic overlap/halo semantics so independently requested windows agree exactly.

## Visualization requirement

F2 must be developed together with Stage 6, not after it.

Required layers include:

- Stage 5 macro support;
- Stage 5 structural context;
- Stage 6 mountain belts / plateaus / basins;
- ridge/local-landform causes;
- final continuous Z;
- projected future integer-cell heights.

Normal pan/zoom must not expose incomplete technical LOD as geography. The renderer may use clipmap-like nested resident levels, overscan, bounded pages and asynchronous preparation, but expensive generation may not block the render thread.

## Acceptance

- deterministic/seam/property gates green;
- requested work bounded by requested area/detail, not total logical world;
- multiple fixed seeds inspected at macro/regional/local scales;
- mountains read as connected systems at far scale and retain believable local morphology close-up;
- plains/coasts/plateaus are readable without rivers/forests hiding the terrain;
- explicit manual acceptance before Stage 7.

---

# Stage 7 — Genesis Drainage + Depression Topology

## Goal

Analyze the accepted Stage 6 surface and create deterministic **topology**, not runtime water physics.

## Build

- local drainage direction/graph over the continuous/requested surface;
- watershed/basin hierarchy;
- accumulated upstream support/area suitable for ranking future channels;
- depression detection;
- nested depression hierarchy;
- saddles/spill points and outflow relationships;
- connection toward ocean/outlet where applicable.

A method such as D-infinity-style flow direction, Priority-Flood/depression hierarchy or another replaceable solver may be used behind the semantic contract.

## Boundary

Stage 7 does not:

- fill the world with runtime Liquid;
- simulate rainfall history;
- simulate real erosion over years;
- mutate Terrain as a runtime process;
- paint rivers/lakes independently from drainage topology.

Its output is deterministic Genesis topology for Stage 8.

---

# Stage 8 — Coupled Rivers, Lakes and Surface

## Goal

Turn Stage 7 topology into coherent initial-world river/lake geometry and reconcile that geometry with Terrain.

## Build

- channel hierarchy/centerlines selected from drainage accumulation/topology;
- channel width/depth intent derived from upstream support and later-compatible hydrologic semantics;
- lake basins derived from meaningful depressions and spill hierarchy;
- river-to-lake and river-to-ocean connectivity;
- bounded valley/channel/lake-basin shaping of the Stage 6 surface;
- a small fixed number of deterministic reconciliation passes if drainage must be recalculated after surface adjustment.

## Abstract geomorphic solvers are allowed

Stage 8 may use stream-power-like, erosion-like, smoothing or relaxation equations **as numerical Genesis construction tools**.

For example:

```text
initial surface
  -> drainage topology
  -> select major channels
  -> bounded channel/valley adjustment
  -> recompute affected drainage
  -> final Genesis surface + channel/lake topology
```

Those iterations are not simulated years and create no runtime erosion history.

## Explicitly postponed

Real-time/long-time water erosion, landslides, sediment transport and direct terrain modification belong to later Runtime mechanics and are not prerequisites for initial rivers/lakes.

---

# Stage 9 — Hierarchical Regional Refinement + Seam Proof

## Goal

Prove that the accepted Stage 5–8 world can be reconstructed/refined locally at large scale without full-world materialization or seams.

## Required proof

- a query refines only intersecting structural/natural causes;
- overlapping requests return exactly identical shared structures and Terrain values;
- query order/cache history/thread order cannot change results;
- eviction/rematerialization reproduces the same natural world;
- work/memory scale with requested area/detail and active cache, not total logical world;
- natural features own continuity; technical pages never become geography;
- representative worlds large enough to contain hundreds of regional landscape areas remain browsable/materializable with bounded working set.

---

# Stage 10 — Exact XYZ Materialization

## Goal

Convert the accepted continuous Terrain surface into exact integer XYZ Terrain facts only where exact cells are required.

## Build/proof

- exact authoritative integer Terrain owner/capability;
- deterministic projection/materialization from the accepted Stage 8/9 surface;
- projected Stage 6 cell diagnostics and exact Stage 10 cells agree where the projection contract says they should;
- no one-block generator chatter introduced by quantization;
- exact cells are materialized only for requested/active physical regions;
- eviction of rebuildable Genesis materialization does not destroy world truth;
- later runtime mutation can distinguish reconstructable Genesis cells from authoritative changed cells.

---

# Later runtime terrain mutation and persistence

Genesis terrain is reconstructable; current runtime Terrain is authoritative.

When future mechanics such as digging, construction, landslides or real water erosion exist, the conceptual current state is:

```text
reconstructable Genesis terrain
        +
authoritative persistent changes
        =
current Terrain truth
```

The exact sparse delta/voxel/persistence representation is intentionally postponed until runtime mutation and Stage 17 persistence work require it. The architectural requirement is that storage cost follows actually changed world state rather than total potential world area.

Runtime erosion is therefore **not** smuggled into Stage 7/8 Genesis generation.

---

# Simulation object scale: observer independence

World generation and later ecology/agents share one non-negotiable law:

> observation may decide what is rendered or cached, but not which physical/behavioral laws an existing entity follows.

Future object-count optimization may use data-oriented packed storage, exact elapsed-time advancement, wake/event scheduling, batching and sparse indexes. It may not turn a distant individual animal into a different statistical simulation merely because no camera is nearby.

A concept may be a field/aggregate by its actual ontology (for example grass biomass) rather than one object per microscopic element. That is a semantic modeling decision, not a visibility LOD.

---

# Stage discipline

The rejected PR #136 is archive-only. Replacement work starts from accepted Stage 5 plus the separately reviewed Stage 5 structural-preparation contract.

The immediate order is:

```text
Roadmap/ADR reconciliation
        ↓
Stage 5 structural-geophysics preparation PR
        ↓
Stage 6 regional structures
        ↓
Stage 6 V12-informed local surface
        ↓
Stage 6 world-to-cell F2 acceptance
        ↓
Stage 7 drainage/depression topology
        ↓
Stage 8 Genesis rivers/lakes/surface reconciliation
        ↓
Stage 9 lazy regional seam/scale proof
        ↓
Stage 10 exact XYZ
```

Every checkpoint requires its own contract, tests, scale evidence where material, canonical documentation and explicit manual acceptance where visual/spatial quality matters.

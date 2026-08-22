# Continuum World Development Plan

This is the canonical executable roadmap for EvoForge Continuum/world generation.

**Stage numbers are immutable.** PR order, implementation accidents or previously completed support work never renumber stages. Development proceeds one stage at a time and the next stage starts only after manual acceptance of the current one.

## Fixed architectural laws

1. One continuous XYZ world; shared coordinates do not imply a giant shared `WorldCell`.
2. No global `WorldFact` store. Each authoritative domain owns its state.
3. Pages/chunks/tiles are technical representation only, never natural geography.
4. Camera/presentation never controls simulation truth or generation semantics.
5. Same seed/version/definitions/coordinates reproduce the same Genesis facts.
6. Query order, cache history and thread order cannot move generated facts.
7. Genesis and mutable Runtime ownership are separate.
8. Continuous/high-precision terrain is formed before exact integer XYZ materialization.
9. Rivers, lakes, coasts, valleys and ecological labels are consequences of causes, not independent painters.
10. Definitions remain semantic and human-readable; numerical solver tuning stays inside implementations unless it is true content semantics.
11. Runtime cost depends on active/requested work, not total logical world area.
12. World age alone cannot increase tick cost, scheduler size or RAM.
13. Cross-domain mutation uses explicit coupling/transfer; one mutable state has one owner.
14. No V16/V17/V18 whole-generator lineage.

## Mandatory gate for every stage

A stage is complete only after the **applicable** correctness, property, determinism, order-independence, seam, replaceability, performance, scale, visualization and documentation gates pass.

Tests and performance evidence are part of implementation, not later cleanup.

**Visualization is required only when it genuinely helps inspect world/spatial behavior.** Do not create dashboards or artificial screens merely to satisfy a gate. Internal infrastructure such as scheduling, compaction and cache bookkeeping is proved by tests, profiles and diagnostics.

The long-term `F2` Inspector is a real world viewer. Once landscape exists, it must provide a clear **2D map mode and 3D terrain mode**, free pan/zoom/navigation, understandable display settings, and switchable diagnostic layers. It should recover the useful inspection capability of the old visualizer without reintroducing the old world-generation architecture. Later runtime time controls belong on this real world view when mutable world state exists.

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
- **Stage 4 — CURRENT.** Map / Zoom Performance Proof; draft PR #125, awaiting exact-head gates and manual inspection.
- **Stage 5+ — not started.**

---

# Stage 1 — Local Query + Shared Region Cache

## Goal

A consumer receives only the local data it needs, while overlapping consumers reuse shared expensive work.

In plain language: if ten objects need almost the same place, the world reads/calculates that common place once and then gives each object only its own requested slice.

## Accepted result

- local bounded requests;
- shared technical region work;
- batching and deduplication;
- concurrent single-flight materialization;
- revision/invalidation;
- consumer-local immutable views;
- bounded shared cache;
- 1 / 10 / 100 consumer performance proof;
- understandable spatial F2 visualization.

**Status:** complete.

---

# Stage 2 — Infinite-Time Foundation

## Goal

The world must not become more expensive merely because it is older.

In plain language: if nothing meaningful happened for a million years, the engine must not replay a million years of tiny ticks just to discover that fact. It stores the current state and the future work that still matters.

## Accepted result

- exact long-horizon integer time;
- sleeping-process wake scheduling;
- one current wake obligation per sleeping process;
- elapsed-time fast-forward contract;
- scheduler cleanup and safe handle reuse;
- bounded in-memory checkpoint + delta-tail compaction;
- longevity tests and young-vs-ancient scale profile;
- no fake Stage 2 dashboard; `F2` stays world-oriented.

**Status:** complete.

---

# Stage 3 — Multi-Resolution Continuum

## Goal

Read the same logical world directly at different spatial resolutions without generating exact detail first.

In plain language: when we look from far away, we ask the world for fewer samples covering a larger area. When we zoom closer, we ask for finer samples. Both are views of the same world, not two different worlds.

## Accepted result

- nested resolution levels with `step = 1, 2, 4, 8...`;
- resolution-aware page layout;
- direct coarse materialization from the same deterministic coordinate field;
- shared coarse/fine coordinates return the same value;
- page payload/sample count stays bounded while covered world area grows;
- query order and cache eviction/rematerialization do not change results;
- visual zoom remains presentation-only and does not change world truth;
- Stage 3 Inspector was manually accepted by the user;
- after Stage 1 and Stage 2 integration, the full Gradle suite and scale profile still pass.

**Status:** complete.

---

# Stage 4 — Map / Zoom Performance Proof

## Goal

Browsing an enormous logical world must cost roughly what the current screen needs, not what the whole world contains.

In plain language: the map loads a small set of tiles around the camera. Moving slightly reuses most of them. Zooming chooses a coarser or finer map representation automatically. If new detail is not ready, an already-ready coarse parent temporarily fills that area instead of showing a blank square.

## Build

- derived map tile keys include resolution, coordinates and source revision;
- replaceable tile generator over the existing deterministic Continuum field;
- direct map-tile generation at the requested resolution, never exact-detail generation followed by downsampling;
- bounded asynchronous CPU tile cache;
- single-flight generation for identical concurrent requests;
- bounded outstanding async work during rapid camera movement;
- parent fallback while finer detail is pending;
- viewport-driven automatic LOD selection;
- one small prefetch ring around the visible viewport;
- stale-revision rejection;
- bounded render/GPU resource cache;
- world-oriented `F2` map with mouse drag and wheel zoom;
- technical tile diagnostics hidden by default and toggleable with `G`.

## Required proof

- identical simultaneous misses perform one tile generation;
- pending detail still has a valid coarse visual fallback;
- rapid pan cannot grow ready tiles or pending jobs without bound;
- changing source revision cannot display an old tile as current;
- zoom preserves the logical world coordinate under the cursor;
- zoom-in requests a finer map level;
- small pan reuses most existing tiles;
- camera movement never changes source revision/world truth;
- render resources are LRU-bounded and disposed on eviction.

## Performance

The Stage 4 scale profile runs the same 1600×900 pan/zoom workload on progressively larger logical worlds.

Initial GitHub-runner evidence:

| Logical side | Visible tiles | CPU tiles after stress | CPU payload |
|---:|---:|---:|---:|
| 1,000,000 | 16 | 81 | 1,327,104 bytes |
| 100,000,000 | 36 | 37 | 606,208 bytes |
| 1,000,000,000 | 16 | 81 | 1,327,104 bytes |

The exact timings are diagnostic only. The hard architectural result is that viewport working set stays bounded instead of scaling with total logical area.

## Visual acceptance

`F2` opens a map-like synthetic Continuum field.

Controls:

```text
Left mouse drag  pan
Mouse wheel      zoom around cursor
Home             fit whole logical world
G                toggle tile/cache diagnostics
Esc              back
```

No blank holes should appear while requested detail is loading. With `G` enabled, green borders mean requested detail is ready; orange borders mean a coarser parent is temporarily filling that tile.

This is still synthetic diagnostic data. Real geography starts at Stage 5.

## Boundary

Stage 4 is presentation/query infrastructure only. It does not add continents, ocean, tectonics, relief, rivers, lakes, climate or simulation LOD. Camera state never becomes authoritative world state.

## Done when

Gradle tests, Docs Site and Continuum Scale Profile are green on the exact PR head, the `F2` map is understandable and smooth enough for manual inspection, and the user accepts Stage 4.

---

## Stage discipline

After Stage 4 passes automated gates and manual inspection, stop. Do not start **Stage 5 — Macro Ocean + Geophysical Skeleton** until the user explicitly accepts Stage 4 and asks to continue.

# Continuum World Development Plan

## Purpose

This is the canonical executable roadmap for EvoForge Continuum/world generation. It replaces the older phase numbering that predated the current bounded page/cache/performance/inspection foundation.

Development is strictly sequential: **one stage is completed, reviewed and accepted before the next stage begins.** A later stage may not be started merely because its algorithm is interesting.

The plan is architectural rather than algorithm-lineage based. Implementations are replaceable; `[FIXED]` laws below are not.

---

# Fixed laws

1. One continuous XYZ world; shared coordinates do not imply a giant shared `WorldCell`.
2. No global `WorldFact` store. Each authoritative domain owns its state.
3. Pages/chunks/tiles are technical representation only, never natural geography.
4. Camera and presentation never control simulation truth or generation semantics.
5. Same seed/version/definitions/coordinates must reproduce the same Genesis facts.
6. Query order, cache history and thread order must not move generated facts.
7. Genesis and mutable Runtime ownership are separate.
8. Continuous/high-precision terrain is formed before exact integer XYZ materialization.
9. Rivers, lakes, coasts, valleys and ecological labels are consequences of causes, not independent painters.
10. Definitions remain semantic and human-readable; solver tuning stays inside implementations unless truly semantic.
11. Runtime cost must depend on active/requested work, not total logical world area.
12. World age alone must not increase tick cost, scheduler size or RAM.
13. Cross-domain mutation uses explicit coupling/transfer; one mutable state has one owner.
14. No V16/V17/V18 whole-generator lineage.

---

# Mandatory gate for every stage

A stage is complete only after applicable gates pass:

- **Correctness** — unit/integration tests.
- **Properties** — semantic/physical invariants.
- **Determinism** — same input gives same result.
- **Order independence** — query/thread order cannot change truth.
- **Seams** — technical boundaries are invisible in authoritative results.
- **Replaceability** — consumers do not depend on private algorithm type where a real seam exists.
- **Performance** — measured time, work count, allocations/heap diagnostics and resident bytes where relevant.
- **Scale** — large logical domain tests, including 1M where no full-area materialization is required.
- **Visual inspection** — spatial results are inspectable through production contracts.
- **Documentation** — current state and limitations are recorded in the same PR.

A visually attractive result that fails scale/determinism is rejected. A fast result that is spatially wrong is also rejected.

---

# Current execution status

| Stage | Status | Accepted work |
|---|---|---|
| 0 — Clean Continuum baseline | ✅ merged | legacy generated-world line removed; clean coordinate-addressed foundation |
| 1 — Bounded technical paging/cache | ✅ merged | PR #118 |
| 2 — Scale/performance + inspection harness | ✅ merged | PR #119 performance gate, PR #120 F2 page/cache inspector |
| 3 — Multi-Resolution Continuum | 🟡 active | PR #121, awaiting manual acceptance |
| 4+ | ⛔ not started | do not begin until Stage 3 is accepted and merged |

---

# Stage 0 — Clean Continuum baseline

## Goal

Remove dependency on the old dense V12–V15 generated-world pipeline and retain only neutral Continuum primitives.

## Required result

- long logical coordinates;
- deterministic coordinate-addressed sampling;
- bounded materialization windows;
- no mandatory full-world array;
- stale worldgen documentation and assets removed or moved to valid domain ownership.

**Status:** complete.

---

# Stage 1 — Bounded technical paging/cache

## Goal

Prove that a huge logical world can materialize only requested technical pages with hard residency limits.

## Required result

- configurable page dimensions;
- global coordinate ↔ technical page mapping;
- bounded LRU page cache;
- explicit page and payload-byte budgets;
- eviction/rematerialization equality;
- cache metrics.

## Acceptance

Same active working set on 10k / 100k / 1M logical worlds keeps the same resident page/payload budget.

**Status:** complete.

---

# Stage 2 — Scale/performance + inspection harness

## Goal

Make scale regressions measurable and Continuum representation visually inspectable before geography exists.

## Required result

- automated `Continuum Scale Profile` CI workflow;
- 10k / 100k / 1M repeatable workload;
- cold/warm/cache metrics;
- F2 Continuum Inspector;
- requested/resident/evicted page overlays;
- pan and presentation-only zoom;
- seed/world-coordinate/cache telemetry.

**Status:** complete.

---

# Stage 3 — Multi-Resolution Continuum

## Goal

Allow the **same logical world** to be queried directly at several nested sampling scales without first generating exact detail.

## Representation

The current proof hierarchy is a replaceable power-of-two lattice:

```text
L0 step 1
L1 step 2
L2 step 4
L3 step 8
...
```

This is query representation policy, not simulation LOD and not geography semantics.

## Required result

- explicit resolution object;
- resolution-aware page layout;
- coarse pages keep bounded sample count while covering a larger world span;
- coarse coordinates are nested in finer grids;
- shared coordinates return exactly the same authoritative value;
- coarse cache eviction/rematerialization remains invisible;
- 1M logical-world performance proof;
- Inspector exposes sampling resolution independently from drawing zoom.

## Acceptance

- L0 behavior remains compatible;
- L0/L5/L10 direct materialization uses work proportional to requested samples, not covered exact area;
- query order cannot change a coarse page;
- manual F2 inspection confirms logical focus stays stable while `PageDown/PageUp` changes resolution.

**Status:** active in PR #121. Stop here until manual acceptance.

---

# Stage 4 — Local Query + Shared Region Work

## Goal

Give consumers only local information while overlapping consumers reuse common expensive work.

## Build

- local region requests;
- shared immutable regional backing results;
- batching/deduplication;
- single-flight in-progress computation;
- revision/invalidation;
- consumer-specific local views/filters.

## Key proof

Ten or one hundred consumers requesting strongly overlapping areas must not cause ten or one hundred duplicate regional computations.

## Performance

Measure queries, unique regions, cache hits/misses, duplicate work prevented, allocations and latency.

---

# Stage 5 — Infinite-Time Foundation

## Goal

World age must not itself increase cost.

## Build

- robust simulation-time representation;
- active/sleeping process scheduling;
- wake-up causes;
- elapsed-time fast-forward where mathematically valid;
- snapshots/checkpoints;
- delta compaction;
- stale-event cleanup.

## Key invariant

Equivalent current state at year 10 and year 1,000,000 should have comparable RAM/tick cost.

---

# Stage 6 — Map/Zoom Presentation Pyramid

## Goal

Produce smooth pan/zoom over huge worlds using direct multi-resolution data.

## Build

- tile pyramid / clipmap-like presentation cache;
- parent fallback;
- asynchronous refinement;
- single-flight tile jobs;
- bounded CPU/GPU caches;
- selective prefetch.

Camera remains presentation only.

---

# Stage 7 — Macro Ocean + Geophysical Skeleton

## Goal

Create the first real Genesis geography without dense full-resolution terrain.

## Required causes

- global sea level;
- dominant connected world ocean;
- oceanic/continental structural foundation;
- large geophysical regions;
- uplift/subsidence tendencies.

No detailed terrain cells yet.

## Visual acceptance

Many deterministic seeds must show plausible large-scale land/ocean structure without cell-scale noise or round blob geography.

---

# Stage 8 — Continuous Surface Evolution

## Goal

Produce natural continuous relief from causal processes.

## Candidate processes

- uplift/subsidence;
- hillslope smoothing/diffusion;
- drainage-driven erosion;
- sediment movement/deposition.

High-frequency per-cell height noise is forbidden as the main surface source.

---

# Stage 9 — Drainage + Depression Topology

## Goal

Determine where water goes before final rivers/lakes are represented.

## Build

- flow routing;
- accumulation;
- watersheds;
- natural depressions;
- spill points;
- depression hierarchy.

Natural depressions are not automatically destroyed just to simplify routing.

---

# Stage 10 — Coupled Rivers, Lakes and Surface

## Goal

Let surface and Genesis hydrology co-evolve.

```text
surface → drainage → flow → erosion/deposition → surface → ...
```

Derive rivers, tributaries, valleys, lake basins, lake levels, spill/overflow and coasts. Depression-Hierarchy / Fill–Spill–Merge style lake handling is the leading candidate but remains replaceable.

---

# Stage 11 — Hierarchical Regional Refinement + Seam Proof

## Goal

Generate arbitrary detailed regions without generating the whole world.

Parent levels provide mandatory constraints such as ridge crossings, river entry/exit, basin membership and sea boundaries. Child refinement adds detail but cannot violate them.

Generate A→B, B→A, concurrent A/B, evict/reload: results must agree.

---

# Stage 12 — Exact XYZ Materialization

## Goal

Convert accepted continuous terrain into exact EvoForge integer XYZ.

## Quality gates

Track isolated +1/-1 peaks/pits, tiny contour bands, slope/curvature anomalies and boundary equality. The exact block surface must faithfully represent the smooth source rather than reintroduce zebra-like one-Z noise.

---

# Stage 13 — Climate Coupling

## Goal

Replace synthetic rainfall with scale-appropriate climate causes:

- temperature;
- ocean moisture source;
- moisture transport;
- elevation/orographic precipitation;
- continental drying;
- rain shadow.

Climate works at its natural numerical scale, not exact XYZ everywhere.

---

# Stage 14 — Detailed Geology, Sediment and Soil

## Goal

Make surface materials consequences of geological properties, erosion, transport, deposition, water and climate.

Do not paint materials from biome labels.

---

# Stage 15 — Genesis → Runtime Handoff

## Goal

Transfer initial state to ordinary authoritative owners (Landscape, Water, Soil, etc.). Genesis stops owning mutable runtime state.

Runtime changes must never be overwritten by later rematerialization.

---

# Stage 16 — Generic Runtime Coupling + Flux

## Goal

Allow independently owned systems to interact through explicit read/compute/couple/commit flow and agreed transfers of water, sediment, heat or other conserved quantities.

No central MegaPhysics owner.

---

# Stage 17 — Runtime Surface Water

## Goal

Support long rivers/oceans without updating every exact water cell every tick.

Candidate representations:

- steady compact river reaches;
- stable lake storage/level;
- sleeping ocean regions;
- detailed local hydraulic patches only when dynamics require them.

Stable water must remain numerically stable and cheap.

---

# Stage 18 — Surface + Soil + Subsurface Water

## Goal

Couple surface flow, infiltration, soil saturation and groundwater while preserving separate ownership and appropriate spatial/time scales.

---

# Stage 19 — Persistence + Long-Time Proof

## Goal

Prove effectively unbounded world age using compact current state, checkpoints, delta compaction and safe eviction/restoration.

Required stress cases include long-sleep lakes/rivers, huge time jumps, many scheduler events, repeated edits + compaction, save/load and return after eviction.

---

# Stage 20 — Underground World

Optional independent modules: geological volumes, faults/fractures, caves, karst, groundwater extensions and ore/resource structures. Surface generation must still work if this entire stage is absent.

---

# Stage 21 — Ecology / Derived Environment

Derive ecological suitability from temperature, water, soil, terrain and other physical conditions. Biome/ecoregion names are derived human-readable classifications, not authoritative causes.

---

# Stage 22 — Full System Acceptance

Run permanent deterministic seed suites, large logical random access, rapid pan/zoom, eviction/reload, concurrency, long-time persistence and physical-coupling stress.

Track geography quality metrics such as slope/curvature distributions, one-Z anomaly rate, watershed/river/lake statistics, coastline structure and conservation balances.

---

# Permanent Inspector rule

Inspector overlays grow with the project: requests/cache, resolution, ocean/geophysics, elevation, erosion/deposition, drainage, watersheds, depressions/spills, rivers/lakes, climate, geology/sediment/soil, exact XYZ, runtime water, couplings, scheduler and revisions.

The long-term inspection question is:

> **Why is this location/state like this?**

The answer should expose causal inputs rather than only a final color.

---

# Anti-patterns

Do not reintroduce:

- global `WorldFact`;
- giant `WorldCell`;
- full-world dense authoritative arrays;
- per-consumer duplicate regional work;
- camera-controlled simulation;
- exact world generation followed by downsampling for overview;
- cell-frequency elevation noise as terrain cause;
- standalone random Mountain/River/Lake painters;
- fill/destroy every depression;
- finished terrain followed by destructive river carving;
- chunk-owned natural features;
- one system mutating another owner's state directly;
- every water cell updated every tick;
- unbounded caches/event history/scheduler history;
- persistence growing merely with elapsed ticks;
- numeric whole-generator lineage.

---

# Future-chat handoff

Use:

> Continue EvoForge Continuum from **Stage X — [name]**. Inspect current repository/PR state first. Implement only that stage. Keep all fixed laws. Do not start Stage X+1 until I manually accept Stage X.

Current handoff while PR #121 is open:

> **Stage 3 — Multi-Resolution Continuum: automated gates green; manual F2 inspection pending.**

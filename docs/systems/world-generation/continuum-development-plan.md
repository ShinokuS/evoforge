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

A stage is complete only after applicable correctness, property, determinism, order-independence, seam, replaceability, performance, scale, visual-inspection and documentation gates pass.

Tests and performance evidence are part of implementation, not later cleanup. Spatial output must be understandable in the Inspector where useful.

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
- **Stage 1 — CURRENT.** Complete it before Stage 2.
- **Stage 2 — not started.**
- **Stage 3 — multi-resolution support already exists from earlier work.** It remains useful, but does not permit skipping unfinished Stage 1 or Stage 2.
- **Stage 4+ — not started.**

---

# Stage 1 — Local Query + Shared Region Cache

## Goal

A consumer receives only the local data it needs, while overlapping consumers reuse shared expensive work.

In plain language: if ten objects need almost the same place, the world reads/calculates that common place once and then gives each object only its own requested slice.

## Build

Introduce only the concrete concepts needed for this proof:

- spatial region keys;
- local query requests;
- read-only regional results;
- bounded regional cache;
- request batching;
- deduplication;
- single-flight in-progress computation;
- revision/invalidation;
- consumer-local views.

Do **not** create a global `WorldFact` object or a universal query framework for every future subsystem.

## Required proof

Run the same workload with:

- 1 consumer;
- 10 consumers with strongly overlapping requests;
- 100 consumers with strongly overlapping requests;
- consumers in unrelated areas.

Expensive regional work must track **unique required regions**, not consumer count.

## Tests

- same region requested N times -> one computation;
- overlap reuses shared backing data;
- a consumer cannot read outside its requested area;
- stale revision is never accepted as current;
- eviction + reload returns the correct result;
- request order cannot change values;
- the implementation can be substituted behind its real seam.

## Performance

Measure at least:

- requests;
- unique regional computations;
- reused requests / duplicate work prevented;
- cache hits/misses;
- resident pages/payload;
- latency;
- heap/allocation diagnostics where reliable.

Use a large logical domain so the proof cannot accidentally rely on full-world allocation.

## Visual acceptance

The Inspector must explain the mechanism without requiring knowledge of internal class names. It should clearly distinguish:

- **consumer request** — the area one object/system asks for;
- **shared region** — the common technical region calculated once;
- **reused work** — requests served from an already calculated region;
- **local result** — only the data returned to that consumer.

A simple 1 / 10 / 100-consumer demonstration should make the benefit visible.

## Done when

A huge logical world serves many overlapping local requests with bounded memory and without duplicated expensive regional work, and the behavior is understandable in the Inspector.

---

## Stage discipline

After Stage 1 passes automated gates and manual inspection, stop. Stage 2 starts only after explicit user acceptance.

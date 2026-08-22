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

Tests and performance evidence are part of implementation, not later cleanup. Spatial or temporal behavior must be understandable in the Inspector where useful.

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
- **Stage 2 — CURRENT.** Infinite-Time Foundation is implemented in draft PR #123 and awaits automated/manual acceptance.
- **Stage 3 — multi-resolution support already exists from earlier work.** It remains useful, but Stage 2 must still be accepted before proceeding.
- **Stage 4+ — not started.**

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
- understandable F2 visualization.

**Status:** complete.

---

# Stage 2 — Infinite-Time Foundation

## Goal

The world must not become more expensive merely because it is older.

In plain language: if nothing meaningful happened for a million years, the engine must not replay a million years of tiny ticks just to discover that fact. It stores the current state and the future work that still matters.

## Build

- exact long-horizon time representation that does not rely on floating point or one lifetime-limited tick counter;
- sleeping-process wake scheduling;
- one current wake obligation per sleeping process;
- explicit reason for waking;
- elapsed-time transition contract so a process can fast-forward from its last evaluated time to the new time;
- scheduler cleanup so cancelled/completed work is physically removed rather than retained as hidden history;
- safe reuse of scheduler handle slots;
- in-memory checkpoint + bounded recent-delta compaction primitive.

The existing ordinary runtime clock remains compatible. Stage 2 adds the long-horizon foundation rather than forcing unrelated runtime systems through a risky migration in one PR.

## Required proof

- a time value can advance far beyond one signed-long timeline without floating-point drift;
- 100,000 reschedules of one sleeping process retain one wake entry;
- 100,000 schedule/cancel operations retain zero future queue entries and reuse bounded handle slots;
- a huge time jump invokes one elapsed-time transition per due process, not one call per skipped tick;
- one million state changes compact into the current state plus a bounded recent tail;
- equivalent current working sets at a young and astronomically old world age retain the same structural memory counts.

## Performance

The scale profile compares equivalent young and ancient states and records:

- sleeping processes;
- physical wake queue entries;
- generic scheduler pending/physical entries;
- reusable handle slots;
- retained delta tail;
- fast-forward calls;
- elapsed time.

World age alone must not increase those structural counts.

## Visual acceptance

`F2` must explain three things without requiring knowledge of class names:

1. **World age** — young and ancient presets keep the same current working set.
2. **Sleeping work** — a huge jump handles only processes whose wake condition became due; it does not replay every skipped tick.
3. **History kept** — a million changes compact into current state + bounded recent history.

The screen also shows that repeated cancelled scheduler tasks leave zero queued history.

## Boundary

Stage 2 is not the final persistence system. Disk persistence, save/load compaction and full-world long-time stress remain Stage 17. Stage 2 establishes the temporal primitives required to make those later systems possible.

## Done when

Automated tests, Docs Site and Continuum Scale Profile are green, the F2 explanation is understandable, and the user manually accepts Stage 2.

---

## Stage discipline

After Stage 2 passes automated gates and manual inspection, stop. Do not start Stage 4 or geography. Stage 3 already exists from earlier work and is only considered satisfied in sequence after Stage 2 acceptance.

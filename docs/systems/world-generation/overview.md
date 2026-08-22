# World Generation — Continuum

EvoForge world generation is being rebuilt on the **Continuum** architecture. The retired dense V12–V15 generator and World Atlas pipeline are historical work, not the current architecture.

The canonical implementation sequence is fixed in [Continuum World Development Plan](continuum-development-plan.md). Stage numbers are immutable.

## Current position

- Stage 0 — complete.
- Stage 1 — **current work: Local Query + Shared Region Cache**.
- Stage 2 — not started.
- Stage 3 multi-resolution support already exists from earlier work, but it does not permit skipping unfinished canonical stages.
- Stage 4+ — not started.

## What Stage 1 proves

Consumers do not receive an omniscient world object. Each asks for one bounded local area.

When many consumers need overlapping areas:

```text
many local requests
        ↓
find unique technical regions
        ↓
calculate/load each unique region once
        ↓
return only each consumer's requested local slice
```

The expensive shared work scales with unique regions, not with the number of consumers making overlapping requests.

The shared cache remains technical representation only. It is never authoritative world state.

Every query carries a world revision. After the revision changes, old reusable regional representation cannot be returned as current data.

## Existing large-world foundation

The repository already contains useful support proven by tests and profiles:

- large `long` logical coordinates without whole-world allocation;
- deterministic coordinate-addressed sampling;
- bounded technical pages and explicit page/payload cache budgets;
- eviction/rematerialization equality;
- automated 10k / 100k / 1M scale profiling;
- nested multi-resolution sampling from the same logical world.

Those capabilities are infrastructure. They do not change the canonical Stage 0–20 order.

## Architectural laws

1. Logical world size is not resident storage.
2. One continuous XYZ world does not imply one giant mutable `WorldCell`.
3. No global `WorldFact` store; authoritative domains own their own state.
4. Pages/chunks/tiles are technical only.
5. Camera/presentation never controls world truth.
6. Seed/version/definitions/coordinates determine Genesis facts; request order/cache history/thread order must not.
7. Genesis and mutable Runtime ownership are separate.
8. Continuous/high-precision terrain precedes exact XYZ materialization.
9. Rivers, lakes, coasts, valleys and ecological labels are consequences of causes, not independent painters.
10. Definitions remain semantic and solver-independent where possible.
11. Runtime cost follows active/requested work, not total area.
12. World age alone must not increase cost.
13. Cross-domain mutation uses explicit coupling/transfer.
14. No whole-generator V16/V17/V18 lineage.

## Current visual acceptance

Run the desktop visualizer and press `F2`. The current Stage 1 proof shows consumer requests versus unique shared regional calculations in plain language.

See [Stage 1 — Local Query + Shared Region Cache](stage1-local-query.md).

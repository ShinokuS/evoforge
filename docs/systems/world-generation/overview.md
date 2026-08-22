# World Generation — Continuum

EvoForge world generation is being rebuilt on the **Continuum** architecture. The retired dense V12–V15 generator and World Atlas pipeline are historical work, not the current architecture.

The canonical implementation sequence is fixed in [Continuum World Development Plan](continuum-development-plan.md). Stage numbers are immutable.

## Current position

- Stage 0 — complete.
- Stage 1 — complete and manually accepted.
- Stage 2 — complete and manually accepted.
- Stage 3 — complete and manually accepted; revalidated after Stage 1/2 integration.
- Stage 4 — **next: Map / Zoom Performance Proof**.
- Stage 5+ — not started.

## Proven foundation

The repository now has:

- large `long` logical coordinates without whole-world allocation;
- deterministic coordinate-addressed sampling;
- bounded technical pages and explicit page/payload cache budgets;
- local queries with shared regional work;
- long-horizon time, sleeping-process scheduling and bounded history compaction primitives;
- nested multi-resolution sampling from the same logical world;
- automated large-world, multi-resolution and time-longevity scale profiles.

Stage 3 still passes after Stage 1/2 integration: direct L0/L5/L10 coarse queries keep the same bounded sample count and payload while covering progressively larger world areas.

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

## Visualizer rule

`F2` is a world-oriented spatial inspector, not a dashboard collection.

When real landscape appears, the inspector must grow into a useful world-view tool with **2D map and 3D terrain modes**, pan/zoom/navigation, clear settings and switchable diagnostic layers. Presentation controls never change world truth or simulation fidelity.

See [Continuum Development Plan](continuum-development-plan.md).

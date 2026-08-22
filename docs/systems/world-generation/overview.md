# World Generation — Continuum

EvoForge world generation is being rebuilt on the **Continuum** architecture. The retired dense V12–V15 generator and World Atlas pipeline are historical work, not the current architecture.

The canonical implementation sequence is fixed in [Continuum World Development Plan](continuum-development-plan.md). Stage numbers are immutable.

## Current position

- Stage 0 — complete.
- Stage 1 — complete and manually accepted.
- Stage 2 — **current work: Infinite-Time Foundation**.
- Stage 3 multi-resolution support already exists from earlier work, but does not permit skipping Stage 2 acceptance.
- Stage 4+ — not started.

## What Stage 2 proves

World age must not itself create work or retained history.

The current proof establishes:

- exact long-horizon integer time (`era + tick`);
- sleeping processes with one current future wake obligation each;
- huge time jumps that return only due processes rather than replaying every missing tick;
- scheduler cleanup and safe handle reuse so cancelled historical work is not retained;
- bounded in-memory delta compaction (`checkpoint + recent tail`);
- a scale profile comparing equivalent young and astronomically old working sets.

The important rule is:

```text
cost now = current work + bounded current state
not
cost now = all historical ticks and events
```

Final disk persistence, save/load compaction and the full world long-time proof remain Stage 17.

## Existing large-world foundation

The repository already contains useful support proven by tests and profiles:

- large `long` logical coordinates without whole-world allocation;
- deterministic coordinate-addressed sampling;
- bounded technical pages and explicit page/payload cache budgets;
- Stage 1 local queries with shared regional work;
- automated large-world scale profiling;
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

Run the desktop visualizer and press `F2`. The current Stage 2 proof explains world age, sleeping work, huge time jumps and retained history in three panels.

See [Stage 2 — Infinite-Time Foundation](stage2-infinite-time.md).

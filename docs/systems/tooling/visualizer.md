# Visualizer and Developer Inspection Tools

## Purpose

The Visualizer is an observer. It may display simulation or Continuum diagnostics, but it never decides what is physically true.

## Current F2 screen — Stage 2

`F2` currently opens the **Stage 2 infinite-time proof**.

The screen is intentionally divided into three plain-language panels:

1. **WORLD AGE** — compare a young world with an astronomically old world while keeping the same current sleeping working set.
2. **SLEEPING WORK** — jump forward a huge interval and show that only due processes are handled, not every skipped tick.
3. **HISTORY KEPT** — compact one million state changes into the current state plus a bounded recent tail.

It also shows a scheduler churn proof: 10,000 schedule/cancel operations leave zero queued historical tasks and one reusable handle slot.

Controls:

```text
1        young world preset
2        ancient world preset
3        jump 1,000,000,000,000,000 ticks
4        compact 1,000,000 changes
Esc      back
```

## Important boundary

The inspector is a diagnostic consumer of production time/scheduling/compaction contracts. It does not own authoritative world state and does not make simulation decisions.

The long-horizon time proof does not mean final persistence is complete. Save/load persistence and the complete long-time world proof remain canonical Stage 17.

## Previous Continuum inspectors

Earlier accepted Stage 1 local-query and multi-resolution inspector code remains in the repository because those proofs are still useful. During Stage 2, `F2` is deliberately routed to the current Stage 2 proof so manual acceptance always matches the stage being reviewed.

## Runtime observer boundary

Ordinary runtime visualization continues to read production simulation capabilities. Real user actions go through production command/domain paths; presentation does not mutate authoritative owners directly.

## Manual acceptance rule

A stage is not complete merely because tests are green. The current proof must make the architectural behavior understandable without requiring knowledge of internal class names.

See [Stage 2 — Infinite-Time Foundation](../world-generation/stage2-infinite-time.md) and [Continuum Development Plan](../world-generation/continuum-development-plan.md).

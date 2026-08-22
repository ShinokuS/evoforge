# Stage 2 — Infinite-Time Foundation

## In plain language

The world may become extremely old without the engine keeping every old tick or every old event.

The rule is:

```text
cost now = current active/sleeping work + bounded current state
not
cost now = everything that ever happened
```

## Long-horizon time

`SimulationInstant` stores exact integer time as:

```text
era + tick inside era
```

This avoids floating-point drift and avoids making one signed `long` the lifetime of the world. `LongHorizonClock` can advance across era boundaries or jump directly to a later instant.

The existing ordinary runtime clock is not forcibly replaced in this stage. Stage 2 establishes the long-horizon contract without breaking unrelated runtime systems.

## Sleeping processes

A sleeping process stores one current wake obligation:

- process id;
- time it was last evaluated;
- future wake time;
- reason for waking.

Rescheduling the same process **replaces** its previous wake record.

When time jumps forward, only due wake records are returned. The owner can then use `ElapsedTimeTransition` to advance its state from `lastEvaluatedAt` to the new time in one logical transition or another bounded/adaptive method.

The infrastructure does not require replaying every skipped tick.

## Scheduler longevity

The existing `Scheduler` was strengthened:

- cancelled tasks are physically removed from the future queue;
- completed/cancelled handle slots are safely reused;
- old handles cannot cancel a newer task that reused the same numeric slot;
- deterministic same-time ordering is kept by a separate long-horizon insertion sequence.

Therefore retained scheduler structures follow current pending work rather than historical task count.

## Delta compaction

`CompactingStateBuffer` demonstrates the retention rule:

```text
checkpoint + bounded recent delta tail
```

When the tail reaches its limit, those changes are folded into the current checkpoint and the tail is cleared.

This is deliberately an **in-memory foundation**, not final persistence. Save/load format, disk checkpoints and full persistence stress belong to canonical Stage 17.

## Required proofs

Automated tests verify:

- time crosses era boundaries exactly;
- time can advance beyond one signed-long lifetime;
- 100,000 reschedules of one sleeping process retain one wake record;
- huge time jumps create one elapsed interval per due process, not one operation per skipped tick;
- 100,000 schedule/cancel operations retain zero future queue entries and one reusable handle slot;
- stale task handles cannot affect newer reused slots;
- same-time task order remains deterministic after handle reuse;
- one million state changes retain only a bounded delta tail while preserving the correct current state.

The `Continuum Scale Profile` also compares equivalent young and astronomically old states. Their structural working-set counts must match.

## Visualization

Stage 2 deliberately has **no dedicated visual screen**.

There is no meaningful landscape or spatial process to show yet: this stage is scheduler/time/compaction infrastructure. A dashboard full of numbers would only duplicate test output and make the visualizer harder to understand.

`F2` therefore remains the spatial Continuum Inspector. Time controls will be added later to the actual world view when there is a real mutable world whose evolution can be watched.

## Not in Stage 2

- final persistence/save format;
- replayable historical timeline;
- runtime water or groundwater;
- map/zoom pyramid;
- geography;
- Stage 17 full persistence and long-time proof.

Those are later canonical stages and must not be pulled into this one.

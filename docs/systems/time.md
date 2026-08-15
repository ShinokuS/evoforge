# Time and Scheduling

## Purpose

Provide deterministic discrete simulation time and event-driven activation so persistent inactive objects do not require mandatory per-object `update(dt)` work.

## Responsibility split

```text
SimulationClock       authoritative current tick
SimulationTime        read-only tick capability
Scheduler             when work is due + deterministic due ordering
HandlerRegistry       HandlerId -> ScheduledHandler
ProcessScheduler      narrow domain capability: scheduleAfter(delay, processId)
BoundProcessScheduler binds one domain process family to one HandlerId + SimulationTime
SimulationStepper     production definition of one simulation tick
domain process owner  what processId means and what happens when it wakes
```

The core law is:

```text
Scheduler knows WHEN / HANDLER / PROCESS ID.
Domain knows WHAT THE PROCESS MEANS.
```

Scheduler must not become a central switch over simulation mechanics.

## Clock boundary

`SimulationClock` is mutable authoritative simulation time. `SimulationTime` exposes only `tick()` to consumers that may observe but not advance time.

Wall-clock time, renderer FPS and monitor refresh rate do not define authoritative mechanics. Presentation game speed changes how many simulation ticks are advanced per real second; it does not mutate MovementRate, Growth rates or other domain data.

## Scheduled work

Conceptually a scheduled task carries:

```text
when
HandlerId
processId
TaskHandle
stable ordering identity
```

`processId` is opaque to Scheduler. The pair `HandlerId + processId` routes an activation without a global enum of all gameplay process types.

A domain process family normally registers one handler, not one handler per object/action. Thousands of Movement actions, Need progression processes or Growth processes can share their respective family handler while carrying distinct domain ids.

## Domain process identity versus TaskHandle

These identities stay separate:

```text
domain process id   semantic identity owned by mechanic
TaskHandle          infrastructure identity of one scheduled activation
```

One domain process may eventually schedule multiple activations; cancellation of one task does not inherently redefine domain-process identity.

Current route-level `MoveTo` cancellation deliberately does **not** cancel an already scheduled atomic Movement task: the current edge may finish, no next edge starts, and route ownership is released afterward. `MovementStateStore` therefore still does not retain the Scheduler `TaskHandle` for early mid-edge cancellation. A future mechanic that genuinely needs atomic task cancellation must own that lifecycle explicitly.

## Narrow relative scheduling

A domain start system normally receives:

```java
ProcessScheduler.scheduleAfter(long delayTicks, long processId)
```

rather than raw Scheduler, HandlerId and mutable clock authority.

`BoundProcessScheduler` reads current `SimulationTime`, calculates the absolute due tick and delegates using its already-bound HandlerId. A mechanic can therefore say “wake my process 17 after 10 ticks” without choosing another domain's handler or advancing time.

## Production tick semantics

`SimulationStepper` owns the current one-tick phase order:

```text
1. SimulationClock.advance()
2. Scheduler.dispatchDue(clock.tick())
```

Scenario fixtures and presentation drive this production operation; they do not invent parallel tick semantics.

Therefore advancing ten ticks is semantically ten calls to the same production step and must match ten individual advances.

## Same-tick dispatch

A dispatch processes a deterministic snapshot batch of work that was due when dispatch began. A handler scheduling more work for the current tick does not cause unbounded recursive draining inside that same batch.

Movement additionally enforces duration of at least one tick, so an adjacent edge never schedules its own completion for the tick in which it starts. A committed child Movement completion may synchronously let MoveTo request the next edge, but that next edge's scheduled completion is still at least one later tick.

A future mechanic requiring repeated scheduled same-tick activation must explicitly justify a phase-policy change rather than rely on accidental recursion.

## Deterministic ordering

Tasks due at the same simulation time have stable explicit ordering by scheduled time/task identity rather than map/heap iteration or thread timing.

This ordering is already observable in current same-tick process composition such as Movement/Occupancy contention, Need/Growth progression and environment schedules. Domain semantics must not rely on accidental collection order.

Where two periodic environmental processes need ordering-independent semantics, the contract is encoded explicitly; current precipitation/evaporation composition, for example, suppresses evaporation on a precipitation tick rather than depending on which handler happens to dispatch first.

## Domain actions are not Scheduler state

A scheduled task is infrastructure. A domain action/process remains domain runtime state.

Examples:

```text
MovementAction      object/source/destination semantics in MovementStateStore
Need progression    Need-specific process state/definition
Growth              stock-replenishment process state/trace
Water flow          active hydraulic frontier + one process continuation
ScheduledTask       due time/routing in Scheduler
```

Crafting/combat/construction should follow the same ownership rule if they later become real mechanics instead of being forced into one universal Action/Scheduler state model.

## Event-driven activity

The intended scale model is:

```text
persistent objects may remain inactive indefinitely
only active causal processes schedule work
Scheduler wakes due process ids
domain handler resumes authoritative domain state
```

Water flow also demonstrates sparse self-dormancy: once the active frontier reaches a fixed point, no continuing WaterFlow task remains until a Water mutation wakes it again.

CPU cost can therefore correlate with active processes rather than total persistent object count. Concrete allocation/storage optimization remains workload-driven.

## Diagnostics and tests

Coverage includes clock advancement, handler registration, deterministic due ordering, task identity/cancellation infrastructure, bound relative scheduling, SimulationStepper phase order, scheduled Movement completion, route continuation/cancellation behavior and batched-versus-individual tick equivalence.

Domain integration tests use real production stepping rather than manually calling scheduled handlers at arbitrary times.

## Deferred

- authoritative RNG-stream ownership when a mechanic requires stateful randomness rather than current deterministic hash variation;
- mid-action TaskHandle retention/cancellation when a real death/stun/replacement consumer requires it;
- background scheduling and multithreaded authoritative mutation;
- any phase-model change required by a demonstrated same-tick consumer.

Those changes must preserve explicit deterministic ordering or deliberately revise it as an architectural decision.

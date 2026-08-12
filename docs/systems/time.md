# Time and Scheduling

## Purpose

Provide deterministic discrete simulation time and event-driven activation so persistent inactive objects do not require mandatory per-object `update(dt)` work.

## Responsibility split

```text
SimulationClock       authoritative current tick
SimulationTime        read-only tick capability
Scheduler             when work is due + deterministic due ordering
HandlerRegistry       HandlerId → ScheduledHandler
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

Scheduler must not become a central switch over gameplay mechanics.

## Clock boundary

`SimulationClock` is mutable authoritative simulation time. `SimulationTime` exposes only `tick()` to consumers that may observe but not advance time.

Wall-clock time, renderer FPS and monitor refresh rate do not define authoritative mechanics. Game-speed presentation should change how many simulation ticks are advanced per real second, not mutate MovementRate or domain costs.

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

A domain process family normally registers one handler, not one handler per object/action. Thousands of Movement actions can therefore share one Movement handler while carrying distinct MovementAction ids.

## Domain process identity versus TaskHandle

These identities stay separate:

```text
domain process id   semantic identity owned by mechanic
TaskHandle          infrastructure identity of scheduled activation
```

One domain process may eventually schedule multiple activations; cancellation of one task does not inherently redefine domain-process identity.

Current Movement has no early-cancellation consumer and therefore does not retain TaskHandle for its active step.

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

Therefore `advanceTicks(10)` is semantically ten calls to the same production step and must match ten individual advances.

## Same-tick dispatch

A dispatch processes a deterministic snapshot batch of work that was due when dispatch began. A handler scheduling more work for the current tick does not cause unbounded recursive draining inside that same batch.

Current Movement additionally enforces duration of at least one tick, so a step never schedules its own completion for the tick in which it starts.

A future mechanic requiring repeated same-tick activation must explicitly justify a phase-policy change rather than rely on accidental recursion.

## Deterministic ordering

Tasks due at the same simulation time have stable explicit ordering (scheduled time, then task identity) rather than depending on map/heap iteration or thread timing.

This ordering is infrastructure semantics and will become especially visible once future dynamic contention such as Occupancy exists.

## Domain actions are not Scheduler state

A scheduled task is infrastructure. A domain action/process remains domain runtime state.

For Movement:

```text
MovementAction    object/source/destination semantics in MovementStateStore
ScheduledTask     due time/routing in Scheduler
```

The same boundary should hold for future crafting, growth, combat or construction instead of prematurely creating one universal Action system.

## Event-driven activity

The intended scale model is:

```text
persistent objects may remain inactive indefinitely
only active processes schedule work
Scheduler wakes due process ids
domain handler resumes authoritative domain state
```

CPU cost can therefore correlate with active processes rather than total persistent object count. Concrete allocation/storage optimization remains workload-driven.

## Diagnostics and tests

Coverage includes clock advancement, handler registration, deterministic due ordering, task identity/cancellation infrastructure, bound relative scheduling, SimulationStepper phase order, scheduled Movement completion and batched-versus-individual tick equivalence.

Domain integration tests use real production stepping rather than manually calling scheduled handlers at arbitrary times.

## Deferred

- authoritative RNG ownership when the first random mechanic appears;
- cancellation policy for real death/stun/replacement consumers;
- background scheduling and multithreaded authoritative mutation;
- any phase-model change required by a demonstrated same-tick consumer.

Those changes must preserve explicit deterministic ordering or deliberately revise it as an architectural decision.

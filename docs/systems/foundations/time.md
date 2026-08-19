# Time and Scheduling

## In plain language

EvoForge has its own clock. One simulation tick is an authoritative step of world time; it is **not** one rendered frame and it does not depend on monitor refresh rate.

Most things also do not need to “wake up” every tick. A movement completion due in 12 ticks, a growing plant and a dormant Water field can all sleep until real causal work is due. This is why EvoForge uses a scheduler/process model instead of mandatory `update(dt)` on every persistent object.

## Current status

The production time stack is:

```text
SimulationClock      mutable authoritative current tick
SimulationTime       read-only tick capability
Scheduler            stores due activations + deterministic ordering
HandlerRegistry      HandlerId -> ScheduledHandler
ProcessScheduler     narrow domain scheduling capability
BoundProcessScheduler
SimulationStepper    authoritative one-tick phase
```

Domain systems own the meaning of process IDs and state. Scheduler owns only when/routing.

## The core responsibility split

```text
Scheduler:
  WHEN does work wake?
  WHICH registered handler receives it?
  WHICH opaque process id is carried?

Domain mechanic:
  WHAT does that process id mean?
  WHAT authoritative state changes when it wakes?
```

Scheduler must never become a switch containing Movement, Water, Growth, Needs and every future mechanic.

## Clock versus wall time

`SimulationClock` owns the integer tick. Consumers that only need to observe receive `SimulationTime.tick()`.

Rendering speed can decide how quickly real-world seconds are converted into requests to advance simulation ticks, but it does not directly alter:

- Movement rate;
- Growth rate;
- Need progression;
- hydrology laws;
- any other authoritative per-tick mechanic.

That keeps the same simulation reproducible at different FPS.

## Scheduled activation identity

A scheduled activation conceptually contains:

```text
dueTick
HandlerId
processId
TaskHandle
stable task ordering identity
```

`processId` is opaque infrastructure data. Meaning belongs to the registered domain handler.

Thousands of Movement actions can therefore share one Movement process-family handler while using distinct process IDs.

`TaskHandle` is different from domain process identity:

```text
domain process id = semantic domain identity
TaskHandle        = one scheduler activation identity
```

One domain process may schedule multiple activations over its lifetime.

## Narrow relative scheduling

A domain usually should not receive raw Scheduler + mutable clock + arbitrary HandlerId authority.

Instead it can receive:

```java
ProcessScheduler.scheduleAfter(delayTicks, processId)
```

`BoundProcessScheduler` already knows the family's `HandlerId` and reads current `SimulationTime` to calculate the absolute due tick.

This lets a mechanic say “wake my process 17 in 10 ticks” without being able to route work into another domain's handler.

## Exact production tick semantics

One production tick is currently:

```text
1. SimulationClock.advance()
2. Scheduler.dispatchDue(clock.tick())
```

`SimulationStepper` owns this order.

Therefore advancing 10 ticks must be semantically equivalent to invoking the same one-tick production operation 10 times; tests/scenarios should not call scheduled handlers manually and invent an alternate phase model.

## Same-tick scheduling semantics

A Scheduler dispatch operates on a deterministic snapshot of work due when that dispatch begins.

If a handler schedules additional work for the **current tick**, that newly created activation is not recursively drained forever inside the same batch.

Movement additionally requires an atomic edge duration of at least one tick, so an edge never schedules its own completion on its start tick.

If a future mechanic genuinely requires repeated same-tick scheduled activation, it must justify an explicit phase-policy change rather than relying on accidental recursion.

## Deterministic due ordering

Activations due at the same tick are ordered by explicit stable scheduler/task identity rather than map iteration, heap accident or thread timing.

This ordering can be observable in contention, so domain behavior must never depend on unspecified collection order.

Where two processes should be logically order-independent, encode that semantics explicitly. Current periodic precipitation/evaporation, for example, suppresses evaporation on a precipitation-event tick instead of hoping the scheduler happens to run rain first.

## Domain action versus scheduler state

A scheduled activation is not the domain action itself.

```text
MovementAction          authoritative Movement state
Need progression state authoritative Need state/process
Growth state            authoritative Growth/resource state
Liquid active frontier  authoritative hydraulic-work state
ScheduledTask           infrastructure wake-up
```

This separation lets domain systems revalidate, cancel or reschedule according to their own lifecycle rather than treating Scheduler as a universal action store.

### MoveTo cancellation example

Current route cancellation deliberately does not cancel an already in-flight atomic Movement activation. The current edge may finish; route logic then starts no later edge and releases route ownership.

Because current semantics do not require mid-edge cancellation, `MovementStateStore` does not retain a `TaskHandle` just in case. A future death/stun mechanic can introduce that ownership only if it truly needs it.

## Event-driven scale model

```text
persistent state may sleep indefinitely
        ↓
a causal event creates/activates work
        ↓
domain schedules a future activation
        ↓
Scheduler wakes the process when due
        ↓
domain resumes its own state
        ↓
work finishes or schedules the next meaningful wake-up
```

Liquid flow demonstrates the same principle spatially: once its active frontier reaches a fixed point, the process becomes dormant until a free-liquid mutation wakes new hydraulic work.

## Invariants

- Simulation ticks are authoritative; renderer FPS is not.
- Scheduler owns activation timing/routing, not domain meaning.
- Domain process identity is separate from `TaskHandle`.
- Same-tick dispatch uses a bounded deterministic batch.
- Same-due-tick ordering is stable.
- Domain systems receive narrow scheduling capabilities when possible.
- Production tests/scenarios advance time through `SimulationStepper`.
- Optimization through sleeping/scheduling must preserve domain semantics.

## Current limitations

Not yet defined:

- authoritative multithreaded mutation;
- distributed scheduler/network time;
- stateful authoritative RNG-stream ownership for mechanics that genuinely need it;
- general mid-action scheduled-task cancellation/interrupt phases;
- richer sub-tick simulation phases.

## Code and tests

Primary code lives under:

```text
simulation/.../time/
```

Representative coverage includes clock advancement, handler registration, deterministic due order, task handles/cancellation infrastructure, bound scheduling, production step ordering, Movement completion, route continuation/cancellation and batched-versus-individual advancement equivalence.

## Sources

**Internal EvoForge design.** The discrete tick/process ownership model is project architecture; it is not presented as a particular published discrete-event simulation kernel.

See [Runtime Composition](runtime.md), [Movement](../traversal/movement.md), and [Architecture](../../architecture.md).

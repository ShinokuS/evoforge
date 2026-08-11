# Time and Scheduler

EvoForge avoids a mandatory `update(dt)` call on every object. Simulation time and activation are handled by explicit scheduling infrastructure so inactive entities do not consume CPU simply because they exist.

## `SimulationClock`

`SimulationClock` is the authoritative time value for the simulation foundation. Domain systems should not derive authoritative ordering from wall-clock time or renderer frame timing.

Presentation may display or interpolate time, but authoritative mechanics use simulation time.

## Scheduler responsibility

The Scheduler owns ordering and activation timing. It does not own the meaning of scheduled work.

```text
Scheduler answers: when and in what deterministic order?
Domain handler answers: what does this activation mean?
```

This distinction prevents the scheduler from becoming a central enum or switch over every gameplay mechanic.

## Current types

The time package currently includes:

```text
SimulationClock
Scheduler
ScheduledTask
ScheduledHandler
HandlerId
HandlerRegistry
TaskHandle
```

Handlers are registered separately and referenced by stable runtime handler ids inside scheduled tasks.

## Event-driven activity

The intended simulation model is:

```text
persistent objects may exist indefinitely
only active processes schedule work
scheduler activates due work
handler mutates the authoritative owner
optional event records the resulting fact
```

This is better suited to large persistent worlds than scanning every object every frame to ask whether it has something to do.

## Scheduler is not an Action system

A scheduled task is infrastructure state. A future domain `Action` may use scheduling, but the two concepts should not be collapsed.

For example, “cow walks to cell X” might be a domain action whose phases schedule future activation. Scheduler should not need to know that the task represents walking.

## Deterministic ordering

When multiple tasks become due at the same simulation time, ordering must be explicit and stable. Authoritative outcomes cannot depend on arbitrary heap/map iteration or thread timing.

The Scheduler tests are therefore important determinism infrastructure, not merely container tests.

## Cancellation and handles

`TaskHandle` provides identity for scheduled work so callers can cancel or track a task without depending on the scheduler's internal data structure.

A handle is infrastructure identity for scheduled activation, not `ObjectId` identity and not a persistence key by itself.

## Handler registration

`HandlerRegistry` associates handler ids with `ScheduledHandler` implementations. Domain-specific code registers handlers explicitly.

The intended extension pattern is:

```text
new timed mechanic
    -> new domain handler
    -> explicit registration
    -> existing Scheduler
```

not:

```text
modify Scheduler switch to add mechanic type
```

## Wall clock versus simulation clock

The game renderer may run at 30, 60, 144, or variable FPS. None of those rates should define authoritative simulation semantics directly.

Future pause, time acceleration, deterministic replay, and headless scenario execution all depend on keeping simulation time independent from rendering cadence.

## Randomness

No generic RNG service exists yet because no current mechanic needs authoritative randomness. When the first random mechanic appears, RNG state must be explicit and reproducible, and scheduled ordering must remain deterministic for the same state/commands.

## Performance model

The scheduler helps satisfy the large-world scale envelope by making CPU cost correlate with active processes rather than total persistent object count.

This does not mean every future recurring process must create huge numbers of tiny heap objects. Allocation/storage representation should be measured when a real active-agent workload exists.

## Testing

Scheduler tests cover ordering, registration, task identity, cancellation, clock interaction, and boundary/error behavior. As domain actions appear, integration tests should assert that scheduled handlers produce deterministic authoritative results through normal system boundaries.

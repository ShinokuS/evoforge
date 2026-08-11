# Time and Scheduler

EvoForge avoids a mandatory `update(dt)` call on every object. Simulation time and activation are handled by explicit scheduling infrastructure so inactive entities do not consume CPU simply because they exist.

## `SimulationClock` and `SimulationTime`

`SimulationClock` is the authoritative mutable time value for the simulation foundation. Domain systems should not derive authoritative ordering from wall-clock time or renderer frame timing.

`SimulationTime` is its read-only capability and exposes only `tick()`. Infrastructure that needs to read the current simulation time without advancing it should depend on this narrower boundary.

Presentation may display simulation time differently, but authoritative mechanics use simulation ticks.

## Scheduler responsibility

The Scheduler owns ordering and activation timing. It does not own the meaning of scheduled work.

```text
Scheduler answers: when and in what deterministic order?
Domain process owner answers: what does this activation mean?
```

This distinction prevents the scheduler from becoming a central enum or switch over every gameplay mechanic.

## Current types

The time package currently includes:

```text
SimulationTime
SimulationClock
SimulationStepper
Scheduler
ScheduledTask
ScheduledHandler
HandlerId
HandlerRegistry
TaskHandle
ProcessScheduler
BoundProcessScheduler
```

Handlers are registered separately and referenced by runtime handler ids inside scheduled tasks.

## Production simulation step

`SimulationStepper` now owns the first production definition of one simulation step:

```text
advance SimulationClock by one tick
    ↓
Scheduler.dispatchDue(currentTick) once
```

The Scenario fixture and future presentation layers may drive this production API, but they do not define their own tick semantics.

The Scheduler dispatches a snapshot batch of work that was due when dispatch began. Work scheduled by a handler for the same tick is therefore not recursively drained in the same batch. Timed Movement guarantees a minimum duration of one tick, so it never depends on same-tick recursive execution.

## Event-driven activity

The intended simulation model is:

```text
persistent objects may exist indefinitely
only active processes schedule work
scheduler activates due work
handler resumes the authoritative domain process
optional diagnostics/events may record resulting facts later
```

This is better suited to large persistent worlds than scanning every object every frame to ask whether it has something to do.

## Scheduler is not an Action system

A scheduled task is infrastructure state. A domain Action or Process may use scheduling, but the concepts are not collapsed.

Timed Movement is the first concrete example:

```text
MovementAction
    = domain state for one active adjacent transition

ScheduledTask
    = wake Movement at a simulation tick with processId
```

`MovementActionId` and `TaskHandle` are deliberately different identities.

## Bound process scheduling

Domain mechanics should not receive raw `Scheduler + HandlerId` simply to schedule their own continuation.

`ProcessScheduler` is the narrow domain-facing capability:

```text
scheduleAfter(delayTicks, processId)
```

`BoundProcessScheduler` binds that capability to one registered `HandlerId` and converts relative delay into absolute simulation time.

The extension pattern is therefore:

```text
new timed mechanic
    ↓
mechanic-owned process store / processor
    ↓
register processor callback once
    ↓
HandlerId
    ↓
BoundProcessScheduler
    ↓
mechanic receives only ProcessScheduler
```

One handler serves all active processes of that mechanic. The Scheduler differentiates them by `processId`; it does not create one handler per Action.

## Deterministic ordering

When multiple tasks become due at the same simulation time, ordering must be explicit and stable. Authoritative outcomes cannot depend on arbitrary heap/map iteration or thread timing.

The Scheduler orders by scheduled time and then task handle. Scheduler tests are therefore determinism infrastructure, not merely container tests.

## Cancellation and handles

`TaskHandle` provides identity for scheduled work so infrastructure callers can cancel or track a task without depending on the scheduler's internal data structure.

A handle is infrastructure identity for scheduled activation, not `ObjectId` identity and not a domain Action identity.

The first Timed Movement slice deliberately has no early cancellation API and therefore does not expose `TaskHandle` through `ProcessScheduler`. Movement Actions are removed when their scheduled completion runs. If early cancellation becomes a real requirement, stale-wakeup versus scheduler-cancellation semantics must be chosen explicitly then.

## Handler registration

`HandlerRegistry` associates handler ids with `ScheduledHandler` implementations. Domain-specific process processors are registered explicitly, usually through a narrow method reference such as `movementActions::complete`.

The intended extension pattern is not:

```text
modify Scheduler switch to add mechanic type
```

The Scheduler remains unchanged as Movement, Crafting, Growth or other timed mechanics appear.

## Wall clock versus simulation clock

The renderer may run at 30, 60, 144, or variable FPS. None of those rates define authoritative simulation semantics directly.

Future pause and time acceleration should change how quickly real time drives simulation steps, not the meaning or ordering of simulation ticks themselves.

Headless tests already enforce the same principle: advancing ten ticks in one helper call is equivalent to calling the production stepper ten times individually.

## Randomness

No generic RNG service exists yet because no current mechanic needs authoritative randomness. When the first random mechanic appears, RNG state must be explicit and reproducible, and scheduled ordering must remain deterministic for the same state and commands.

## Performance model

The scheduler helps satisfy the large-world scale envelope by making CPU cost correlate with active processes rather than total persistent object count.

This does not mean every future recurring process must create huge numbers of tiny heap objects. Allocation and storage representation should be measured when a real active-agent workload exists.

## Testing

Scheduler tests cover ordering, registration, task identity, cancellation, clock interaction and boundary/error behavior.

Timed Movement adds the first production integration tests for:

- delayed authoritative mutation;
- different process durations from different movement rates;
- completion-time revalidation;
- deterministic fractional timing carry;
- equivalence of batched and individual tick advancement.

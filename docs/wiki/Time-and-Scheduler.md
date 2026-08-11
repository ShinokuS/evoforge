# Time and Scheduler

EvoForge avoids a mandatory `update(dt)` call on every object. Simulation time and activation are handled by explicit scheduling infrastructure so inactive entities do not consume CPU simply because they exist.

The first production timed consumer is Movement. Its implementation made the time boundary concrete: domain systems schedule their own process ids through a narrow bound capability, while `SimulationStepper` owns the production meaning of advancing one simulation tick.

## Responsibility split

```text
SimulationClock
    -> authoritative current simulation tick

Scheduler
    -> when scheduled work becomes due
    -> deterministic ordering of due work

HandlerRegistry
    -> HandlerId -> ScheduledHandler

ProcessScheduler
    -> narrow domain capability: scheduleAfter(delay, processId)

BoundProcessScheduler
    -> binds one ProcessScheduler to one HandlerId and SimulationTime

SimulationStepper
    -> production owner of one tick's phase order

Domain action/process owner
    -> what processId means and what happens when it wakes
```

The core law is:

```text
Scheduler knows WHEN / HANDLER / PROCESS ID.
Domain knows WHAT THAT PROCESS MEANS.
```

Scheduler must not become a central switch over gameplay mechanics.

## `SimulationClock` and `SimulationTime`

`SimulationClock` is the authoritative mutable time value for the simulation foundation.

`SimulationTime` is its read-only capability:

```java
public interface SimulationTime {
    long tick();
}
```

Consumers that only need to read current simulation time receive this narrow capability rather than mutable clock control.

`BoundProcessScheduler` depends on `SimulationTime`; it cannot advance the world clock.

Domain systems should not derive authoritative ordering from wall-clock time or renderer frame timing. Presentation may display or accelerate simulation time, but authoritative mechanics use simulation ticks.

## `Scheduler`

Scheduler owns timed activation infrastructure. Conceptually each scheduled task contains:

```text
when
HandlerId
processId
TaskHandle
stable ordering identity
```

The Scheduler does not interpret `processId`. `processId = 17` may mean a Movement action for one handler and a completely unrelated Crafting process for another future handler.

The pair:

```text
HandlerId + processId
```

is enough to route activation without a global process-type enum.

## `HandlerRegistry`

`HandlerRegistry` associates runtime `HandlerId` values with `ScheduledHandler` callbacks.

A domain process family normally registers one handler:

```text
MovementActionProcessor::complete
    -> one HandlerId for all MovementAction instances
```

One thousand moving objects do **not** require one thousand handlers. They schedule one thousand tasks carrying different process ids to the same Movement handler.

Future mechanics follow the same pattern:

```text
Movement     -> movement handler
Crafting     -> crafting handler
Growth       -> growth handler
Construction -> construction handler
```

No central Scheduler switch changes when a new mechanic is added.

## `ScheduledHandler`

The infrastructure callback is intentionally tiny:

```text
handle(processId)
```

It is an adapter boundary, not a domain model.

For Movement, the registered callback resolves the `processId` as a `MovementActionId`, reloads that action from `MovementStateStore`, revalidates the world and either commits Spatial or discards the interrupted action.

## `ProcessScheduler`

Domain start systems should not normally receive raw `Scheduler + HandlerId + SimulationClock`.

The reusable narrow scheduling capability is:

```java
ProcessScheduler.scheduleAfter(
        long delayTicks,
        long processId)
```

This lets a mechanic say:

```text
wake my process 17 after 10 simulation ticks
```

without giving it authority to:

```text
advance the clock
choose another domain's HandlerId
inspect Scheduler storage
redefine absolute time semantics
```

This abstraction arrived with the first real timed consumer rather than being designed speculatively before any domain needed it.

## `BoundProcessScheduler`

`BoundProcessScheduler` is the infrastructure adapter that implements the narrow domain capability.

It owns references to:

```text
SimulationTime
Scheduler
one HandlerId
```

When the domain calls:

```text
scheduleAfter(delay, processId)
```

it calculates the absolute due tick from authoritative simulation time and delegates to Scheduler with its already-bound handler.

This is the intended general bridge for future timed mechanics.

## Domain process identity versus `TaskHandle`

Scheduler task identity and domain process identity are different concepts.

For Movement:

```text
MovementActionId
    = domain identity of an active movement process

TaskHandle
    = infrastructure identity of one scheduled activation
```

They must not be collapsed merely because both can be represented numerically.

A future domain process may schedule more than one activation over its lifetime. Conversely, infrastructure cancellation may remove a task without changing the semantic identity of the domain process.

Current Movement does not expose early cancellation, so it does not retain or propagate `TaskHandle` yet.

## `SimulationStepper`

`SimulationStepper` is the production owner of current simulation-step semantics.

The current one-tick order is:

```text
1. SimulationClock.advance()
2. Scheduler.dispatchDue(clock.tick())
```

This matters because test fixtures and future GUI code must **drive** this production contract rather than define their own tick semantics.

`ScenarioHarness.advance()` delegates to `SimulationStepper`. `advanceTicks(n)` is only a loop over the same production operation.

Therefore:

```text
advanceTicks(10)
```

and:

```text
advance(); advance(); ... x10
```

must produce identical authoritative state.

## Same-tick dispatch semantics

Scheduler dispatch works in a deterministic snapshot batch of work that was due when dispatch began.

A task scheduled from inside a handler for the current tick is therefore not recursively drained forever inside the same batch.

Current Movement additionally guarantees:

```text
movement duration >= 1 tick
```

so a Movement action never schedules its own completion for the same tick in which it starts.

If a future mechanic genuinely needs repeated same-tick activation, that mechanic must provide evidence for revisiting the phase policy rather than relying on accidental recursive draining.

## Movement example

Suppose current tick is 100 and an actor starts a Movement action whose duration is 15 ticks.

```text
tick 100
MovementSystem creates MovementAction #42
MovementSystem calls movement ProcessScheduler.scheduleAfter(15, 42)

BoundProcessScheduler
    reads current SimulationTime = 100
    schedules Scheduler task for tick 115
    uses the already-bound Movement HandlerId

... no per-tick Movement polling ...

tick 115
SimulationStepper advances clock
Scheduler dispatches due Movement task
MovementActionProcessor.complete(42)
    -> reload action
    -> revalidate source/object/Navigation
    -> SpatialSystem.move(...) if still valid
```

The action consumes no mandatory CPU on ticks 101..114 simply because it exists.

## Event-driven activity

The intended simulation model is:

```text
persistent objects may exist indefinitely
only active processes schedule work
scheduler activates due work
domain handler resumes the authoritative domain process
optional diagnostics/events may record resulting facts later
```

This is better suited to large persistent worlds than scanning every object every frame to ask whether it has something to do.

Movement is now the first concrete proof of this model rather than only a future example.

## Scheduler is not an Action system

A scheduled task is infrastructure state. A domain `Action` is domain runtime state.

Current Movement demonstrates the distinction:

```text
MovementAction
    owns object/source/destination semantics
    lives in MovementStateStore

ScheduledTask
    owns activation time/routing
    lives in Scheduler
```

Scheduler does not know that the task represents walking.

This boundary should remain for future Crafting, Growth, Combat or Construction processes instead of creating one generic all-purpose `ActionSystem` prematurely.

## Deterministic ordering

When multiple tasks become due at the same simulation time, ordering must be explicit and stable. Authoritative outcomes cannot depend on arbitrary heap/map iteration or thread timing.

The current Scheduler orders by scheduled time and then task handle. Scheduler tests are therefore determinism infrastructure, not merely container tests.

A future multi-agent workload may make ordering effects more visible, especially once Occupancy introduces contention for destinations/resources. The ordering contract must remain stable and observable.

## Cancellation and stale activation

`TaskHandle` provides infrastructure identity for cancellation/tracking.

The current Movement slice deliberately does not implement early cancellation. Actions normally remain active until their scheduled completion wakes and removes them.

When a real cancellation consumer appears — for example death, stun, forced displacement or process replacement — the design must decide whether to:

```text
cancel Scheduler task eagerly using TaskHandle
or
remove domain process and tolerate a later stale wake-up that finds nothing
```

That decision should be driven by semantics and measured stale-task volume, not guessed in advance.

## Wall clock versus simulation clock

The renderer may run at 30, 60, 144 or variable FPS. None of those rates define authoritative simulation semantics.

Likewise, a game-speed control such as 1x/2x/5x should conceptually change how many simulation ticks are advanced per real second, not change `MovementRate` or `TransitionCost` merely to simulate faster presentation.

For the same initial state and same sequence of simulation ticks/commands, authoritative state must not depend on presentation FPS.

Future pause, time acceleration, deterministic replay, headless scenario execution and testing all depend on this separation.

## Randomness

No generic RNG service exists yet because current timed Movement and TransitionCost are deterministic and need no authoritative randomness.

When the first random mechanic appears, RNG state must be explicit and reproducible, and scheduled ordering must remain deterministic for the same state and commands.

## Performance model

Scheduler helps satisfy the large-world scale envelope by making CPU cost correlate with active processes rather than total persistent object count.

Movement currently schedules one completion activation per active adjacent step rather than ticking every mover each world tick.

This does not mean every future recurring process must create huge numbers of tiny heap objects. Allocation and storage representation should be measured when representative active-agent workloads exist.

## Testing

Time/Scheduler coverage now includes:

```text
clock advancement
handler registration
deterministic task ordering
task identity/cancellation infrastructure
BoundProcessScheduler relative scheduling
SimulationStepper phase order
movement scheduled completion
batched-versus-individual tick advancement equivalence
completion-time world revalidation
```

The key integration rule is that domain action tests use the real production stepping path rather than manually invoking scheduled handlers at arbitrary times.

## Stable rules

```text
1. SimulationClock is authoritative simulation time.
2. Presentation wall time/FPS does not define authoritative mechanics.
3. Scheduler owns when/order, not domain meaning.
4. HandlerRegistry routes process families without a global gameplay switch.
5. ProcessScheduler is the normal narrow scheduling capability for a domain start system.
6. BoundProcessScheduler binds one domain family to one HandlerId.
7. Domain process id and TaskHandle remain different identities.
8. SimulationStepper owns production tick phase order.
9. Test fixtures drive SimulationStepper instead of inventing time semantics.
10. A domain process owns its runtime state outside Scheduler.
```

## Related documentation

- [Movement System](Movement-System.md) — first production timed consumer and TransitionCost integration.
- [Control Backbone](Control-Backbone.md) — external start intent.
- [Testing Strategy](Testing-Strategy.md) — deterministic integration tests.

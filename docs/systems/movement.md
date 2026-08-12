# Movement

## Purpose

Execute one concrete adjacent actor movement as deterministic timed simulation work.

```text
Navigation       structural edge exists
TransitionCost   intrinsic edge price
MovementRate     converts price to actor time
Movement         owns the in-flight step and completion decision
Spatial          commits authoritative position
```

Movement does not own topology, object identity, Spatial storage, Scheduler internals, pathfinding, occupancy, rendering or AI intent.

## External intent

`MoveStepCommand(objectId, destination XYZ)` means **start a timed attempt to move this object from its current authoritative position to one adjacent destination**. It never means teleport immediately.

An accepted command means an action was started, not that movement already completed.

Normal start rejection is structured and includes states such as:

- movement capability unavailable;
- object not placed;
- object already moving;
- destination not one of the 26 immediate neighbors;
- structural Navigation transition unavailable.

Unknown trusted ids or broken definition/bootstrap state remain exceptional rather than ordinary gameplay rejection.

## Start sequence

Current domain validation is conceptually:

```text
object exists
    ↓
definition has MovementRate
    ↓
object has Spatial transform
    ↓
no active MovementAction
    ↓
destination is adjacent
    ↓
Navigation exposes source → destination
    ↓
calculate shared TransitionCost
    ↓
convert cost to ticks using MovementRate + carry
    ↓
create MovementAction
    ↓
schedule completion
```

Movement never reimplements Ramp/Shape rules. Structural validity comes from Navigation.

## Movement capability

`MovementRate` is immutable definition data compiled from an object definition's `movement.rate`. It is a positive integer measured in transition-cost units per simulation tick.

Absence of the movement aspect means ordinary self-propelled `MoveStep` is unavailable. Swimming, flying, climbing, stamina and surface affinities are not hidden inside this first capability.

## Active state

A `MovementAction` stores only what one scheduled completion requires:

```text
MovementActionId
ObjectId
source XYZ
destination XYZ
```

`MovementActionId` is monotonic domain-process identity and is distinct from Scheduler task identity. Stale scheduled work therefore cannot resolve to a later movement action that reused an id.

`MovementStateStore` conceptually owns:

```text
per ObjectId:
    timing carry
    active MovementActionId | none

per MovementActionId:
    active MovementAction
```

At most one ordinary movement action may be active per object. Completed/interrupted actions are removed; active-state storage is not history/replay storage.

## Timing and fractional carry

Repeated per-step ceiling would bias long-run speed. Movement carries integer remainder across steps.

For transition cost `cost`, actor rate `rate` and previous remainder `carry`:

```text
total = cost + carry
ticks = floor(total / rate)
carry = total mod rate
```

Duration is clamped to at least one simulation tick.

Example, `cost = 1000`, `rate = 300`:

```text
step 1: 3 ticks, carry 100
step 2: 3 ticks, carry 200
step 3: 4 ticks, carry   0
```

Three equal steps therefore take 10 ticks instead of independently rounding all three to four ticks.

Carry belongs to the object’s Movement state so it survives separate actions.

## Scheduler boundary

Movement receives a narrow process scheduling capability rather than Scheduler internals:

```text
ProcessScheduler.scheduleAfter(delayTicks, processId)
```

A bound scheduler associates one domain process family with SimulationTime, Scheduler and its handler. Movement does not choose arbitrary HandlerIds or calculate an absolute completion tick.

The reusable pattern is:

```text
domain start
    ↓ scheduleAfter(delay, processId)
bound ProcessScheduler
    ↓
Scheduler
    ↓ when due
domain processor resumes processId
```

Scheduler owns activation timing; Movement owns the meaning and state of the action.

## Authoritative position

Movement is discrete. For `A → B` completing at tick 15:

```text
ticks 0..14  Spatial = A
tick 15      completion revalidation
tick 15      if valid: Spatial = B
```

There is no authoritative fractional position between A and B. Future visual interpolation remains presentation-only.

## Completion-time revalidation

Scheduled completion reloads the active action and verifies at least:

```text
object still exists
object still has a transform
object is still at recorded source
Navigation still exposes source → destination
```

Only then does Movement ask Spatial to commit the destination. Whether completion succeeds or is interrupted, the active action is removed.

A sleeping action does not currently wake immediately on terrain/geometry mutation. It observes such invalidation when its scheduled completion runs. Reactive cancellation is deferred until a real consumer justifies it.

## Shared edge price

Movement consumes [`Transition Cost`](traversal-cost.md). It does not calculate a private movement price. Future Pathfinder must consume the same cost semantics.

`MovementRate` changes how long an actor takes to execute a cost; it does not redefine the cost itself.

## Diagnostics and tests

Scenario coverage includes:

- position remains at source until completion;
- different movement rates finish at different ticks;
- diagonal length changes duration;
- fractional carry stays deterministic across steps;
- a second simultaneous ordinary action is rejected;
- missing capability and invalid structural transitions are rejected;
- completion revalidation interrupts stale edges;
- terrain/Shape traversal contributions affect duration through shared TransitionCost;
- batching `advanceTicks(N)` is equivalent to N production steps.

## Deferred

- occupancy/reservation interaction;
- public cancellation and Scheduler task retention;
- immediate reaction to world mutation;
- actor-specific terrain/locomotion affinity;
- long-range `MoveTo` / route lifecycle;
- observable completion outcome for agents;
- involuntary falling and richer locomotion.

Future route execution must reuse one-edge Movement and revalidate the changing world instead of teleporting along an immutable precomputed path.

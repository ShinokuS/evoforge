# Movement

## Purpose

Execute one concrete adjacent actor movement as deterministic timed simulation work.

```text
Navigation       structural edge exists
TransitionCost   intrinsic edge price
Occupancy        current destination availability / execution claim
MovementRate     converts price to actor time
Movement         owns the in-flight step and completion decision
Spatial          commits authoritative position
```

Movement does not own topology, object identity, Spatial storage, Scheduler internals, pathfinding, occupancy state, rendering or AI intent.

## External intent

`MoveStepCommand(objectId, destination XYZ)` means **start a timed attempt to move this object from its current authoritative position to one adjacent destination**. It never means teleport immediately.

An accepted command means an action was started, not that movement already completed.

Normal start rejection is structured and includes states such as:

- movement capability unavailable;
- object not placed;
- object already moving;
- destination not one of the 26 immediate neighbors;
- structural Navigation transition unavailable;
- exclusive destination physically occupied;
- exclusive destination already reserved by another execution action.

The occupancy-specific command result codes are:

```text
movement:destination_occupied
movement:destination_reserved
```

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
create MovementAction identity
    ↓
exclusive mover: claim immediate destination through Occupancy
    ↓
schedule completion
```

Movement never reimplements Ramp/Shape rules. Structural validity comes from Navigation. It also never scans Spatial objects to invent its own occupancy rule; destination admission/claiming comes from Occupancy.

If an exclusive destination is `OCCUPIED` or `RESERVED`, the provisional action is removed and the command is rejected. The object's timing carry is not changed by that rejected attempt.

## Movement capability

`MovementRate` is immutable definition data compiled from an object definition's `movement.rate`. It is a positive integer measured in transition-cost units per simulation tick.

Absence of the movement aspect means ordinary self-propelled `MoveStep` is unavailable. Swimming, flying, climbing, stamina and surface affinities are not hidden inside this first capability.

Movement capability and exclusive occupancy are intentionally independent. A definition may be movable without requiring an exclusive cell, and a future stationary object may require exclusive occupancy without ordinary self-propelled Movement.

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
    OccupancyReservationId | none
```

Only exclusive movers acquire the execution reservation. Occupancy mints the opaque reservation handle; Movement stores that handle with the concrete active action. This keeps reservation identity owned by Occupancy while tying its practical lifetime to the Movement action that acquired it.

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

Carry belongs to the object’s Movement state so it survives separate actions. A rejected occupancy claim does not consume/update the carry because no movement action actually starts.

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

If scheduling the accepted action itself fails exceptionally, Movement rolls back an already acquired destination reservation and removes the provisional action before propagating the programming/infrastructure failure.

## Authoritative position and execution claim

Movement is discrete. For an exclusive move `A → B` completing at tick 15:

```text
ticks 0..14  Spatial = A
             A derives as OCCUPIED
             B = RESERVED by this MovementAction

tick 15      completion revalidation

tick 15      if valid:
             Spatial = B
             reservation released
             B derives as OCCUPIED
```

There is no authoritative fractional position between A and B. Future visual interpolation remains presentation-only.

Only the immediate destination of the active one-edge action is reserved. Later cells of any future route are not claimed by `MoveStep`.

## Completion-time revalidation

Scheduled completion reloads the active action and verifies at least:

```text
object still exists
object still has a transform
object is still at recorded source
Navigation still exposes source → destination
exclusive action still owns its exact destination reservation
reserved destination has not become physically occupied
```

Only then does Movement ask Spatial to commit the destination.

Successful completion performs the Spatial commit, releases the exact execution reservation and removes the action. Interrupted completion leaves Spatial unchanged, releases the reservation if present and removes the action.

A stale action cannot release another action's claim because Occupancy release requires the exact reservation identity, claimant and destination.

A sleeping action does not currently wake immediately on terrain/geometry mutation. It observes such invalidation when its scheduled completion runs. Reactive cancellation is deferred until a real consumer justifies it.

## Shared edge price

Movement consumes [`Transition Cost`](traversal-cost.md). It does not calculate a private movement price. Future Pathfinder must consume the same cost semantics.

`MovementRate` changes how long an actor takes to execute a cost; it does not redefine the cost itself.

Occupancy likewise does not change `TransitionCost`: dynamic availability and intrinsic edge price remain separate facts.

## Future route execution

A future `MoveTo`/Pathfinder route is a disposable plan, not authoritative motion. Long-range execution must continue to reuse one-edge Movement:

```text
Pathfinder route
    ↓
next edge only
    ↓
MoveStep claims and executes that edge
    ↓
continue / wait / replan from the new world state
```

If another object occupies or reserves a later route cell before it becomes the next edge, the old route is not guaranteed. A higher-level route owner can use the structured reason to wait or ask Pathfinder for a replacement route.

Execution reservation and future path-planning reservation are intentionally different concerns.

## Known conservative corridor behavior

Because Spatial remains at the source throughout the timed step, an exclusive follower cannot claim that source until the leading mover completes. A one-cell-wide column therefore progresses conservatively rather than as an automatically coordinated train.

This is a known consequence of the current discrete Movement contract, not an Occupancy bug. Early source release, yielding, swap/displacement and coordinated following require real multi-agent consumers before their semantics are chosen.

## Diagnostics and tests

Scenario coverage includes:

- position remains at source until completion;
- different movement rates finish at different ticks;
- diagonal length changes duration;
- fractional carry stays deterministic across steps;
- a second simultaneous ordinary action on one object is rejected;
- missing capability and invalid structural transitions are rejected;
- completion revalidation interrupts stale edges;
- terrain/Shape traversal contributions affect duration through shared TransitionCost;
- batching `advanceTicks(N)` is equivalent to N production steps;
- exclusive destination claim exists for the timed move window;
- same-destination contention returns `destination_reserved` to the later starter;
- after completion that destination is physical `OCCUPIED` rather than `RESERVED`;
- occupied destinations return `destination_occupied`;
- interrupted completion releases its reservation;
- non-exclusive movers can share cells because Movement does not equate mobility with physical exclusivity.

## Deferred

- public cancellation and Scheduler task retention;
- immediate reaction to world mutation;
- actor-specific terrain/locomotion affinity;
- long-range `MoveTo` / route lifecycle;
- observable completion outcome for agents;
- involuntary falling and richer locomotion;
- yielding, swap/displacement, deadlock resolution and coordinated multi-agent movement.

Future route execution must reuse one-edge Movement and revalidate the changing world instead of teleporting along an immutable precomputed path.

# Movement

## Purpose

Execute concrete actor movement as deterministic timed simulation work and compose long-range `MoveTo` intent over the same one-edge execution primitive.

```text
Pathfinder       disposable long-range route advice
        ↓
MoveTo           owns route-level locomotion intent
        ↓
Movement         executes one concrete timed edge
        ↓
Navigation       structural edge exists
TransitionCost   intrinsic edge price
Occupancy        current destination availability / execution claim
MovementRate     converts price to actor time
Spatial          commits authoritative position
```

Movement does not own topology, object identity, Spatial storage, Scheduler internals, pathfinding algorithms, occupancy state, rendering or AI policy.

## Result model

Expected impossibility is open structured data rather than a project-wide outcome enum.

A movement operation exposes the neutral floor:

```text
accepted
ResultCode
```

`ResultCode` is namespaced data such as `movement:started` or `movement:destination_reserved`. The set is deliberately open. Generic Control and continuing route orchestration do not maintain exhaustive switches over possible reasons.

For one edge, `MovementStartAttempt` additionally returns the `MovementActionId` when accepted. This lets an internal owner correlate the concrete child action with its later completion without copying a catalog of domain results into Control.

Broken programming, configuration and ownership invariants remain exceptions.

## External intent

`MoveStepCommand(objectId, destination XYZ)` means start one timed attempt from the current authoritative position to one adjacent destination. It never teleports.

`MoveToCommand(objectId, goal XYZ)` means accept one long-range locomotion intent. Acceptance does not mean the goal has already been reached. A valid intent may terminate immediately with `NO_PATH` or source equal to goal, or later after one or more timed edges.

The latest terminal `MoveToCompletion` retained for an object exposes:

```text
MoveToActionId
ObjectId
reachedGoal
ResultCode
```

This is bounded observation, not an unbounded action history.

## One-edge start sequence

The concrete execution primitive is conceptually:

```text
object exists
    ↓
definition has MovementRate
    ↓
object has Spatial transform
    ↓
caller is allowed to control locomotion
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
exclusive mover: claim immediate destination through Occupancy
    ↓
create MovementAction identity
    ↓
attach reservation handle if acquired
    ↓
schedule completion
```

Movement never reimplements Shape/Ramp rules, traversal prices or physical occupancy rules. Those facts come from their authoritative owners.

Rejected starts do not create action state and do not consume timing carry.

## Movement capability

`MovementRate` is immutable definition data. It is a positive integer measured in transition-cost units per simulation tick.

Absence of the movement aspect means ordinary self-propelled Movement is unavailable. Swimming, flying, climbing, stamina and actor-specific surface affinity are not hidden inside this capability.

Movement capability and exclusive occupancy remain independent definition properties.

## MovementAction state

A `MovementAction` represents exactly one scheduled edge:

```text
MovementActionId
ObjectId
source XYZ
destination XYZ
```

`MovementStateStore` owns per-object timing carry, the active one-edge action, attached Occupancy reservation identity, and long-lived locomotion claims.

At most one concrete MovementAction may be active per object. Completed or interrupted actions are removed; this storage is not replay/history storage.

## Long-lived MovementClaim

A route-level controller needs exclusive ownership even while no concrete edge is active, for example while planning or between child transitions.

`MovementClaimId` is an opaque ownership token with the invariant:

```text
ObjectId → 0..1 active MovementClaim
```

A `MoveTo` acquires one claim for its entire lifetime. Future long-lived locomotion controllers may use the same ownership contract without adding type-specific checks for `MoveTo`, `Follow`, `Flee` or similar behaviors.

Standalone `MoveStep` does not manufacture a long-lived claim. Its active `MovementAction` already provides one-edge exclusivity. A standalone step is rejected while another controller owns a MovementClaim.

Claim release requires the exact `MovementClaimId + ObjectId`. A stale owner therefore cannot release a newer claim for the same object.

## Timing and fractional carry

Repeated per-step ceiling would bias long-run speed. Movement carries integer remainder across concrete edges.

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

Carry belongs to the object's Movement state, so a `MoveTo` route and the equivalent sequence of standalone concrete edges use identical timing physics.

## Scheduler and synchronous continuation

Movement receives only `ProcessScheduler.scheduleAfter(delayTicks, processId)` rather than Scheduler internals.

When a concrete edge completes, `MovementActionProcessor` first finishes Movement-owned authoritative work:

```text
commit Spatial if valid
release exact Occupancy reservation
remove MovementAction state
```

Only after cleanup does it synchronously publish `MovementStepCompletion` through one narrow completion sink.

The stable semantic fact is:

```text
committed = true  → destination was committed to Spatial
committed = false → destination was not committed
```

A namespaced `ResultCode` travels with the completion as open diagnostic data. `MoveTo` may pass that code onward but does not branch on an exhaustive reason catalog.

The production composition uses one bind-once `MovementStepCompletionRelay` to solve bootstrap ordering between the action processor and `MoveToSystem`. It has one target and is not an EventBus or listener registry.

### No artificial route tick

A successful child edge may synchronously start the next route edge in the same simulation tick in which the previous edge completed.

Two existing invariants make this safe:

1. every concrete edge duration is at least one tick;
2. Scheduler dispatches a fixed due batch, so work scheduled during that dispatch is not recursively executed inside the same batch.

Therefore route orchestration adds no idle tick between concrete edges and cannot form a zero-time recursive movement chain.

## Authoritative position and execution claim

Movement remains discrete. During an exclusive timed move `A → B`:

```text
before completion  Spatial = A
                   A derives as OCCUPIED
                   B = RESERVED by this MovementAction

completion         revalidate current world

if committed       Spatial = B
                   reservation released
                   B derives as OCCUPIED
```

There is no authoritative fractional coordinate. Rendering interpolation remains presentation-only.

Only the immediate destination of the active concrete edge is reserved. A `MoveTo` never reserves its whole route.

## Completion-time revalidation

Scheduled completion reloads the active action and validates current execution facts including object existence/placement, recorded source, Navigation availability and exact Occupancy reservation ownership/commit availability.

If valid, Spatial commits the destination. Otherwise Spatial stays at the last committed cell. Normal terminal paths still release Movement-owned reservation/action state before completion is observed.

A sleeping action currently discovers world invalidation only when its scheduled completion runs. Reactive early wake-up/cancellation remains deferred.

## Long-range MoveTo

`MoveToSystem` is orchestration over existing systems, not a second movement implementation.

```text
MoveToCommand
    ↓
acquire MovementClaim
    ↓
Pathfinder.begin(PathQuery)
    ↓
PathRoute
    ↓
start next edge through MovementSystem
    ↓
MovementAction
    ↓
MovementStepCompletion
    ├─ committed     → next route edge in the same tick
    └─ not committed → terminal unsuccessful MoveTo
```

`MoveToSystem` does not mutate Spatial and does not depend directly on Navigation, TransitionCost or Occupancy execution APIs. Every physical transition still passes through the ordinary Movement edge and its completion-time revalidation.

### Pathfinding computation and simulation time

In the first production consumer, a `PathSearch` is advanced deterministically in bounded expansion chunks until terminal without advancing the simulation clock between chunks.

This is deliberate: pathfinder algorithm/cache performance is computational cost, not actor travel time. Replacing the pathfinding implementation must not silently make an actor arrive later because one algorithm needed more search slices.

If representative profiling later proves synchronous searches too expensive, computational work scheduling must be designed without coupling CPU work to authoritative movement time.

### Disposable route semantics

A returned route is advice, not a reservation or promise. After `FOUND`, `MoveTo` does not maintain a second route-revision protocol. Each concrete edge is revalidated by Movement when it starts and completes.

Consequences are intentional in v1:

- a route may become invalid after several already committed steps;
- the object then remains at its last committed cell;
- a route may become suboptimal after the world changes and still continue while its edges remain executable;
- dynamic Occupancy on a later cell is handled only when that cell becomes the immediate next edge.

The first `MoveTo` does not embed AI policy for waiting, retrying, replanning, yielding or changing goals. An unsuccessful terminal outcome is returned to the higher-level consumer, which may choose a new intent.

## Diagnostics and tests

Current headless coverage proves, among other existing Movement behavior:

- source position remains authoritative until edge completion;
- MovementRate, transition length and fractional carry determine deterministic duration;
- one-edge Occupancy reservations are acquired/released correctly;
- stale structural transitions do not commit Spatial;
- open result codes pass through the command boundary without an exhaustive result mapping;
- `MoveTo` owns locomotion across its full route and blocks competing standalone movement;
- route edges chain without an extra orchestration tick;
- `NO_PATH` and source-equals-goal are terminal without advancing simulation time;
- a later occupied route edge stops at the last committed cell and releases route ownership;
- `MoveTo` and the equivalent manual edge sequence preserve timing/carry and Occupancy semantics;
- multi-Z `MoveTo` execution traverses RampShape geometry through the same one-edge Movement primitive;
- structural dependency guards prevent `MoveToSystem` from bypassing Movement to reach Spatial/Navigation/Occupancy/TransitionCost execution APIs directly.

## Deferred

- public cancellation and retained Scheduler task handles;
- immediate/reactive wake-up on world mutation;
- automatic waiting or replanning inside `MoveTo`;
- actor-specific terrain/locomotion affinity;
- falling, climbing, jumping, swimming and flying;
- path-wide or space-time reservations;
- yielding, swap/displacement, pushing, deadlock resolution and coordinated multi-agent movement;
- persistent route caches and moving-target tracking.

These are activated only by a concrete consumer, correctness requirement or measured workload; they are not hidden inside the current Movement contract.

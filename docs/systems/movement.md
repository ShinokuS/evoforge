# Movement

## Purpose

Execute concrete actor movement as deterministic timed simulation work and compose long-range `MoveTo` intent over the same one-edge execution primitive.

```text
Pathfinder        disposable route advice
        ↓
MoveTo            route-level locomotion intent
        ↓
Movement          one concrete timed edge
        ↓
Navigation        structural edge exists
TransitionCost    intrinsic edge price
Traversal constraint current mover/environment restriction
Occupancy         immediate destination claim
MovementRate      price -> actor time
Spatial           authoritative position commit
```

Movement does not own topology, object identity, Spatial storage, Scheduler internals, pathfinding algorithms, Occupancy state, rendering or AI policy.

## Result model

Expected impossibility is structured data. Movement operations expose the common floor:

```text
accepted
ResultCode
```

`ResultCode` is open namespaced data such as `movement:started`, `movement:destination_reserved` or `movement:traversal_restricted`. Generic Control and route orchestration do not maintain an exhaustive enum of every reason.

Broken programming/configuration/ownership invariants remain exceptions.

## External intent

Current command-level locomotion intents are:

```text
MoveStepCommand(objectId, destination XYZ)
MoveToCommand(objectId, goal XYZ)
CancelMoveToCommand(objectId)
```

`MoveStep` starts one adjacent timed attempt; it never teleports.

`MoveTo` accepts one long-range locomotion intent. Acceptance is not the same as eventual arrival: the route may be `NO_PATH`, become unusable later, or be cancelled. `MoveToCompletion` retains the bounded latest terminal observation for that object.

`CancelMoveTo` requests route-level cancellation. It does not tear an already scheduled atomic edge out of Scheduler/Occupancy state.

## One-edge start sequence

A concrete edge is accepted only after:

```text
object exists + is placed
    ↓
definition has MovementRate
    ↓
caller owns locomotion / no conflicting active action
    ↓
destination is one immediate neighbor
    ↓
Navigation exposes source -> destination
    ↓
current mover traversal constraint allows the edge
    ↓
shared TransitionCost is calculated
    ↓
MovementRate + fractional carry -> duration
    ↓
exclusive mover reserves the immediate destination through Occupancy
    ↓
MovementAction identity/state is created
    ↓
completion is scheduled
```

Rejected starts create no action state and consume no timing carry. If exceptional scheduling fails after an Occupancy claim, Movement rolls back the exact reservation before propagating the failure.

## Movement capability

`MovementRate` is immutable definition data measured in transition-cost units per simulation tick. Absence of the definition aspect means ordinary self-propelled Movement is unavailable.

Actor-specific environmental restrictions are separate. Current production composition supplies a `MoverTraversalConstraint` for Water wading; swimming/flying/climbing are not hidden inside MovementRate.

## MovementAction and authoritative position

A `MovementAction` represents exactly one scheduled edge:

```text
MovementActionId
ObjectId
source XYZ
destination XYZ
```

`MovementStateStore` owns per-object timing carry, the active one-edge action, any attached Occupancy reservation identity and long-lived movement claim identity.

Spatial remains authoritative while the action is in flight:

```text
before completion  Spatial = source
                   source derives OCCUPIED
                   immediate destination may be RESERVED

successful completion
                   Spatial = destination
                   reservation released
```

There is no authoritative fractional coordinate. Rendering interpolation, if used, is presentation-only.

## Long-lived MovementClaim

A route-level controller needs exclusive locomotion ownership even while no concrete edge is currently active, for example during planning or between child edges.

`MovementClaimId` is an opaque token with:

```text
ObjectId -> 0..1 active MovementClaim
```

`MoveTo` acquires one claim for its whole lifetime. Standalone `MoveStep` does not create a long-lived claim; its active edge supplies one-step exclusivity. A stale owner cannot release a newer claim because release requires the exact claim identity and object.

This contract is generic enough for future route-level controllers without adding `if MoveTo/Follow/Flee` branches to Movement state.

## Timing and carry

Repeated per-edge ceiling would bias long-run speed. Movement carries integer remainder across accepted concrete edges.

The current deterministic timing is equivalent to accumulating transition-cost remainder against the actor's `MovementRate`, with every edge clamped to at least one simulation tick.

For `cost = 1000` and `rate = 300`, repeated equal edges produce:

```text
step 1: 3 ticks, carry 100
step 2: 3 ticks, carry 200
step 3: 4 ticks, carry   0
```

Carry belongs to the object's Movement state, so a MoveTo route and the equivalent sequence of manual concrete edges use the same timing physics.

## Completion-time revalidation

Scheduled completion reloads the active action and revalidates current execution facts, including:

- object still exists and remains at the recorded source;
- Navigation still exposes the edge;
- current mover traversal constraint still allows the edge;
- exact Occupancy reservation/commit conditions remain valid.

Only a valid completion commits Spatial. Otherwise the object stays at its last committed cell. Normal terminal paths still release Movement-owned reservation/action state before completion is observed.

A sleeping action currently discovers world invalidation at its scheduled completion rather than through reactive early wake-up.

## Synchronous child completion

`MovementActionProcessor` finishes Movement-owned work first:

```text
commit Spatial if valid
release exact reservation
remove MovementAction
```

Only then does it publish a narrow synchronous `MovementStepCompletion` through `MovementStepCompletionRelay`.

A committed child edge may let MoveTo start the next edge in the same simulation tick. This adds no artificial route idle tick and remains safe because every concrete edge lasts at least one tick and Scheduler processes a fixed due batch.

## Long-range MoveTo

`MoveToSystem` orchestrates existing systems; it is not a second physical movement implementation.

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
MovementStepCompletion
    ├─ committed     -> next edge
    └─ not committed -> terminal unsuccessful MoveTo
```

It never mutates Spatial and does not bypass Movement to reach Navigation/TransitionCost/Occupancy execution APIs.

### Mover-aware advisory planning

Production MoveTo composes query-local mover constraints (currently Water wading) into `PathQuery`. This can avoid currently restricted edges during route advice.

The route is still disposable. Every real edge is revalidated by Movement at start and completion; later cells are not reservations or promises.

### Computational search versus simulation time

The first production consumer advances a `PathSearch` in deterministic expansion chunks until terminal without advancing simulation time between chunks. Pathfinder CPU cost is not actor travel time.

If representative profiling later requires background/resumable computation across simulation ticks, that scheduling must be designed explicitly rather than making actor speed depend on algorithm runtime.

## Cancellation semantics

`MoveToSystem.cancel(objectId)` implements safe route-level cancellation.

If no concrete child edge is active, cancellation completes immediately and releases the MovementClaim.

If an atomic edge is already scheduled:

```text
cancel requested
    ↓
current MovementAction is allowed to complete normally
    ↓
no next route edge is started
    ↓
MoveTo finishes with movement:move_to_cancelled
    ↓
route claim is released
```

Therefore a cancellation may move the object by **at most the already accepted current edge**. It never leaves an Occupancy reservation or scheduled Movement action orphaned.

Mid-edge cancellation that would revoke/rewrite already scheduled atomic work is not implemented.

## Diagnostics and tests

Headless coverage includes deterministic edge timing/carry, Occupancy reservation lifecycle, topology and traversal revalidation, MoveTo ownership and chaining, `NO_PATH`/source-equals-goal terminals, stale/blocked later route edges, multi-Z Ramp execution, Water-aware planning/commit checks, open result propagation and safe route-level cancellation.

The visualizer exposes Move/Cancel Move through cell-centric object interaction and reads active MoveTo routes through the authoritative read projection rather than invoking its own execution logic.

## Deferred

- mid-edge atomic-action cancellation and retained Scheduler task handles;
- immediate/reactive wake-up on world mutation;
- automatic waiting/replanning/yielding inside MoveTo;
- actor-specific terrain affinity beyond current Water-wading constraints;
- falling, climbing, jumping, swimming and flying;
- path-wide/space-time reservations;
- swap/displacement, pushing, deadlock resolution and coordinated multi-agent movement;
- persistent route caches and moving-target tracking.

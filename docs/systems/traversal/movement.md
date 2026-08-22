# Movement

## In plain language

Movement is the system that turns “go to the neighboring cell” into a **real timed physical action**.

Pathfinding may suggest a route, but a route is only advice. For every individual step Movement checks that the structural edge still exists, the actor is allowed to use it, the destination can be claimed, calculates how long the step takes, waits through simulation time, then checks everything important again before committing the object's authoritative Spatial position.

`MoveTo` builds long-distance travel by repeating that same one-edge primitive. It never teleports an actor along a precomputed route.

## Current status

The production traversal chain is:

```text
Pathfinder          disposable long-range route advice
     ↓
MoveToSystem        owns route-level locomotion intent
     ↓
MovementSystem      owns one concrete timed edge
     ↓
Navigation          structural edge exists?
TransitionCost      intrinsic edge price
Mover constraint    actor/environment currently allows edge?
Occupancy           immediate destination can be claimed?
MovementRate        convert price into actor time
Spatial             commit authoritative position
```

Movement does not own topology, path search, Spatial storage, Occupancy storage, Scheduler internals, rendering or AI policy.

## External movement intents

Current Control-facing intents are:

```text
MoveStepCommand(objectId, destinationXYZ)
MoveToCommand(objectId, goalXYZ)
CancelMoveToCommand(objectId)
```

### `MoveStep`

Starts at most one adjacent structural edge. Accepted means the timed edge was started, not that it already completed.

### `MoveTo`

Accepts route-level locomotion intent. It may later reach the goal, return `NO_PATH`, fail on a later changed edge, or be cancelled.

### `CancelMoveTo`

Stops future route continuation. If an atomic edge is already accepted/scheduled, that edge may still complete; cancellation prevents the next route edge from starting.

## One-edge start algorithm

A concrete edge is accepted only after this order of checks/work:

```text
1. object exists and is spatially placed
2. object definition has MovementRate
3. locomotion ownership is available / caller owns required claim
4. destination is one immediate neighboring coordinate
5. Navigation exposes source -> destination
6. current mover traversal constraint allows the edge
7. TransitionCost is calculated from authoritative structural/material facts
8. MovementRate + timing carry calculate duration
9. exclusive mover attempts immediate destination Occupancy reservation
10. MovementAction state is created
11. completion activation is scheduled
```

If an expected check fails, Movement returns a structured rejection and does not mutate timing carry or leave action/reservation state.

If an exceptional scheduling/action-creation failure happens after a reservation was acquired, Movement rolls back the exact reservation before propagating the exception.

## Movement capability

`MovementRate` is immutable definition data measured in **transition-cost units per simulation tick**.

A definition with no Movement rate does not have ordinary self-propelled Movement under the current model.

Movement rate is independent from dynamic environmental restrictions. Current production separately composes Water wading; future swimming/climbing/flying would be separate locomotion semantics rather than hidden multipliers inside `MovementRate`.

## MovementAction state

One in-flight edge owns a `MovementAction` containing:

```text
MovementActionId
ObjectId
source XYZ
destination XYZ
```

`MovementStateStore` also owns per-object fractional timing carry and the exact reservation/claim identities associated with Movement-owned work.

## Authoritative position while moving

Spatial does not slide continuously between cells.

```text
edge starts
    Spatial = source
    source is physically OCCUPIED
    destination may be RESERVED

edge is in flight
    Spatial still = source

completion succeeds
    Spatial = destination
    reservation released
```

Any interpolation is presentation-only.

This means all authoritative systems see one unambiguous discrete position at every tick.

## Long-lived locomotion claim

`MoveTo` needs to retain locomotion ownership while planning and between child edges. A route-level `MovementClaimId` provides exactly that.

```text
ObjectId -> zero or one active MovementClaim
```

`MoveTo` acquires a claim for its whole lifetime. A standalone `MoveStep` does not need a long-lived claim because the active edge itself supplies immediate exclusivity.

Release requires the exact claim identity so stale route/controller state cannot release a newer claim.

This is a generic movement-ownership concept rather than a `MoveTo`-specific boolean.

## Timing and fractional carry

A naïve calculation that independently rounds every edge upward would systematically slow actors. EvoForge instead preserves integer remainder between accepted edges.

For intrinsic edge cost `c`, actor rate `r`, and previous carry `q`, the implementation is equivalent in behavior to accumulating exact cost against the rate and retaining the remainder while requiring at least one tick per edge.

A representative equal-edge sequence:

```text
cost = 1000
rate = 300

edge 1 -> 3 ticks, carry 100
edge 2 -> 3 ticks, carry 200
edge 3 -> 4 ticks, carry   0
```

Across the three edges:

```text
total cost = 3000
total time = 10 ticks
```

which matches the long-run ratio `3000 / 300 = 10` rather than repeatedly applying an independent ceiling.

Timing carry belongs to Movement state, so a route executed through `MoveTo` and the same accepted edge sequence issued manually share the same movement-time physics.

Rejected starts do not consume carry.

## Completion-time revalidation

A scheduled Movement completion does not blindly commit an old promise. It reloads the active action and rechecks current authoritative facts including:

- the object still exists;
- the object is still at the recorded source;
- Navigation still exposes the directed edge;
- current mover/environment traversal policy still allows the edge;
- the exact Occupancy reservation/commit conditions still belong to this action.

If all checks succeed:

```text
Spatial.move(source -> destination)
release reservation
remove MovementAction
publish step completion
```

If an expected world change invalidates the action:

```text
Spatial remains at source
release Movement-owned reservation
remove MovementAction
publish unsuccessful completion
```

The current model discovers such invalidation when the sleeping action wakes at its scheduled completion. Reactive early wake-up is deferred.

## Completion relay and same-tick chaining

`MovementActionProcessor` finishes Movement-owned state first:

```text
commit if valid
release reservation
remove action
```

Only then does it publish a narrow synchronous `MovementStepCompletion` through the completion relay.

`MoveTo` may respond to a committed step by starting the next child edge in the same simulation tick. That does not recursively complete the new edge, because every edge duration is at least one tick and Scheduler dispatch uses a fixed due batch.

## MoveTo algorithm

`MoveToSystem` is route orchestration, not another movement engine:

```text
MoveTo request
    ↓
acquire MovementClaim
    ↓
Pathfinder.begin(PathQuery)
    ↓
advance deterministic search until terminal
    ↓
PathRoute
    ↓
MovementSystem.startStep(next edge)
    ↓
MovementStepCompletion
    ├─ committed -> start next advised edge
    └─ failed    -> terminate MoveTo
```

`MoveTo` never mutates Spatial directly and does not execute Navigation/Occupancy transitions itself.

### Computational search does not consume actor travel time

Current production MoveTo advances resumable `PathSearch` in deterministic expansion chunks but continues those chunks to a terminal search result without advancing simulation ticks between them.

Pathfinder CPU work is not actor movement time.

If future profiling justifies asynchronous/background path computation, its simulation-time visibility must be designed explicitly rather than allowing machine speed to change actor speed.

## Advisory mover-aware routing

MoveTo can compose query-local `PathTransitionConstraint`s. Production uses the same mover Water-wading semantics to avoid already-overdeep destinations during planning.

This remains **advice**:

```text
query-time constraint can avoid a currently bad edge
        ↓
route returned
        ↓
Movement still rechecks each edge at start and completion
```

A route never reserves every future cell.

`MoverDestinationAccessResolver` provides an even cheaper local necessary-condition check: a non-current destination is locally enterable only if at least one structural incoming edge also passes current mover policy. It does not prove connectivity from the actor's location and is not a second pathfinder.

## Cancellation semantics

If no child edge is active, route cancellation completes immediately and releases the route claim.

If one edge is already in flight:

```text
cancel requested
     ↓
current atomic edge may finish normally
     ↓
no next child edge starts
     ↓
MoveTo terminal = cancelled
     ↓
MovementClaim released
```

Therefore cancellation may move the actor by **at most the already accepted current edge**.

This avoids orphaned scheduler work/reservations and preserves the atomic-edge contract.

## Result model

Expected Movement impossibility is structured data using the common `accepted + ResultCode` model. Codes are open namespaced values such as:

```text
movement:started
movement:destination_reserved
movement:traversal_restricted
movement:move_to_cancelled
```

Broken internal/configuration invariants remain exceptions.

## Invariants

- Movement executes one immediate edge at a time.
- Pathfinding/MoveTo never teleport or mutate Spatial directly.
- In-flight Spatial remains at source until successful completion.
- Accepted exclusive edges own only the immediate destination reservation.
- Rejected starts leave no reservation/action and consume no timing carry.
- Start and completion both revalidate current structural/mover facts.
- Timing uses persistent remainder so long-run speed is unbiased by per-edge rounding.
- Route advice is disposable; each real edge is authoritative only when Movement accepts it.
- Route cancellation never orphans an already scheduled atomic edge.

## Current limitations

Not yet implemented:

- mid-edge interruption/task cancellation;
- reactive early wake-up from world mutation;
- automatic wait/replan/yield inside MoveTo;
- falling, climbing, jumping, swimming or flying;
- actor-specific surface affinities beyond current Water wading;
- path-wide/space-time reservations;
- swaps/pushing/displacement/deadlock policy;
- persistent/moving-target route tracking.

## Code and tests

Primary code lives under:

```text
simulation/.../world/mechanics/movement/
simulation/.../world/navigation/pathfinding/   route advice
```

Headless coverage includes timing/carry, reservation lifecycle, start/commit revalidation, route ownership/chaining, cancellation, ramps, mover constraints and multi-agent contention. Visualizer movement tools invoke the same Control/domain path and only observe active state.

## Sources

**Internal EvoForge design.** Timed atomic-edge execution, carry, ownership and route orchestration are project mechanics.

Path search itself follows A* lineage; see [Pathfinding](pathfinding.md) and [References](../../references.md).

See [Navigation](navigation.md), [Transition Cost](traversal-cost.md), [Occupancy](occupancy.md), [Water Traversal](water-traversal.md), [Spatial](../foundations/spatial.md), and [Time](../foundations/time.md).

# Glossary

This page defines EvoForge project terms. The goal is to keep architectural discussions precise: several words have narrower meanings here than in ordinary game-development conversation.

## Authoritative state

The single source of truth for a mutable simulation fact.

Examples:

```text
ObjectRepository -> object existence
SpatialSystem     -> object XYZ
TerrainSystem     -> terrain definition at XYZ
GeometrySystem    -> non-default Shape override
MovementStateStore -> active ordinary movement + timing carry
Scheduler         -> scheduled activation infrastructure
```

Derived indexes and caches are not separate authorities.

## Owner

The subsystem responsible for validating and mutating one authoritative property.

Ownership does not mean other systems cannot read the property. It means they must not maintain competing mutable copies of the same semantic fact.

## `ObjectId`

Stable runtime identity of one individual WorldObject during its lifetime, currently represented with slot/generation semantics.

A stale id never silently resolves to a later object reusing the same slot.

## Definition

Immutable compiled description of a content type.

Source definitions use stable string keys. Runtime systems use typed definition ids.

Examples of current mechanic-owned compiled definition data:

```text
ObjectDefinitionId -> MovementRate
LandscapeDefinitionId -> SurfaceTraversalCost
```

## Definition aspect

One composition fragment owned by a mechanic.

Current examples:

```text
physical
movement
traversal
```

The generic definition loader dispatches aspects to registered compilers rather than containing every mechanic field itself.

## Terrain

Base landscape content at one XYZ coordinate.

Representation:

```text
XYZ -> LandscapeDefinitionId | absence
```

Terrain is not a WorldObject.

## Terrain absence

No terrain exists at a coordinate. It is represented by absence/null in the current read contract, not by an `air` or `empty` definition.

## `LandscapeMutations`

The coordinated landscape write capability used when one logical terrain-lifecycle operation must keep Terrain and Geometry coherent.

It is distinct from low-level ownership: `TerrainSystem` still owns terrain storage; `LandscapeSystem` coordinates multiple owners from above.

## Shape

Immutable local geometry object anchored at a terrain coordinate.

A Shape currently contributes:

```text
transition departures
transition arrivals
transition blocks
intrinsic directed departure traversal factor
intrinsic directed arrival traversal factor
```

Shape is context-free: it does not query World, neighboring Shapes, moving ObjectId, Navigation, Occupancy or Pathfinder.

## Terrain anchor

The terrain XYZ coordinate whose geometry a Shape describes.

For current solid Shapes, the primary standing position is one Z coordinate above the anchor.

## Standing position

A navigation/object position supported by terrain geometry.

Current production Shapes use:

```text
standing = terrain anchor + (0,0,1)
```

This is a current structural Shape-model convention, not an eternal restriction on all future geometry.

## Relative source

Navigation source position expressed relative to one Shape anchor:

```text
relative source = source XYZ - Shape anchor XYZ
```

Shape topology and Shape traversal-factor queries use this same coordinate system.

## Transition direction

One of the 26 non-zero immediate-neighbor deltas in the 3D grid:

```text
dx, dy, dz in [-1,1]
not (0,0,0)
```

## Departure

A Shape contribution stating that a directed structural transition may leave the source position supported by that Shape.

In current TransitionCost semantics, the same source-supporting Shape owns the departure traversal factor for that edge.

A departure alone does not create an edge.

## Arrival

A Shape contribution confirming that a directed transition may end at the standing position supported by that Shape.

In current TransitionCost semantics, the same destination-supporting Shape owns the arrival traversal factor for that edge.

An arrival alone does not create an edge.

## Block

A local Shape contribution declaring that a direction is geometrically obstructed.

Transition resolution always applies blocks after departure/arrival matching.

## Transition algebra

The generic composition rule:

```text
resolved = departures & arrivals & ~blocks
```

Contributions from multiple Shapes are OR-accumulated before this resolution.

## Structural edge

A directed immediate-neighbor connection produced by Navigation from current Geometry.

Structural means the geometry supports the edge. It does not mean every actor can necessarily use it, that the destination is unoccupied, or that the edge is cheap.

## Navigation

The structural-adjacency subsystem.

Current public query:

```text
XYZ -> 26-bit structural transition mask
```

Navigation does not know actor identity, MovementRate, TransitionCost, Occupancy or Pathfinder.

## `GridTransitionLength`

Actor-independent fixed-point length of an immediate 3D grid direction.

Current values:

```text
cardinal / one changed axis         -> 1000
2-axis diagonal                     -> 1414
3-axis diagonal                     -> 1732
```

It belongs to the direction itself, not terrain material or a concrete Shape.

## `ShapeTraversalFactor`

Fixed-point intrinsic geometry multiplier contributed by one Shape for one directed departure/arrival role.

Current scale:

```text
NONE    = 0
NEUTRAL = 1000
```

It is actor-independent and follows the same role ownership as topology ports.

## `SurfaceTraversalCost`

Positive actor-independent base traversal price associated with a `LandscapeDefinitionId` through the landscape `traversal.cost` aspect.

Current neutral baseline is `1000` units.

## `TransitionCost`

Positive actor-independent intrinsic price of one **already-valid directed structural edge**.

Current conceptual formula for `A -> B`:

```text
localA = surfaceCost(A) * departureFactor(shapeA, d)
localB = surfaceCost(B) * arrivalFactor(shapeB, d)

TransitionCost
    = lengthFactor(d) * average(localA, localB)
```

Movement consumes this price; future Pathfinder must consume the same semantics.

TransitionCost does not itself authorize topology and is currently independent of mover identity.

## `TransitionCostLookup`

Narrow read boundary that prices a directed adjacent transition.

It exists so Movement and future Pathfinder can share one edge-price model without depending on calculator internals.

## `MovementRate`

Positive immutable object-definition property measured in traversal-cost units per simulation tick.

It converts intrinsic TransitionCost into mover-specific duration. It does not redefine the edge's intrinsic price.

## Movement capability

The current ordinary self-propelled movement capability attached to an `ObjectDefinitionId` through the `movement.rate` aspect.

Absence of the aspect means `MoveStepCommand` cannot start ordinary Movement for that definition.

This is not yet the final universal locomotion capability model.

## `MoveStepCommand`

External intent to start one timed adjacent movement attempt for an object toward a specified neighboring XYZ.

Accepted means the MovementAction started; it does **not** mean Spatial already moved.

## `MovementAction`

Domain runtime state representing one active adjacent timed movement attempt.

Current action stores:

```text
MovementActionId
ObjectId
source XYZ
destination XYZ
```

Presence in `MovementStateStore` means active. Completed/interrupted actions are removed rather than retained as history.

## `MovementActionId`

Monotonic non-reused domain identity of one MovementAction.

It is distinct from Scheduler `TaskHandle`.

## Movement timing carry

Per-object integer remainder preserved across adjacent Movement actions so cost-to-tick conversion does not systematically lose fractional timing.

Conceptually:

```text
total = cost + carry
ticks = floor(total / rate)
carry = total mod rate
```

with minimum one tick per movement.

## Completion-time revalidation

Current Movement policy of checking the world again when the scheduled action completion wakes.

It verifies object/source/Navigation state before `SpatialSystem.move`. A terrain/geometry mutation during the dormant interval can therefore interrupt the action instead of allowing a stale commit.

## Dormant / sleeping Action

A timed domain process that does not run every simulation tick while waiting for its next scheduled activation.

Current MovementAction is dormant between start and completion.

## Simulation tick

The minimum discrete authoritative time step currently represented by `SimulationClock`.

A tick is simulation time, not a renderer frame and not a guarantee that every object acts once.

## `SimulationTime`

Read-only capability exposing the current simulation tick without authority to advance it.

## `SimulationStepper`

Production owner of current one-tick phase order:

```text
clock.advance()
Scheduler.dispatchDue(clock.tick())
```

Scenario and future presentation code drive this contract rather than inventing their own tick semantics.

## Scheduler

Infrastructure that owns when scheduled work becomes due and the deterministic routing/order of activation.

Scheduler does not own domain Action state or know what a process means.

## `ScheduledHandler`

Infrastructure callback registered for one process family. It receives an opaque `processId` and delegates/resumes domain logic.

Current Movement registers one handler for all MovementActions rather than one handler per mover.

## `ProcessScheduler`

Narrow domain-facing capability:

```text
scheduleAfter(delayTicks, processId)
```

It allows a domain system to schedule its own process without knowing `HandlerId` or mutable SimulationClock.

## `BoundProcessScheduler`

Infrastructure adapter binding one `ProcessScheduler` to:

```text
SimulationTime
Scheduler
one HandlerId
```

This is the intended connection pattern for future timed mechanics.

## `TaskHandle`

Infrastructure identity of one scheduled activation.

It is not the same as `ObjectId` or a domain process id such as `MovementActionId`.

## Occupancy

Future mechanic/policy describing temporary use/reservation of positions by objects.

It is intentionally separate from structural Navigation. Current Movement does not yet reserve destinations.

## Pathfinder

Future route-selection consumer of Navigation and `TransitionCostLookup`.

Pathfinder chooses a route; it does not define topology and does not become the authoritative movement mutator.

## Command

Immutable external intent submitted by Player/AI/scripts/scenarios/network adapters/debug tools through the Control boundary.

A Command is not an internal RPC requirement for every system-to-system mutation.

## Command delivery

Mechanism that gets a Command to `CommandDispatcher`.

Current delivery is synchronous. A future queued/asynchronous gateway may change delivery timing while reusing stable Command/Handler contracts.

Synchronous delivery does not imply synchronous domain completion: `MoveStepCommand` synchronously starts a timed Action that completes later.

## `CommandResult`

Control-visible result of a Command. It extends `OperationResult` and therefore exposes at least:

```text
accepted
namespaced ResultCode
```

## `OperationResult`

Minimal neutral observation contract shared by structured domain results and command results.

It deliberately does not contain every domain-specific field.

## `ResultCode`

Validated namespaced code such as:

```text
terrain:position_occupied
movement:already_moving
movement:transition_unavailable
```

There is no global enum of all domain failures.

## `OperationResults.requireAccepted`

Generic internal helper expressing that the caller expects a structured operation to succeed because of its own invariant.

It does not change the underlying operation into an exception-based API.

## Scenario fixture

Test-only deterministic arrange/start/control/read layer.

`ScenarioBuilder` may use controlled setup writes before `start()`. The running `ScenarioHarness` submits production Commands, advances production `SimulationStepper`, and exposes read-only observations rather than raw authoritative mutators.

## Cache

Derived state stored to avoid recomputation.

A cache is never a second authoritative owner. Current Navigation/TransitionCost have no persistent cache contract.

## Hot path

Code executed often enough under representative workload that allocation count, boxing, locality or algorithmic cost materially affects performance.

A path is not considered hot merely because it might become important later.

## Definition-driven content

Content that can be added through source definitions because existing mechanics already express its behavior.

Example: a new terrain material choosing another `traversal.cost` is content, not a new Movement mechanic.

## Mechanic

A semantic behavior/state owner such as Spatial, Terrain, Geometry, Movement, Health or Inventory.

A new mechanic is justified when existing semantic owners/contracts cannot express the required behavior without violating ownership boundaries.

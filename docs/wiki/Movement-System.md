# Movement System

Movement is the first production timed gameplay mechanic in EvoForge. It turns an accepted external intent to move one object to one adjacent structural position into a deterministic scheduled process, then revalidates the world before committing the authoritative Spatial mutation.

This page documents the current implemented contract in detail: ownership, command semantics, timing, Scheduler integration, `MovementRate`, active actions, transition costs, Shape traversal factors, completion-time revalidation, known gaps, and extension rules.

## Responsibility in one sentence

```text
Navigation decides whether an adjacent structural edge exists.
Traversal cost decides how expensive that edge is.
MovementRate converts that cost into simulation time.
Movement owns the timed action that eventually asks Spatial to commit the new position.
```

Movement does **not** own terrain topology, object identity, object position storage, Scheduler internals, pathfinding, occupancy, rendering, or AI intent.

## Package map

Current production types are split by responsibility:

```text
simulation/control/movement/
├── MoveStepCommand
├── MoveStepResult
└── MoveStepHandler

simulation/world/mechanics/movement/
├── MovementRate
├── MovementDefinitions
├── MovementDefinitionCompiler
├── MovementStartResult
├── MovementActionId
├── MovementAction
├── MovementStateStore
├── MovementSystem
└── MovementActionProcessor

simulation/world/mechanics/traversal/
├── SurfaceTraversalCost
├── LandscapeTraversalDefinitions
├── LandscapeTraversalDefinitionCompiler
├── TransitionCost
├── TransitionCostLookup
└── TransitionCostCalculator

simulation/time/
├── SimulationTime
├── SimulationClock
├── ProcessScheduler
├── BoundProcessScheduler
├── Scheduler
└── SimulationStepper
```

The separation is deliberate. Movement owns movement semantics; Traversal owns actor-independent edge price; Time owns activation timing; Spatial owns the authoritative object coordinate.

## External intent: `MoveStepCommand`

The current Control vertical slice represents exactly one adjacent movement request:

```text
MoveStepCommand(objectId, destination XYZ)
```

Its meaning is:

> start a timed attempt to move this object from its current authoritative position to this adjacent destination.

It does **not** mean “teleport the object now”. An accepted command starts a `MovementAction`; the position remains unchanged until that action completes.

The flow is:

```text
external controller
    ↓
MoveStepCommand
    ↓
SynchronousCommandGateway
    ↓
CommandDispatcher
    ↓
MoveStepHandler
    ↓
MovementSystem.startStep(...)
    ↓
MoveStepResult
```

Control remains thin. `MoveStepHandler` adapts external intent to the domain API; it does not calculate paths, durations or mutate Spatial directly.

## Structured start result

Movement start uses the shared structured-result floor.

Normal domain impossibilities are represented by `MovementStartResult` / `MoveStepResult` with `accepted()` and a namespaced `ResultCode`. Current start failures include conditions such as:

```text
movement capability unavailable
object not placed
object already moving
destination not adjacent
structural transition unavailable
```

Unknown/stale trusted runtime ids and broken bootstrap/configuration state remain programming/configuration errors rather than ordinary gameplay rejection results.

An accepted result means only:

```text
movement action successfully started
```

It does not mean the destination has already been committed.

## Movement capability and `MovementRate`

Movement capability is definition-backed rather than stored as a field on every `WorldObject`.

Object definition data uses the `movement` aspect:

```json
{
  "key": "core:walker",
  "aspects": {
    "movement": {
      "rate": 100
    }
  }
}
```

`MovementDefinitionCompiler` compiles this into `MovementDefinitions`:

```text
ObjectDefinitionId -> MovementRate
```

`MovementRate` is a positive integer measured in traversal-cost units per simulation tick.

Absence of a movement aspect means that this definition does not have the ordinary self-propelled movement capability used by `MoveStepCommand`.

Movement capability is intentionally still narrow. Swimming, flying, climbing, jumping, wheel/track behavior, stamina and surface affinities are not hidden inside `MovementRate`.

## Start-time validation

`MovementSystem.startStep` performs the current start checks in domain order:

```text
object exists
    ↓
object definition has MovementRate
    ↓
object has a Spatial transform
    ↓
object has no active MovementAction
    ↓
destination is one of the 26 immediate neighbors
    ↓
Navigation exposes that directed structural transition
    ↓
calculate TransitionCost
    ↓
convert cost to duration using MovementRate + carry
    ↓
create active MovementAction
    ↓
schedule completion
```

The destination delta must satisfy:

```text
dx, dy, dz ∈ [-1, 1]
not (0, 0, 0)
```

Movement never replaces Navigation with its own ramp/full/shape rules. If Navigation does not expose the edge, Movement rejects the start.

## Position semantics while moving

Movement is authoritative but discrete.

For an action:

```text
A -> B
```

with completion at tick 15:

```text
tick 0..14  Spatial position = A
tick 15     completion revalidation
tick 15     if still valid: Spatial position becomes B
```

There is no authoritative fractional coordinate between A and B.

The first debug renderer is expected to observe these discrete commits. Smooth interpolation is not part of the current simulation contract and is not required for correctness.

## `MovementAction`

A `MovementAction` is the runtime state for one accepted adjacent step.

It stores only what completion requires:

```text
MovementActionId
ObjectId
source XYZ
destination XYZ
```

It deliberately does not store a generic action status enum, history, renderer progress, wall-clock timestamps, or a path.

Presence in `MovementStateStore` means the action is active. Removal means it is no longer active.

## `MovementActionId`

`MovementActionId` is domain identity for a movement process.

It is monotonic and is not reused. This prevents a stale scheduled activation from accidentally resolving to a later movement action that happened to occupy the same numeric identity.

It is distinct from Scheduler `TaskHandle`:

```text
MovementActionId = identity of domain movement state
TaskHandle        = identity of scheduler infrastructure work
```

The current Movement slice does not propagate a `TaskHandle` because early cancellation is not yet a supported operation.

## `MovementStateStore`

`MovementStateStore` owns Movement runtime state, not history.

Conceptually it contains:

```text
per ObjectId:
    fractional timing carry
    active MovementActionId | none

per MovementActionId:
    active MovementAction
```

This supports two important invariants:

```text
one object cannot have two simultaneous ordinary movement actions
fractional timing precision survives across separate adjacent steps
```

Completed or interrupted actions are removed rather than retained indefinitely. Future diagnostics/replay/history should use a separate mechanism rather than turning the active-state store into an append-only log.

## Fractional timing carry

Per-step ceiling would systematically distort fast actors and diagonal movement. EvoForge therefore carries the integer remainder across steps.

For a transition cost `cost`, actor rate `rate`, and previous remainder `carry`:

```text
total = cost + carry
ticks = floor(total / rate)
carry = total mod rate
```

The implementation is arranged to avoid overflow from directly adding arbitrary long values, but it is mathematically equivalent to the formula above for valid state.

Movement duration is clamped to at least one simulation tick:

```text
duration >= 1 tick
```

This defines the current time resolution and prevents a chain of zero-duration moves from draining indefinitely inside one simulation tick.

Example for `cost = 1000`, `rate = 300`:

```text
step 1: 3 ticks, carry 100
step 2: 3 ticks, carry 200
step 3: 4 ticks, carry   0
```

Across three cells the actor spends 10 ticks, preserving the intended long-run rate instead of rounding every cell independently to four ticks.

Carry belongs to per-object Movement state, not to one `MovementAction`, because it must survive `A -> B`, then `B -> C`, then later steps.

## Scheduler boundary

Movement does not know `HandlerId`, does not register arbitrary scheduler handlers, and does not calculate an absolute completion tick itself.

It receives the narrow capability:

```java
ProcessScheduler.scheduleAfter(delayTicks, processId)
```

For Movement, `processId` is the numeric form of `MovementActionId`.

`BoundProcessScheduler` binds that narrow capability to:

```text
SimulationTime
Scheduler
one HandlerId
```

so Movement can say:

```text
wake movement action 17 after 10 ticks
```

without being able to schedule another domain's handler or mutate the simulation clock.

This is the intended reusable pattern for future timed mechanics:

```text
Domain start system
    ↓ scheduleAfter(delay, processId)
BoundProcessScheduler
    ↓
Scheduler
    ↓ when due
one registered domain ScheduledHandler
    ↓
Domain action/process owner resumes processId
```

There is one handler per process family, not one handler per action and not one global switch over all gameplay process types.

## Production simulation step

`SimulationStepper` is the production owner of the current one-tick phase ordering.

The current step is:

```text
1. advance SimulationClock by one tick
2. dispatch the Scheduler batch due at the new tick
```

Scenario tests and future presentation code drive this production contract; they do not define their own interpretation of a simulation tick.

One call to `ScenarioHarness.advanceTicks(10)` is only ten calls to the same production step. Batching the calls does not change authoritative semantics.

Wall-clock FPS is outside this contract. Running more simulation ticks per real second changes how quickly a user observes simulation time passing, not the deterministic result of a given tick sequence.

## Completion-time revalidation

Scheduled completion does not blindly commit the destination.

`MovementActionProcessor` reloads the active action and checks the world again:

```text
object is still alive
object still has a transform
object is still at the recorded source
Navigation still exposes source -> destination
```

Only then does it call:

```text
SpatialSystem.move(objectId, destination)
```

After either successful completion or interruption, the active action is removed.

This keeps Spatial ownership intact: Movement decides that the movement is authorized; Spatial performs the authoritative position mutation and updates its indexes.

## Sleeping action semantics

Between start and scheduled completion the action is intentionally dormant.

If the world changes on an intermediate tick, the current Movement slice does not wake immediately. It notices the change at completion revalidation.

Example:

```text
tick 100  movement A -> B starts
tick 110  support for B is removed
tick 115  movement completion wakes
tick 115  Navigation no longer exposes A -> B
tick 115  action is removed; object remains at A
```

This is a deliberate first-slice tradeoff, not an accidental omission. A future event-driven invalidation mechanism may wake affected processes earlier if a real consumer justifies that complexity.

## Transition cost overview

The timed Movement foundation originally used only grid direction length as the cost source. The current implementation now has an explicit actor-independent `TransitionCost` model shared as a narrow read capability.

For one directed adjacent transition `A -> B`:

```text
localA = surfaceCost(A) * departureFactor(shapeA, d)
localB = surfaceCost(B) * arrivalFactor(shapeB, d)

TransitionCost(A -> B)
    = lengthFactor(d)
      * average(localA, localB)
```

In fixed-point form, factors are scaled by 1000 and the implementation performs the combined calculation with integer arithmetic and one deterministic half-up rounding at the final transition-cost boundary.

The model deliberately uses **both** supported cells rather than charging only the destination.

For a path:

```text
A -> B -> C
```

with neutral Shape factors and cardinal length:

```text
cost(A->B) = A/2 + B/2
cost(B->C) = B/2 + C/2
```

so interior cell B contributes one full surface cost across the two neighboring transitions.

## Surface traversal cost

Base surface price belongs to the landscape definition, not to `Shape` and not to the actor.

Landscape definition data uses the `traversal` aspect:

```json
{
  "key": "core:granite",
  "aspects": {
    "traversal": {
      "cost": 1000
    }
  }
}
```

`LandscapeTraversalDefinitionCompiler` produces:

```text
LandscapeDefinitionId -> SurfaceTraversalCost
```

`1000` is the current neutral baseline. Examples of future content may choose lower or higher positive values without modifying Movement or the calculator.

Missing traversal data for terrain that participates in a valid Movement transition is treated as broken definition/bootstrap configuration, not a normal gameplay rejection.

## Grid transition length

Direction length is independent of terrain material and Shape-specific traversal characteristics.

`GridTransitionLength` uses fixed-point values:

```text
cardinal / one changed axis          = 1000   ≈ 1
double diagonal / two changed axes  = 1414   ≈ √2
triple diagonal / three axes        = 1732   ≈ √3
```

This prevents diagonal movement from receiving the same time as a shorter cardinal transition solely because both connect immediate grid neighbors.

The length term is part of the transition itself. It does not belong to a terrain definition and does not belong to a concrete Shape.

## Shape traversal factors

`Shape` now exposes two directed traversal-characteristic methods in addition to topology:

```text
departureTraversalFactor(...)
arrivalTraversalFactor(...)
```

These are intrinsic geometry contributions, not actor-specific movement policy.

The factor scale is:

```text
ShapeTraversalFactor.NONE    = 0
ShapeTraversalFactor.NEUTRAL = 1000
```

A positive factor scales the local surface contribution. For example, a hypothetical geometry factor of `1250` means `1.25x` local traversal price for that role/direction.

### Same role law as topology

Traversal factors obey exactly the same ownership law as Shape ports.

For `A -> B` with direction `d`:

```text
source support Shape:
    queried as departure owner
    relative source = (0,0,1)
    direction = d

destination support Shape:
    queried as arrival owner
    relative source = (0,0,1) - d
    direction = d
```

The source Shape never computes a cost for the destination Shape. The destination Shape never inspects the source terrain. Each contributes only its own local geometric characteristic.

The default Shape implementation derives whether a factor exists from the Shape's own `transitionPorts`. A transition role that the Shape does not own returns `ShapeTraversalFactor.NONE`; an owned role is neutral unless the Shape implementation deliberately overrides the factor.

This makes the cost contract structurally parallel to the existing departure/arrival topology contract and prevents a central `instanceof RampShape` / `switch(shapeType)` cost table.

### Current production Shapes

`FullShape` and the current cardinal `RampShape` use the neutral default traversal factor for the structural roles they expose.

This is deliberate. Ramp topology already changes the actual grid direction, including Z changes where appropriate, so `GridTransitionLength` already accounts for the corresponding geometric displacement. No arbitrary additional uphill/downhill effort multiplier is invented yet.

If a real future mechanic requires intrinsic shape-specific effort, that Shape can override its local factor without modifying `TransitionCostCalculator`. Actor-specific differences such as “wheeled object dislikes stairs” are a separate future capability interaction and are not encoded as a universal Shape factor today.

## Why TransitionCost is actor-independent today

The current formula does not receive `ObjectId`, `MovementRate`, species, locomotion mode or other actor capability.

Therefore different actors currently agree on which edge is intrinsically cheaper; `MovementRate` only changes how long the same cost takes.

For example, a horse with twice the rate of a human traverses every identical cost in roughly half the simulation time, subject to one-tick resolution and deterministic carry.

The current model intentionally does **not** claim that a swamp creature prefers mud while a human prefers a road. That requires an actor/surface interaction and will be designed only when a real consumer needs it.

## Navigation versus TransitionCost

The ordering is strict:

```text
Navigation: does A -> B structurally exist?
TransitionCost: what is the intrinsic price of that already-valid edge?
Movement: how long does this actor take to execute it?
```

`TransitionCostCalculator` does not create or authorize edges. `MovementSystem` first asks Navigation and only calculates cost after the directed edge exists.

This prevents cost configuration from silently becoming topology.

Future Pathfinder must consume the **same** transition-cost semantics rather than maintain a second independent price table. That is the reason `TransitionCostLookup` is a narrow reusable read boundary.

## Current fixed-point arithmetic

Authoritative cost/timing arithmetic uses integers.

Current scales:

```text
surface neutral cost     = 1000 units
Shape factor neutral     = 1000
Grid length scale        = 1000
```

The conceptual formula is evaluated as one positive integer numerator and fixed denominator, then rounded half-up once to obtain positive `TransitionCost.units`.

This avoids `double` in authoritative cost calculation and avoids repeated intermediate rounding of each component.

Movement then applies its separate per-object carry when converting transition-cost units to ticks.

## Error boundary

Normal world-state rejection remains structured. Broken invariants/configuration are exceptional.

Examples of normal rejection:

```text
already moving
not adjacent
Navigation transition unavailable
movement capability absent
```

Examples of exceptional state:

```text
trusted ObjectId is unknown
valid structural edge reaches support terrain with no traversal definition
cost calculation finds no source/destination support Shape for the current single-standing-position contract
integer configuration overflows supported arithmetic
```

Failing loudly on the latter prevents deterministic simulation from silently inventing fallback prices.

## Known current gaps

### Occupancy and reservation

Movement does not yet reserve a destination and does not yet consult a domain Occupancy system.

Two actors can therefore start toward the same structurally valid destination. Multi-agent destination conflict is a known deferred responsibility of the Occupancy milestone, not something to hide inside structural Navigation.

### Early cancellation

There is currently no public `cancel movement` operation and Movement does not retain Scheduler `TaskHandle` values.

Actions normally leave the store when their scheduled completion runs. Forced interruption sources such as death, stun or displacement will define cancellation semantics when they become real consumers.

### Immediate reaction to world mutation

An active movement does not subscribe to terrain/geometry mutation events. It revalidates when scheduled completion wakes.

### Actor-specific surface affinity

Surface/locomotion interactions are deferred. Current TransitionCost is actor-independent.

### Pathfinder / `MoveTo`

The current command executes one adjacent step only. There is no route planner and no long-lived `MoveTo` process yet.

A future route executor should use the same one-edge Movement semantics and revalidate each edge rather than teleport along a precomputed path.

## Scenario fixture integration

The test-only Scenario fixture composes the real production movement path.

Arrange phase can:

```text
register landscape definitions with traversal costs
register object definitions
assign MovementRate
place terrain
set Shapes
create and place objects
```

After `start()` the harness can:

```text
submit MoveStepCommand
advance one production tick
advance N production ticks
read object transforms
read terrain / geometry / navigation
```

The running harness still does not expose raw authoritative mutation systems merely to make tests convenient.

Scenario coverage includes:

```text
position remains at source until completion
different MovementRate values finish at different ticks
diagonal length changes duration
fractional carry remains deterministic across steps
second active movement is rejected
missing movement capability is rejected
invalid structural transition is rejected
completion revalidation interrupts stale edges
surface traversal cost changes duration
Shape traversal factor changes duration
advanceTicks(N) matches N calls to advance()
```

## Extending terrain costs

To add a new landscape material with a different intrinsic traversal cost:

```text
1. add/change the definition's traversal.cost
2. load it through LandscapeTraversalDefinitionCompiler
3. add content/integration tests for the intended relative cost
```

Do not modify `MovementSystem`, Navigation or `TransitionCostCalculator` for each terrain material.

## Extending Shape traversal characteristics

If a new Shape has an intrinsic geometry multiplier that is not already represented by grid direction length:

```text
1. implement its normal transitionPorts / transitionBlocks
2. override only the relevant departure/arrival traversal factor
3. preserve the same local relative-coordinate role law
4. add role-contract and transition-cost tests
```

Do not add:

```text
if (shape instanceof NewShape)
switch (shapeType)
central map of every concrete Shape
```

inside Movement or the calculator.

## Future Pathfinder contract

When Pathfinder arrives, it should conceptually ask:

```text
Navigation -> candidate structural outgoing edges
TransitionCostLookup -> price of each valid edge
```

Pathfinder then chooses a route. Movement remains responsible for actually executing and revalidating one edge at a time.

This separation allows path planning and real execution to agree on price without letting Pathfinder become an authoritative movement mutator.

## Stable invariants

The current Movement architecture should preserve these rules:

```text
1. MoveStepCommand starts an action; it does not immediately move Spatial state.
2. At most one ordinary MovementAction is active per object.
3. Spatial remains the sole authoritative owner of object XYZ.
4. Navigation remains structural and actor-independent.
5. TransitionCost is calculated only for an already-valid adjacent structural edge.
6. Surface cost belongs to LandscapeDefinitionId data.
7. Shape contributes only its own departure/arrival traversal characteristic.
8. No central cost logic branches on concrete Shape type.
9. MovementRate converts cost to time; it does not redefine edge price.
10. Fractional timing carry persists per object across steps.
11. Every movement lasts at least one simulation tick.
12. Scheduler knows when/handler/process id, not movement semantics.
13. Movement does not know HandlerId or absolute completion tick.
14. Completion revalidates before Spatial commit.
15. Completed/interrupted active actions are removed from Movement state.
16. Wall-clock/render FPS does not define authoritative movement timing.
17. Future Pathfinder must reuse the same transition-cost semantics.
```

## Related documentation

- [Navigation](Navigation.md) — structural adjacency.
- [Shape Contract](Shape-Contract.md) — local Shape role law and traversal-factor ownership.
- [Spatial System](Spatial-System.md) — authoritative object position storage.
- [Time and Scheduler](Time-and-Scheduler.md) — event-driven activation and production stepping.
- [Control Backbone](Control-Backbone.md) — external intent and structured command results.
- [Definitions](Definitions.md) — composition-driven object and landscape aspects.
- [Roadmap and Deferred Decisions](Roadmap-and-Deferred-Decisions.md) — Occupancy, Pathfinder, visualizer and later movement work.

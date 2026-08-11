# EvoForge Technical Reference

This file describes the **current implementation**. It may change after ordinary pull requests without changing the stable semantic architecture in `ARCHITECTURE.md`.

Baseline: Java 21, libGDX presentation modules plus a pure-Java `simulation` module.

For the long-form timed movement walkthrough, formulas, invariants and extension guidance, see `docs/wiki/Movement-System.md`.

## 1. Modules

```text
core/        libGDX application/presentation layer
lwjgl3/      desktop launcher
simulation/  deterministic simulation/domain code without libGDX
assets/      definitions and presentation assets
docs/        architecture, technical reference and long-form Wiki source
```

The `simulation` module is the authoritative architecture target. Presentation must not become the owner of simulation state.

## 2. Implemented simulation areas

```text
io.github.evoforge.simulation
├── result/
├── control/
│   ├── core/
│   ├── sync/
│   ├── terrain/
│   └── movement/
├── definition/
├── time/
└── world/
    ├── World
    ├── object/
    │   └── definition/
    ├── spatial/
    │   └── indexes/
    ├── landscape/
    │   ├── LandscapeMutations
    │   ├── LandscapeSystem
    │   ├── definition/
    │   └── terrain/
    │       └── storage/
    ├── mechanics/
    │   ├── physical/
    │   ├── geometry/
    │   ├── movement/
    │   └── traversal/
    └── navigation/
```

Future packages are not created merely to reserve names.

## 3. Objects and identity

Implemented foundation includes:

- `ObjectId` with slot/generation identity semantics;
- `WorldObject` as a small domain object containing identity + definition identity;
- `ObjectRepository` for existence/identity;
- read-only `ObjectLookup`;
- `ObjectFactory` for definition-backed creation;
- object definitions compiled separately from mutable runtime state.

`ObjectRepository` is not used as a generic bag of mechanics.

Movement rate, position, active movement state and timing carry are deliberately **not** fields added to `WorldObject`.

## 4. Definitions

Definitions are composition-driven and compiled during bootstrap.

Current conventions:

- source keys use stable string form such as `namespace:name`;
- runtime systems use typed ids;
- runtime numeric ids are not persistence identity;
- loaders resolve definitions in deterministic startup flow;
- mechanics own their own compiled definition data;
- adding content that uses existing mechanics should normally require data only.

Current roots:

```text
assets/definitions/object/
assets/definitions/landscape/
```

### 4.1 Object `movement` aspect

Current ordinary movement capability is definition-backed:

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

Implementation:

```text
MovementDefinitionCompiler
    -> MovementDefinitions
    -> ObjectDefinitionId -> MovementRate
```

`movement.rate` must be a positive integer measured in transition-cost units per simulation tick.

Absence of the aspect means ordinary self-propelled `MoveStep` capability is unavailable.

### 4.2 Landscape `traversal` aspect

Current actor-independent surface price is landscape-definition data:

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

Implementation:

```text
LandscapeTraversalDefinitionCompiler
    -> LandscapeTraversalDefinitions
    -> LandscapeDefinitionId -> SurfaceTraversalCost
```

`traversal.cost` must be a positive integer. Current neutral baseline is `1000` units.

Missing traversal data for terrain participating in an otherwise valid Movement edge is treated as broken definition/bootstrap configuration, not as an ordinary rejection with a hidden fallback price.

### 4.3 Definition data versus runtime state

Current ownership examples:

```text
MovementRate             -> immutable object-definition data
SurfaceTraversalCost     -> immutable landscape-definition data
MovementAction           -> mutable Movement runtime state
per-object timing carry  -> mutable Movement runtime state
Spatial XYZ              -> mutable Spatial runtime state
```

## 5. Time and scheduling

Current time package includes:

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

### 5.1 Clock/read boundary

`SimulationClock` owns mutable simulation tick state.

`SimulationTime` is the read-only capability exposing `tick()` to infrastructure that must observe time without advancing it.

### 5.2 Scheduler

Scheduler owns activation timing and deterministic routing, not domain semantics.

Each scheduled task conceptually carries:

```text
when
HandlerId
processId
TaskHandle / stable order identity
```

One registered `ScheduledHandler` serves an entire domain process family. Movement therefore does not allocate one handler per moving object.

### 5.3 Bound process scheduling

`ProcessScheduler` is the narrow domain-facing capability:

```text
scheduleAfter(delayTicks, processId)
```

`BoundProcessScheduler` binds it to:

```text
SimulationTime
Scheduler
one HandlerId
```

Movement therefore does not receive raw `HandlerId` authority and does not calculate its own absolute completion tick.

### 5.4 Production simulation step

`SimulationStepper` owns current one-tick production order:

```text
clock.advance()
Scheduler.dispatchDue(clock.tick())
```

One step performs one Scheduler snapshot dispatch batch. Work newly scheduled by a handler for the current tick is not recursively drained in that same batch.

Scenario `advance()` delegates to this production operation; `advanceTicks(n)` loops over it.

### 5.5 Domain identity versus Scheduler identity

`MovementActionId` and `TaskHandle` remain distinct.

Current Movement has no public early cancellation path, so its narrow `ProcessScheduler` does not expose task handles. A future real cancellation consumer will decide eager Scheduler cancellation versus stale wake-up semantics.

## 6. Object spatial system

Implemented discrete XYZ object positioning:

- `TransformState`;
- `TransformLookup`;
- `SpatialSystem`;
- `ObjectSpatialIndex` implementations.

Spatial stores positions for WorldObjects only. Terrain does not enter object spatial indexes.

`SpatialSystem.move` is the authoritative mutation used by successful Movement completion and updates transform + registered spatial indexes consistently.

While a timed Movement Action is active, Spatial remains at the source coordinate. There is no second authoritative movement coordinate or fractional position.

## 7. Landscape terrain

Core representation:

```text
XYZ -> LandscapeDefinitionId | absence
```

Implemented:

- `LandscapeDefinitionId`;
- `TerrainSystem`;
- `TerrainLookup`;
- `TerrainStorage` boundary;
- current `SparseTerrainStorage`;
- `TerrainPlacementResult`;
- `TerrainReplacementResult`;
- `TerrainRemovalResult`;
- `LandscapeMutations` coordinated write boundary;
- `LandscapeSystem` coordinator over Terrain and Geometry lifecycle.

`TerrainLookup.find(x,y,z)` returns `null` for absent terrain.

`TerrainSystem.place/replace/remove` are result-based. Current world-state conflicts do not throw:

```text
place on occupied position -> terrain:position_occupied
replace absent terrain     -> terrain:terrain_absent
remove absent terrain      -> terrain:terrain_absent
```

Null/unknown definitions remain programming/configuration errors.

`LandscapeSystem` coordinates sparse Geometry override lifecycle:

```text
placeTerrain   -> successful placement clears stale override
replaceTerrain -> successful replacement preserves override
removeTerrain  -> successful removal clears override
```

Therefore new terrain without explicit override resolves to `FullShape.INSTANCE`, and an override does not resurrect after remove/re-place.

The current sparse storage is an implementation, not a final chunk model.

## 8. Geometry

Package:

```text
world/mechanics/geometry/
```

Implemented core types:

```text
Shape
FullShape
RampShape
GeometryLookup
GeometryState
GeometrySystem
TransitionMask
TransitionPorts
TransitionComposition
SolidCellBlocking
GridTransitionLength
ShapeTraversalFactor
```

### 8.1 Geometry ownership

`GeometrySystem` reads `TerrainLookup`.

For absent terrain:

```text
GeometryLookup.find(XYZ) -> null
```

For present terrain without override:

```text
GeometryLookup.find(XYZ) -> FullShape.INSTANCE
```

Only non-default Shape overrides are stored in `GeometryState`.

`GeometrySystem.clearShapeOverride(x,y,z)` is the low-level override-lifecycle operation used by `LandscapeSystem`. `TerrainSystem` does not depend back on Geometry.

### 8.2 Current Shape API

Current Shape contract includes topology plus actor-independent directed traversal characteristics:

```java
long transitionPorts(
        int relativeX,
        int relativeY,
        int relativeZ);

int transitionBlocks(
        int relativeX,
        int relativeY,
        int relativeZ);

int departureTraversalFactor(
        int relativeX,
        int relativeY,
        int relativeZ,
        int directionX,
        int directionY,
        int directionZ);

int arrivalTraversalFactor(
        int relativeX,
        int relativeY,
        int relativeZ,
        int directionX,
        int directionY,
        int directionZ);
```

`transitionBlocks` defaults to no blocks.

Traversal factors default from the same role ownership exposed by `transitionPorts`:

```text
owned role -> ShapeTraversalFactor.NEUTRAL = 1000
not owned  -> ShapeTraversalFactor.NONE    = 0
```

Relative coordinates describe the Navigation source position relative to the Shape terrain coordinate. Direction arguments describe the directed edge from that source.

Shape has no world lookup and receives no neighboring Shape information.

### 8.3 Supported-position role law

Current production structural Shapes (`FullShape` and four primitive cardinal `RampShape` orientations) expose one supported navigation position at:

```text
anchor + (0,0,1)
```

Their role convention is:

```text
departures -> originate from supported position
arrivals   -> confirm transitions ending at supported position
```

Traversal-factor ownership follows exactly the same law.

For directed edge `A -> B` with `d = B - A`:

```text
source support Shape:
    relative source = (0,0,1)
    query departure factor for d

destination support Shape:
    relative source = (0,0,1) - d
    query arrival factor for d
```

This is covered by the production Shape role-contract sweep.

The supported-position model is current WORKING geometry, not a promise about every future Shape. If it changes, Navigation read envelope and TransitionCost support lookup must be revised together.

### 8.4 TransitionMask

A structural step is one of the 26 non-center offsets in a `3x3x3` direction neighborhood.

`TransitionMask` maps those offsets to bits in an `int`; the center bit is excluded from `ALL`.

Important operations are primitive and allocation-free:

```text
TransitionMask.of(dx,dy,dz)
TransitionMask.contains(mask,dx,dy,dz)
```

### 8.5 GridTransitionLength

`GridTransitionLength` gives an intrinsic fixed-point length to immediate directions:

```text
1 changed axis   -> 1000  ~= 1
a2 changed axes  -> 1414  ~= sqrt(2)
3 changed axes   -> 1732  ~= sqrt(3)
```

(The second row means two changed axes; `1414` is the current integer approximation.)

Length belongs to the discrete grid direction, not terrain material and not a concrete Shape.

### 8.6 TransitionPorts and composition

Departure and arrival masks are packed into one `long` in two non-overlapping regions.

Current composition:

```text
resolved = departures & arrivals & ~blocks
```

Navigation OR-accumulates contributions from all relevant Shape instances before `TransitionComposition.resolve`.

For an external edge, one Shape can provide departure and another independently provide arrival. If either contribution is absent, the edge does not exist.

### 8.7 Solid-cell blocking

`FullShape` and `RampShape` represent occupied solid terrain coordinates and share package-private `SolidCellBlocking`.

The occupied terrain anchor is not an ordinary navigation position. Concrete solid Shapes reuse this rule without teaching Navigation their types.

### 8.8 FullShape behavior

`FullShape.INSTANCE` is the default Shape for present terrain.

Current behavior includes:

- eight horizontal departure candidates from the supported position;
- cardinal upward candidates that require independent matching arrivals;
- arrivals into its supported top position;
- downward arrivals needed to confirm descent onto a Full-supported position;
- strict side/corner blocking;
- blocking transitions whose destination enters the solid Full terrain coordinate.

Extra upward departure candidates do not create free Full-to-Full stairs because matching arrivals are absent in ordinary flat Full geometry.

Current traversal factor is neutral for every role FullShape actually owns.

### 8.9 RampShape behavior

`RampShape` is the first production Shape that changes Z through ordinary structural Navigation.

Orientations:

```text
RampShape.POSITIVE_X
RampShape.NEGATIVE_X
RampShape.POSITIVE_Y
RampShape.NEGATIVE_Y
```

The first production model is deliberately primitive: one solid terrain block with a bidirectional structural passage along one cardinal rise axis, no side entry and no XY-diagonal entry.

For a positive-Y ramp:

```text
lower -> ramp = (0,+1,+1)
ramp -> lower = (0,-1,-1)
ramp -> upper = (0,+1,0)
upper -> ramp = (0,-1,0)
```

Ramp owns neither neighboring surface. Neighbor Shapes independently provide the opposite topology role.

Consecutive ramps can connect successive Z levels through matching directed roles.

Current Ramp traversal factors are neutral for owned roles. No separate arbitrary uphill/downhill multiplier is implemented; the diagonal/elevation displacement is already represented by `GridTransitionLength`.

## 9. Navigation

Package:

```text
world/navigation/
```

Implemented:

- `NavigationLookup`;
- `NavigationSystem`.

Public read boundary:

```java
int transitions(
        int x,
        int y,
        int z);
```

### 9.1 Resolver

Structural directions remain the 26 immediate neighbors.

For one source XYZ, Navigation currently examines Geometry in:

```text
dx in [-1,1]
dy in [-1,1]
dz in [-2,1]
```

at most 36 Geometry lookups.

The extra lower Z layer exists so the Shape whose terrain anchor supports a lower destination can contribute its arrival under the one-supported-position role law. It is not a longer movement edge.

For every Shape in the read envelope:

```text
relative source = source XYZ - Shape terrain coordinate
ports  |= shape.transitionPorts(relative source)
blocks |= shape.transitionBlocks(relative source)
```

Then `TransitionComposition.resolve(ports, blocks)` produces the final mask.

No concrete Shape type appears in Navigation logic.

### 9.2 Directed topology

Navigation edges are directed. A forward transition does not generate its reverse automatically.

Bidirectional Full/Ramp behavior comes from independent role support for both directed edges.

### 9.3 Current cache status

There is **no persistent Navigation cache**.

Current topology queries see current Geometry on the next call and require no manual Navigation invalidation.

Caching may return only after representative Movement/Pathfinder workload measurement.

## 10. Traversal / TransitionCost

Package:

```text
world/mechanics/traversal/
```

Implemented:

```text
SurfaceTraversalCost
LandscapeTraversalDefinitions
LandscapeTraversalDefinitionCompiler
TransitionCost
TransitionCostLookup
TransitionCostCalculator
```

### 10.1 Scope

TransitionCost prices an **already-valid adjacent directed structural edge**. It does not authorize topology and does not know the moving actor.

Movement calls Navigation first; only an accepted structural direction is passed to `TransitionCostLookup`.

### 10.2 Two-cell formula

For `A -> B`, direction `d`:

```text
localA = surfaceCost(A) * departureFactor(shapeA, d)
localB = surfaceCost(B) * arrivalFactor(shapeB, d)

TransitionCost(A -> B)
    = lengthFactor(d)
      * average(localA, localB)
```

Current implementation reads the supporting terrain + Shape under A and B using the current standing-position rule.

The model uses both cells rather than destination-only pricing. For neutral cardinal path `A -> B -> C`:

```text
cost(A->B) = A/2 + B/2
cost(B->C) = B/2 + C/2
```

so interior cell B contributes one full surface cost over the two edges.

### 10.3 Fixed-point arithmetic

Current scales:

```text
surface neutral cost = 1000
Shape neutral factor = 1000
grid length scale    = 1000
```

The calculator combines positive integer contributions with exact checked arithmetic and performs one deterministic half-up rounding at final `TransitionCost` output.

Movement timing then applies its own per-object carry when converting cost units to ticks. Cost rounding and time rounding are intentionally separate boundaries.

### 10.4 Directed Shape contribution

The source support Shape is queried only for its departure factor. The destination support Shape is queried only for its arrival factor.

A custom/new Shape can override its own positive factor without any `instanceof` branch in the calculator.

Current production Full/Ramp factors are neutral. Tests include custom directed factors proving `cost(A->B)` and `cost(B->A)` may differ.

### 10.5 Actor independence

`TransitionCostCalculator` does not receive `ObjectId`, `MovementRate`, species or locomotion mode.

Different movers therefore see the same intrinsic price ordering today. Actor-specific affinity remains deferred.

Future Pathfinder must consume the same `TransitionCostLookup` semantics.

## 11. Timed Movement

Packages:

```text
simulation/control/movement/
world/mechanics/movement/
```

Implemented Control types:

```text
MoveStepCommand
MoveStepResult
MoveStepHandler
```

Implemented Movement types:

```text
MovementRate
MovementDefinitions
MovementDefinitionCompiler
MovementStartResult
MovementActionId
MovementAction
MovementStateStore
MovementSystem
MovementActionProcessor
```

### 11.1 Start semantics

`MoveStepCommand(objectId, destinationXYZ)` means:

```text
start one timed adjacent movement attempt
```

It does not mean immediate Spatial mutation.

`MovementSystem.startStep` currently checks:

```text
object exists
movement capability exists
object has Spatial transform
no active MovementAction for object
destination is immediate neighbor
Navigation exposes directed edge
```

Then it obtains `TransitionCost`, derives duration and creates/schedules the action.

Normal current rejections are structured `MovementStartResult` / `MoveStepResult` outcomes. Unknown trusted runtime ids and broken definitions remain exceptional.

### 11.2 Active action state

`MovementAction` stores:

```text
MovementActionId
ObjectId
source XYZ
destination XYZ
```

`MovementStateStore` owns:

```text
MovementActionId -> active MovementAction
ObjectId -> active action identity
ObjectId -> fractional timing carry
```

Presence in the store means active. Completed/interrupted history is not retained there.

`MovementActionId` is monotonic/non-reused and is distinct from Scheduler `TaskHandle`.

### 11.3 Cost-to-tick timing

For cost `c`, rate `r` and persistent per-object remainder `carry`, timing is mathematically equivalent to:

```text
total = c + carry
ticks = floor(total / r)
carry = total mod r
```

with minimum:

```text
ticks >= 1
```

Implementation avoids unsafe arbitrary-long addition while preserving this result for valid state.

Carry persists across separate actions so per-step rounding does not systematically distort long-run speed.

### 11.4 Scheduled completion

After accepted start:

```text
Spatial remains at source
MovementAction remains active
Scheduler owns one future wake-up
```

At due tick `MovementActionProcessor` revalidates:

```text
object still exists
transform still exists
object still at recorded source
Navigation still exposes source -> destination
```

If valid:

```text
SpatialSystem.move(objectId, destination)
```

If invalid, Spatial remains at source.

In both cases the active MovementAction is removed.

### 11.5 Dormant interval

Movement does not poll each active mover every tick.

If terrain/geometry changes during the action, current Movement observes the change only at scheduled completion. Reactive wake-up is not implemented yet.

### 11.6 Current known Movement gaps

- no Occupancy/destination reservation;
- no early cancellation API;
- no actor-specific surface affinity;
- no reactive wake-up on world mutation;
- no Pathfinder / `MoveTo` route lifecycle;
- no involuntary falling process;
- no authoritative interpolation between cells.

The planned first visual/debug view is intentionally allowed to display the discrete source position until completion commit.

## 12. Control Backbone

Neutral result infrastructure:

```text
simulation/result/
```

Implemented:

- `OperationResult`;
- validated namespaced `ResultCode`;
- `OperationResults.requireAccepted(...)`.

Generic Control:

```text
simulation/control/core/
```

Implemented:

- `Command<R extends CommandResult>`;
- `CommandResult`;
- typed `CommandHandler<C,R>`;
- `CommandDispatcher` with exact runtime-class routing.

Current delivery:

```text
simulation/control/sync/SynchronousCommandGateway
```

Concrete use-cases:

```text
control/terrain/   PlaceTerrainCommand/Result/Handler
control/movement/  MoveStepCommand/Result/Handler
```

Synchronous command delivery means the handler executes before `submit` returns. For timed Movement that handler starts an action but final Spatial mutation occurs later through Scheduler, not through another internal Command.

Dependency policy remains executable through `ControlDependencyContractTest`:

```text
control/core -> no world imports
control/sync -> no world imports
world/*      -> no control imports
```

Concrete use-case handlers may import narrow domain APIs.

## 13. Scenario fixture

The deterministic scenario layer is test-only:

```text
ScenarioBuilder
    -> arrange
    -> register definitions/capabilities
    -> place terrain/Shapes/objects
    -> start()

ScenarioHarness
    -> submit production Commands
    -> advance production SimulationStepper
    -> read only public lookups
```

Current builder can assign:

```text
landscape traversal cost
object movement rate
```

and composes real production:

```text
Terrain -> Geometry -> Navigation
Objects -> Spatial
TransitionCostCalculator
MovementSystem / MovementActionProcessor
BoundProcessScheduler -> Scheduler
SimulationStepper
CommandDispatcher
```

The running harness does not expose raw authoritative mutation systems.

## 14. Current testing coverage

### 14.1 Geometry/Navigation

Coverage includes:

- no geometry -> no transitions;
- generic Shape composition without type knowledge;
- flat Full neighborhood -> eight horizontal transitions;
- missing support behavior;
- strict side/corner blocking;
- direct solid-cell blocking;
- current 36-lookup resolver envelope;
- coordinate wrap protection;
- Terrain -> Geometry -> Navigation integration;
- directed edge contract;
- all four Ramp orientations;
- no side/XY-diagonal Ramp entry;
- missing upper/lower support behavior;
- real lower -> ramp -> upper traversal and reverse;
- consecutive ramps;
- role-contract sweep for production Shapes;
- center-bit sanitization;
- seeded randomized comparison against an independent reference resolver.

Traversal-factor role tests additionally assert that production Shape factors are neutral exactly where their corresponding departure/arrival role exists and NONE otherwise.

### 14.2 Control/Landscape

Coverage includes:

- structured terrain place/replace/remove results;
- generic `requireAccepted` expectation handling;
- Geometry override lifecycle through LandscapeMutations;
- exact command routing;
- duplicate/missing registration failures;
- generic Control dependency direction;
- PlaceTerrain accepted/rejected integration.

### 14.3 Time/Movement

Coverage includes:

- `SimulationTime` / bound relative scheduling;
- `SimulationStepper` phase order;
- exact delayed Movement completion;
- different MovementRate values;
- cardinal/diagonal timing;
- persistent fractional carry;
- minimum one-tick duration;
- already-moving rejection;
- missing movement capability;
- unavailable structural transition;
- completion-time revalidation after landscape mutation;
- `advanceTicks(n)` equivalence to `advance()` repeated n times.

### 14.4 TransitionCost

Coverage includes:

- landscape traversal definition compiler validation/freeze;
- positive/integer cost validation;
- two-cell surface average;
- grid length multiplier;
- directed source departure + destination arrival factor ownership;
- different reverse cost from directed factors;
- missing traversal configuration failure;
- non-adjacent calculator input rejection;
- scenario-level proof that surface cost changes authoritative Movement duration;
- scenario-level proof that non-neutral Shape arrival factor changes authoritative Movement duration.

## 15. Coordinate implementation note

Public coordinates currently use signed `int`.

Tests at integer boundaries protect local arithmetic from accidental wrap. They do **not** define valid EvoForge world dimensions.

World bounds and packed coordinate representations remain undecided.

## 16. Current known gaps

### 16.1 Unloaded versus absent terrain

Current read contracts represent terrain absence with `null`. A future chunk/region model must distinguish true absence from not-loaded/not-generated state if those concepts exist.

### 16.2 Navigation diagnostics

`NavigationLookup.transitions` intentionally returns only a primitive mask. Rich diagnostic explanation remains outside the hot read contract until a real visualizer/Pathfinder debugging consumer requires it.

### 16.3 Occupancy

Current Movement does not reserve/claim a destination. Multi-agent conflict semantics are intentionally deferred to the Occupancy milestone.

### 16.4 Movement cancellation/reactivity

There is no early cancellation operation and no reactive wake-up on terrain/geometry mutation. Current action discovers invalidated topology during completion revalidation.

### 16.5 Actor-specific traversal policy

TransitionCost is currently actor-independent. Wheels/stairs, swimming, surface affinity and similar interactions are not implemented.

### 16.6 Pathfinder

There is no Pathfinder or multi-step `MoveTo`. Future Pathfinder must consume Navigation plus the same TransitionCost semantics used by Movement.

### 16.7 Falling

Production vertical topology exists through RampShape, but falling is not ordinary Navigation. Missing supporting geometry creates no structural edge. Falling remains a future explicit involuntary mechanic/process.

### 16.8 Richer Shape semantics

Current Ramp remains deliberately narrow:

- one cardinal axis;
- bidirectional linear passage;
- no side entry;
- no XY-diagonal entry;
- no fractional surface state;
- no general stair/orientation framework.

### 16.9 Queued/asynchronous command delivery

Only synchronous submission exists. A future queued gateway must define ordering, flush point and within-tick visibility explicitly.

### 16.10 Caching

No Navigation/TransitionCost cache policy is selected. Future Pathfinder profiling should determine whether any derived cache representation is justified.

## 17. Determinism status

Current Movement/TransitionCost implementation adds concrete deterministic rules:

```text
fixed-point integer transition-cost arithmetic
one deterministic final cost rounding boundary
persistent deterministic movement timing carry
minimum one-tick movement duration
stable Scheduler ordering
production tick semantics through SimulationStepper
no dependency on renderer FPS for authoritative movement
```

A general authoritative RNG service still does not exist because no current mechanic requires randomness. It should arrive with the first real random consumer.

## 18. Performance watch points

Current Geometry/Terrain sparse implementations use object-keyed maps. Lookup allocation and throughput should be measured under representative Pathfinder workload before replacing them preemptively.

Navigation performs at most 36 local Geometry lookups per source query under the current Shape role model.

TransitionCost currently performs direct source/destination support lookups after Navigation has already confirmed the edge; it does not repeat Navigation's 36-cell resolver scan.

Movement schedules completion instead of scanning every mover each tick. Representative active-agent profiling should measure Action/state/Scheduler allocation and queue throughput before introducing specialized DOD storage.

## 19. Current roadmap

```text
DONE  Object / Definition / Scheduler / Spatial foundation
DONE  Landscape terrain core
DONE  Geometry foundation and transition algebra
DONE  Directed local Navigation
DONE  Production cardinal RampShape + hardening
DONE  Control Backbone + PlaceTerrain vertical slice
DONE  deterministic test-only Scenario fixture
DONE  Timed Basic Movement + first production SimulationStepper
DONE  ProcessScheduler / BoundProcessScheduler timed-process binding
DONE  actor-independent TransitionCost model
NEXT  minimal Z-level visual/debug view
      Occupancy
      Pathfinder
      first agent vertical slice
      World generation
```

Before Occupancy is implemented, destination reservation/conflict semantics must be designed from a real multi-agent scenario. Before Pathfinder optimization, Navigation/Geometry/Terrain/TransitionCost throughput and allocation behavior should be measured under representative route-search load.

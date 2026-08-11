# Технический справочник EvoForge

> Русский перевод для чтения. Канонический источник current implementation — [английская версия](../TECHNICAL_REFERENCE.md). При расхождении формулировок приоритет имеет английский документ.

Этот файл описывает **текущую реализацию**. Она может меняться после ordinary pull requests без изменения stable semantic architecture из `ARCHITECTURE.md`.

Baseline: Java 21, presentation-модули libGDX и pure-Java module `simulation`.

Для подробного walkthrough timed Movement, формул, invariants и extension guidance см. `docs/ru/wiki/Movement-System.md`.

## 1. Модули

```text
core/        libGDX application/presentation layer
lwjgl3/      desktop launcher
simulation/  deterministic simulation/domain code without libGDX
assets/      definitions and presentation assets
docs/        architecture, technical reference and Wiki source
```

`simulation` — authoritative architecture target. Presentation не становится owner simulation state.

## 2. Реализованные области simulation

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

Future packages не создаются только ради reservation names.

## 3. Objects и identity

Implemented foundation:

- `ObjectId` со slot/generation semantics;
- `WorldObject` как небольшой domain object identity + definition identity;
- `ObjectRepository` для existence/identity;
- read-only `ObjectLookup`;
- `ObjectFactory` для definition-backed creation;
- object definitions отдельно от mutable runtime state.

`ObjectRepository` не используется как generic bag mechanics.

Movement rate, position, active action и timing carry намеренно не добавляются полями в `WorldObject`.

## 4. Definitions

Definitions composition-driven и compile-ятся при bootstrap.

Current conventions:

- source keys — stable `namespace:name`;
- runtime systems используют typed ids;
- runtime numeric ids не persistence identity;
- loaders дают deterministic startup flow;
- mechanics владеют собственными compiled definition data;
- новый content на existing mechanics обычно требует только data.

Current roots:

```text
assets/definitions/object/
assets/definitions/landscape/
```

### 4.1 Object aspect `movement`

Ordinary self-propelled capability:

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

`movement.rate` — positive integer в transition-cost units per simulation tick.

Absence aspect означает отсутствие current ordinary `MoveStep` capability.

### 4.2 Landscape aspect `traversal`

Actor-independent base surface price:

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

`traversal.cost` — positive integer. Current neutral baseline = `1000`.

Если otherwise-valid Movement edge опирается на terrain без compiled traversal data, это broken definition/bootstrap configuration, а не normal rejection с silent fallback price.

### 4.3 Definition data и runtime state

```text
MovementRate             -> immutable object-definition data
SurfaceTraversalCost     -> immutable landscape-definition data
MovementAction           -> mutable Movement runtime state
per-object timing carry  -> mutable Movement runtime state
Spatial XYZ              -> mutable Spatial runtime state
```

## 5. Time и scheduling

Current types:

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

`SimulationClock` владеет mutable simulation tick.

`SimulationTime` — read-only capability `tick()` для infrastructure, которой не нужно право advance.

### 5.2 Scheduler

Scheduler владеет activation timing/routing, но не domain meaning.

Scheduled task концептуально несёт:

```text
when
HandlerId
processId
TaskHandle / stable order identity
```

Один registered handler обслуживает целое process family. Тысяча movers не создаёт тысячу handlers.

### 5.3 Bound process scheduling

`ProcessScheduler` — narrow domain-facing capability:

```text
scheduleAfter(delayTicks, processId)
```

`BoundProcessScheduler` связывает её с:

```text
SimulationTime
Scheduler
one HandlerId
```

Movement поэтому не получает raw `HandlerId` authority и не рассчитывает absolute completion tick самостоятельно.

### 5.4 Production simulation step

`SimulationStepper` владеет current one-tick order:

```text
clock.advance()
Scheduler.dispatchDue(clock.tick())
```

Один step выполняет один Scheduler snapshot batch. Новая task, scheduled handler-ом на current tick, не дренируется recursive в том же batch.

Scenario `advance()` вызывает production operation; `advanceTicks(n)` только повторяет её.

### 5.5 Domain identity и Scheduler identity

`MovementActionId` отличается от `TaskHandle`.

Current Movement не имеет public early cancellation, поэтому narrow `ProcessScheduler` не возвращает/не хранит task handle. Когда появится реальный cancellation consumer, будет отдельно выбрана eager cancellation или stale wake-up semantics.

## 6. Object Spatial

Implemented discrete XYZ positioning:

- `TransformState`;
- `TransformLookup`;
- `SpatialSystem`;
- `ObjectSpatialIndex` implementations.

Spatial хранит position только WorldObjects. Terrain не входит в object spatial indexes.

`SpatialSystem.move` — authoritative mutation successful Movement completion и согласованно обновляет transform + registered indexes.

Пока Movement Action active, Spatial остаётся в source. Второй authoritative coordinate или fractional position отсутствует.

## 7. Landscape terrain

Core representation:

```text
XYZ -> LandscapeDefinitionId | absence
```

Implemented:

```text
LandscapeDefinitionId
TerrainSystem
TerrainLookup
TerrainStorage
SparseTerrainStorage
TerrainPlacementResult
TerrainReplacementResult
TerrainRemovalResult
LandscapeMutations
LandscapeSystem
```

`TerrainLookup.find` возвращает `null` для absence.

Current world-state conflicts — structured results:

```text
place occupied -> terrain:position_occupied
replace absent -> terrain:terrain_absent
remove absent  -> terrain:terrain_absent
```

Unknown/null definitions — programming/configuration errors.

Landscape lifecycle:

```text
placeTerrain   -> clear stale Geometry override
replaceTerrain -> preserve Geometry override
removeTerrain  -> clear Geometry override
```

Sparse storage — current implementation, не final chunk model.

## 8. Geometry

Package:

```text
world/mechanics/geometry/
```

Current core types:

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

Absent terrain:

```text
GeometryLookup.find(XYZ) -> null
```

Present terrain without override:

```text
GeometryLookup.find(XYZ) -> FullShape.INSTANCE
```

`GeometryState` хранит только non-default overrides.

### 8.2 Shape API

Current Shape contract:

```java
long transitionPorts(...);
int transitionBlocks(...);
int departureTraversalFactor(..., directionX, directionY, directionZ);
int arrivalTraversalFactor(..., directionX, directionY, directionZ);
```

Traversal factor defaults выводятся из same role ownership topology:

```text
owned role -> NEUTRAL = 1000
not owned  -> NONE    = 0
```

Shape не получает World/neighbor lookup.

### 8.3 Supported-position role law

Current production Shapes (`FullShape`, four cardinal `RampShape`) имеют one supported position:

```text
anchor + (0,0,1)
```

Topology и traversal factors используют один role law:

```text
departure -> source support owner
arrival   -> destination support owner
```

Для `A -> B`, `d = B-A`:

```text
source Shape:      rel = (0,0,1), query departure(d)
destination Shape: rel = (0,0,1)-d, query arrival(d)
```

Это покрыто production role-contract tests.

Если будущая Shape model откажется от one-supported-position assumption, Navigation envelope и TransitionCost support lookup пересматриваются вместе.

### 8.4 TransitionMask / Ports / composition

Structural step — один из 26 non-center offsets в `3x3x3`.

Current algebra:

```text
resolved = departures & arrivals & ~blocks
```

Contributions OR-ятся generically. Concrete Shape types Navigation не знает.

### 8.5 GridTransitionLength

Fixed-point lengths:

```text
1 changed axis  -> 1000 ~= 1
2 changed axes -> 1414 ~= sqrt(2)
3 changed axes -> 1732 ~= sqrt(3)
```

Length принадлежит direction, не terrain material и не Shape.

### 8.6 FullShape

`FullShape.INSTANCE` — default present-terrain geometry.

Он поддерживает обычную top surface, horizontal adjacency и дополнительные role contributions, необходимые для generic connection с ramps/elevation edges. Solid-cell blocking не позволяет входить внутрь terrain body.

Current traversal factors neutral для всех owned roles.

### 8.7 RampShape

Production orientations:

```text
POSITIVE_X
NEGATIVE_X
POSITIVE_Y
NEGATIVE_Y
```

Ramp — primitive solid cardinal slope без side/XY-diagonal entry.

Пример positive-Y:

```text
lower -> ramp = (0,+1,+1)
ramp -> lower = (0,-1,-1)
ramp -> upper = (0,+1,0)
upper -> ramp = (0,-1,0)
```

Neighbors independently provide opposite topology role. Consecutive ramps могут соединять successive Z levels.

Current Ramp traversal factors neutral. Additional arbitrary uphill/downhill multiplier не введён: current displacement уже участвует через `GridTransitionLength`.

## 9. Navigation

Package:

```text
world/navigation/
```

Public boundary:

```java
int transitions(int x, int y, int z);
```

Navigation structural-only: не знает ObjectId, mover abilities, TransitionCost, Pathfinder algorithm или concrete Shape types.

### 9.1 Resolver envelope

Для source XYZ:

```text
dx in [-1,1]
dy in [-1,1]
dz in [-2,1]
```

максимум 36 Geometry lookups.

Extra lower layer нужен для destination support Shape under current role law, а не для longer movement edge.

### 9.2 Directed graph

Forward edge не создаёт reverse автоматически. Symmetry возникает только из independent support обоих directions.

### 9.3 Cache

Persistent Navigation cache сейчас нет. Следующий query видит current Geometry.

Caching вернётся только при representative Pathfinder workload evidence.

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

TransitionCost оценивает **уже valid adjacent directed structural edge**. Он не создаёт topology и не знает mover.

Movement сначала спрашивает Navigation и только затем TransitionCost.

### 10.2 Формула двух cells

Для `A -> B`, direction `d`:

```text
localA = surfaceCost(A) * departureFactor(shapeA, d)
localB = surfaceCost(B) * arrivalFactor(shapeB, d)

TransitionCost(A -> B)
    = lengthFactor(d)
      * average(localA, localB)
```

Current calculator читает source/destination support terrain + Shape по one-standing-position model.

Модель использует обе cells. Для neutral cardinal path:

```text
A -> B -> C

cost(A->B) = A/2 + B/2
cost(B->C) = B/2 + C/2
```

Interior B суммарно вносит full surface cost.

### 10.3 Fixed-point arithmetic

Scales:

```text
surface neutral = 1000
Shape neutral   = 1000
grid scale      = 1000
```

Calculator использует checked integer arithmetic и одну deterministic half-up rounding на final TransitionCost boundary.

Movement carry отдельно решает cost-to-tick fractional remainder.

### 10.4 Directed Shape contribution

Source support Shape даёт только departure factor. Destination support Shape — только arrival factor.

New Shape может override собственный factor без central `instanceof`.

Custom tests доказывают, что directed factors могут дать `cost(A->B) != cost(B->A)`.

### 10.5 Actor independence

Calculator не получает ObjectId/MovementRate/species/locomotion mode.

Разные movers пока одинаково rank-ят intrinsic edges. Actor-specific affinity deferred.

Future Pathfinder обязан использовать ту же `TransitionCostLookup` semantics.

## 11. Timed Movement

Packages:

```text
simulation/control/movement/
world/mechanics/movement/
```

Control:

```text
MoveStepCommand
MoveStepResult
MoveStepHandler
```

Movement:

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

`MoveStepCommand(objectId, destinationXYZ)` означает:

```text
start one timed adjacent movement attempt
```

не immediate Spatial mutation.

Current validation:

```text
object exists
movement capability exists
object placed in Spatial
no active MovementAction
destination immediate neighbor
Navigation exposes directed edge
```

После этого Movement получает TransitionCost, вычисляет duration, создаёт action и schedule-ит completion.

Normal impossibilities — structured results. Unknown trusted ids/broken definitions — exceptions.

### 11.2 Active state

`MovementAction` хранит:

```text
MovementActionId
ObjectId
source XYZ
destination XYZ
```

`MovementStateStore` владеет:

```text
ActionId -> active MovementAction
ObjectId -> active action id
ObjectId -> timing carry
```

Presence = active. Completed/interrupted history там не хранится.

`MovementActionId` monotonic/non-reused и не равен `TaskHandle`.

### 11.3 Cost -> ticks

Математически:

```text
total = cost + carry
ticks = floor(total / rate)
carry = total mod rate
```

с:

```text
ticks >= 1
```

Implementation избегает unsafe arbitrary-long addition, сохраняя equivalent valid result.

Carry живёт per object между steps и убирает systematic per-step rounding bias.

### 11.4 Scheduled completion

После accepted start:

```text
Spatial = source
MovementAction active
Scheduler owns future wake-up
```

На due tick `MovementActionProcessor` revalidate-ит:

```text
object alive
transform exists
object still at source
Navigation still exposes source -> destination
```

Если valid — `SpatialSystem.move`; если invalid — object остаётся source. В обоих случаях action удаляется.

### 11.5 Dormant interval

Movement не poll-ит mover каждый tick. World change во время sleep обнаруживается current implementation на completion revalidation.

### 11.6 Current gaps

- Occupancy/destination reservation отсутствует;
- early cancellation отсутствует;
- actor-specific surface affinity отсутствует;
- reactive wake-up on world mutation отсутствует;
- Pathfinder/`MoveTo` отсутствует;
- falling process отсутствует;
- authoritative interpolation между cells отсутствует.

## 12. Control Backbone

Neutral result floor:

```text
OperationResult
ResultCode
OperationResults
```

Generic Control:

```text
Command
CommandResult
CommandHandler
CommandDispatcher
```

Delivery:

```text
SynchronousCommandGateway
```

Current use-cases:

```text
control/terrain/   PlaceTerrain...
control/movement/  MoveStep...
```

Synchronous submission означает immediate handler execution, но не обязательно immediate final domain completion. Timed Movement continuation идёт через Scheduler напрямую, а не через internal Commands.

Dependency contract:

```text
control/core -> no world imports
control/sync -> no world imports
world/*      -> no control imports
```

## 13. Scenario fixture

Test-only deterministic layer:

```text
ScenarioBuilder
    -> arrange definitions/capabilities/terrain/Shapes/objects
    -> start()

ScenarioHarness
    -> submit production Commands
    -> advance production SimulationStepper
    -> read public lookups
```

Builder умеет задавать landscape traversal cost и object MovementRate.

Composition использует real production Terrain/Geometry/Navigation, Objects/Spatial, TransitionCostCalculator, Movement, Scheduler/BoundProcessScheduler и SimulationStepper.

Running harness не раскрывает raw authoritative mutators.

## 14. Current testing coverage

### Geometry / Navigation

Покрываются generic composition, Full flat topology, missing support, solid blocking, current 36-lookup envelope, integer-boundary arithmetic, all Ramp orientations, no side entry, upper/lower support loss, consecutive ramps, directed roles, production role-contract sweep, center-bit sanitization и randomized independent reference comparison.

Traversal-factor contract дополнительно проверяет alignment factor ownership с departure/arrival topology roles.

### Control / Landscape

Покрываются structured terrain results, `requireAccepted`, geometry override lifecycle, exact command routing, dependency direction и PlaceTerrain integration.

### Time / Movement

Покрываются:

```text
BoundProcessScheduler relative scheduling
SimulationStepper phase order
exact delayed completion
different MovementRate values
diagonal timing
persistent carry
minimum one tick
already-moving rejection
missing movement capability
unavailable structural edge
completion revalidation
aadvanceTicks(n) == repeated advance()
```

### TransitionCost

Покрываются:

```text
traversal definition compiler validation/freeze
two-cell average
grid length multiplier
directed departure/arrival factors
reverse directed cost difference
missing traversal config failure
non-adjacent input rejection
surface cost -> actual Movement duration
Shape factor -> actual Movement duration
```

## 15. Coordinate implementation note

Public coordinates — signed `int`. Boundary tests защищают local arithmetic от wrap, но не определяют world dimensions.

World bounds/packed coordinate representation deferred.

## 16. Current known gaps

- unloaded/not-generated vs absent terrain semantics;
- richer Navigation diagnostics;
- Occupancy/reservation;
- Movement early cancellation/reactive wake-up;
- actor-specific traversal policy;
- Pathfinder / `MoveTo`;
- falling;
- richer Shape/ramp semantics;
- queued/asynchronous command delivery;
- Navigation/TransitionCost caching.

## 17. Determinism status

Current Movement/TransitionCost добавляет concrete rules:

```text
fixed-point integer transition cost
one deterministic final cost rounding boundary
persistent movement timing carry
minimum one-tick movement duration
stable Scheduler ordering
production tick semantics through SimulationStepper
no renderer-FPS dependency for authoritative movement
```

General authoritative RNG service пока нет, потому что current mechanics не требуют randomness.

## 18. Performance watch points

Sparse Geometry/Terrain пока используют object-keyed maps. Lookup allocation/throughput нужно измерять под representative Pathfinder workload до premature replacement.

Navigation делает максимум 36 local Geometry lookups per source under current Shape model.

TransitionCost после Navigation читает direct source/destination supports и не повторяет 36-cell resolver scan.

Movement schedule-ит completion вместо per-tick scan movers. Active-agent profiling должен измерить Action/state/Scheduler allocation/throughput прежде чем вводить specialized DOD storage.

## 19. Current roadmap

```text
DONE  Object / Definition / Scheduler / Spatial foundation
DONE  Landscape terrain core
DONE  Geometry + transition algebra
DONE  Directed Navigation
DONE  cardinal RampShape + hardening
DONE  Control Backbone + PlaceTerrain
DONE  deterministic Scenario fixture
DONE  Timed Basic Movement + SimulationStepper
DONE  ProcessScheduler / BoundProcessScheduler
DONE  actor-independent TransitionCost
NEXT  minimal Z-level visual/debug view
      Occupancy
      Pathfinder
      first agent vertical slice
      World generation
```

До Occupancy нужно определить destination reservation/conflict semantics из real multi-agent scenario. До Pathfinder optimization нужно измерить Navigation/Geometry/Terrain/TransitionCost throughput и allocations на representative route-search workload.

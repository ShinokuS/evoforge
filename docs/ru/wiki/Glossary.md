# Глоссарий

Эта страница определяет проектные термины EvoForge. Цель — сохранять архитектурные обсуждения точными: некоторые слова здесь имеют более узкое значение, чем в обычной game-development речи.

## Authoritative state

Единственный source of truth для изменяемого simulation fact.

Примеры:

```text
ObjectRepository -> object existence
SpatialSystem     -> object XYZ
TerrainSystem     -> terrain definition at XYZ
GeometrySystem    -> non-default Shape override
MovementStateStore -> active ordinary movement + timing carry
Scheduler         -> scheduled activation infrastructure
```

Derived indexes и caches не являются отдельными authorities.

## Owner

Подсистема, отвечающая за validation и mutation одного authoritative property.

Ownership не означает, что другие systems не могут читать это property. Он означает, что они не должны поддерживать конкурирующие mutable copies того же semantic fact.

## `ObjectId`

Стабильная runtime identity одного индивидуального WorldObject в течение его lifetime, сейчас представленная slot/generation semantics.

Stale id никогда молча не разрешается в более поздний object, повторно использующий тот же slot.

## Definition

Immutable compiled description content type.

Source definitions используют stable string keys. Runtime systems используют typed definition ids.

Примеры текущих mechanic-owned compiled definition data:

```text
ObjectDefinitionId -> MovementRate
LandscapeDefinitionId -> SurfaceTraversalCost
```

## Definition aspect

Один composition fragment, принадлежащий mechanic.

Текущие примеры:

```text
physical
movement
traversal
```

Generic definition loader направляет aspects зарегистрированным compilers вместо того, чтобы сам содержать поля всех mechanics.

## Terrain

Базовый landscape content в одной XYZ coordinate.

Представление:

```text
XYZ -> LandscapeDefinitionId | absence
```

Terrain не является WorldObject.

## Terrain absence

В coordinate нет terrain. В текущем read contract это представлено absence/null, а не definition `air` или `empty`.

## `LandscapeMutations`

Coordinated landscape write capability для случаев, когда одна logical terrain-lifecycle operation должна сохранять согласованность Terrain и Geometry.

Она отличается от low-level ownership: `TerrainSystem` по-прежнему владеет terrain storage; `LandscapeSystem` координирует нескольких owners сверху.

## Shape

Immutable local geometry object, anchored в terrain coordinate.

Сейчас Shape может вносить:

```text
transition departures
transition arrivals
transition blocks
intrinsic directed departure traversal factor
intrinsic directed arrival traversal factor
```

Shape context-free: он не делает query к World, neighboring Shapes, moving ObjectId, Navigation, Occupancy или Pathfinder.

## Terrain anchor

Terrain XYZ coordinate, geometry которой описывает Shape.

Для текущих solid Shapes primary standing position находится на одну Z coordinate выше anchor.

## Standing position

Navigation/object position, поддерживаемая terrain geometry.

Текущие production Shapes используют:

```text
standing = terrain anchor + (0,0,1)
```

Это current structural Shape-model convention, а не вечное ограничение для всей будущей geometry.

## Relative source

Navigation source position относительно одного Shape anchor:

```text
relative source = source XYZ - Shape anchor XYZ
```

Shape topology и Shape traversal-factor queries используют одну и ту же coordinate system.

## Transition direction

Одна из 26 ненулевых immediate-neighbor deltas в 3D grid:

```text
dx, dy, dz in [-1,1]
not (0,0,0)
```

## Departure

Вклад Shape, утверждающий, что directed structural transition может покинуть source position, поддерживаемую этим Shape.

В текущей TransitionCost semantics тот же source-supporting Shape владеет departure traversal factor этого edge.

Один departure сам по себе edge не создаёт.

## Arrival

Вклад Shape, подтверждающий, что directed transition может закончиться на standing position, поддерживаемой этим Shape.

В текущей TransitionCost semantics тот же destination-supporting Shape владеет arrival traversal factor этого edge.

Один arrival сам по себе edge не создаёт.

## Block

Local Shape contribution, объявляющий direction geometrically obstructed.

Transition resolution всегда применяет blocks после matching departure/arrival.

## Transition algebra

Generic composition rule:

```text
resolved = departures & arrivals & ~blocks
```

Contributions нескольких Shapes OR-накапливаются до этого resolution.

## Structural edge

Directed immediate-neighbor connection, которую Navigation получает из текущей Geometry.

Structural означает, что geometry поддерживает edge. Это не означает, что любой actor обязательно может его использовать, destination свободна или edge дешёвая.

## Navigation

Subsystem structural adjacency.

Текущий public query:

```text
XYZ -> 26-bit structural transition mask
```

Navigation не знает actor identity, MovementRate, TransitionCost, Occupancy или Pathfinder.

## `GridTransitionLength`

Actor-independent fixed-point length immediate 3D grid direction.

Текущие значения:

```text
cardinal / one changed axis         -> 1000
2-axis diagonal                     -> 1414
3-axis diagonal                     -> 1732
```

Она принадлежит самому direction, а не terrain material или concrete Shape.

## `ShapeTraversalFactor`

Fixed-point intrinsic geometry multiplier, который один Shape вносит для одной directed departure/arrival role.

Текущий scale:

```text
NONE    = 0
NEUTRAL = 1000
```

Factor actor-independent и следует тому же role ownership, что и topology ports.

## `SurfaceTraversalCost`

Positive actor-independent base traversal price, связанная с `LandscapeDefinitionId` через landscape aspect `traversal.cost`.

Текущий neutral baseline — `1000` units.

## `TransitionCost`

Positive actor-independent intrinsic price одного **уже valid directed structural edge**.

Текущая conceptual formula для `A -> B`:

```text
localA = surfaceCost(A) * departureFactor(shapeA, d)
localB = surfaceCost(B) * arrivalFactor(shapeB, d)

TransitionCost
    = lengthFactor(d) * average(localA, localB)
```

Movement использует эту цену; будущий Pathfinder должен использовать ту же semantics.

TransitionCost сам не разрешает topology и сейчас не зависит от mover identity.

## `TransitionCostLookup`

Narrow read boundary для оценки стоимости directed adjacent transition.

Он существует, чтобы Movement и будущий Pathfinder могли разделять одну edge-price model без зависимости от internals calculator.

## `MovementRate`

Positive immutable object-definition property, измеряемая в traversal-cost units per simulation tick.

Она превращает intrinsic TransitionCost в mover-specific duration. Она не переопределяет intrinsic price edge.

## Movement capability

Текущая ordinary self-propelled movement capability, подключаемая к `ObjectDefinitionId` через aspect `movement.rate`.

Отсутствие aspect означает, что `MoveStepCommand` не может запустить ordinary Movement для этой definition.

Это ещё не финальная universal locomotion capability model.

## `MoveStepCommand`

External intent запустить одну timed adjacent movement attempt объекта в заданную соседнюю XYZ.

Accepted означает, что MovementAction запущен; это **не** означает, что Spatial уже переместился.

## `MovementAction`

Domain runtime state одной active adjacent timed movement attempt.

Текущий action хранит:

```text
MovementActionId
ObjectId
source XYZ
destination XYZ
```

Наличие в `MovementStateStore` означает active. Completed/interrupted actions удаляются, а не сохраняются как history.

## `MovementActionId`

Monotonic non-reused domain identity одного MovementAction.

Она отличается от Scheduler `TaskHandle`.

## Movement timing carry

Per-object integer remainder, сохраняемый между adjacent Movement actions, чтобы cost-to-tick conversion систематически не терял fractional timing.

Концептуально:

```text
total = cost + carry
ticks = floor(total / rate)
carry = total mod rate
```

при minimum one tick на movement.

## Completion-time revalidation

Текущий Movement policy повторно проверить мир в момент пробуждения scheduled action completion.

Проверяются object/source/Navigation state до `SpatialSystem.move`. Поэтому terrain/geometry mutation во время dormant interval может interrupt action вместо stale commit.

## Dormant / sleeping Action

Timed domain process, который не выполняется каждый simulation tick, пока ждёт следующую scheduled activation.

Текущий MovementAction dormant между start и completion.

## Simulation tick

Минимальный discrete authoritative time step, сейчас представленный `SimulationClock`.

Tick — это simulation time, а не renderer frame и не гарантия, что каждый object действует ровно один раз.

## `SimulationTime`

Read-only capability, раскрывающая current simulation tick без authority продвигать его.

## `SimulationStepper`

Production owner текущего one-tick phase order:

```text
clock.advance()
Scheduler.dispatchDue(clock.tick())
```

Scenario и будущий presentation code продвигают именно этот contract, а не придумывают собственную tick semantics.

## Scheduler

Infrastructure, владеющая моментом, когда scheduled work становится due, и deterministic routing/order активаций.

Scheduler не владеет domain Action state и не знает, что означает process.

## `ScheduledHandler`

Infrastructure callback, зарегистрированный для одного process family. Получает opaque `processId` и делегирует/resumes domain logic.

Текущий Movement регистрирует один handler для всех MovementActions, а не отдельный handler на каждого mover.

## `ProcessScheduler`

Narrow domain-facing capability:

```text
scheduleAfter(delayTicks, processId)
```

Позволяет domain system schedule собственный process без знания `HandlerId` или mutable SimulationClock.

## `BoundProcessScheduler`

Infrastructure adapter, связывающий один `ProcessScheduler` с:

```text
SimulationTime
Scheduler
one HandlerId
```

Это предполагаемый connection pattern для будущих timed mechanics.

## `TaskHandle`

Infrastructure identity одной scheduled activation.

Это не то же самое, что `ObjectId` или domain process id вроде `MovementActionId`.

## Occupancy

Будущая mechanic/policy, описывающая temporary use/reservation positions объектами.

Она намеренно отделена от structural Navigation. Текущий Movement пока не резервирует destinations.

## Pathfinder

Будущий route-selection consumer Navigation и `TransitionCostLookup`.

Pathfinder выбирает route; он не определяет topology и не становится authoritative movement mutator.

## Command

Immutable external intent, который Player/AI/scripts/scenarios/network adapters/debug tools отправляют через Control boundary.

Command не является internal RPC requirement для каждой system-to-system mutation.

## Command delivery

Механизм доставки Command до `CommandDispatcher`.

Текущий delivery synchronous. Будущий queued/asynchronous gateway может изменить delivery timing, переиспользовав stable Command/Handler contracts.

Synchronous delivery не означает synchronous domain completion: `MoveStepCommand` синхронно запускает timed Action, завершающийся позже.

## `CommandResult`

Control-visible результат Command. Он расширяет `OperationResult` и поэтому как минимум предоставляет:

```text
accepted
namespaced ResultCode
```

## `OperationResult`

Минимальный neutral observation contract, общий для structured domain results и command results.

Он намеренно не содержит каждое domain-specific field.

## `ResultCode`

Validated namespaced code, например:

```text
terrain:position_occupied
movement:already_moving
movement:transition_unavailable
```

Глобального enum всех domain failures нет.

## `OperationResults.requireAccepted`

Generic internal helper, выражающий, что caller ожидает success structured operation из-за собственного invariant.

Он не превращает underlying operation в exception-based API.

## Scenario fixture

Test-only deterministic arrange/start/control/read layer.

`ScenarioBuilder` может использовать controlled setup writes до `start()`. Работающий `ScenarioHarness` отправляет production Commands, продвигает production `SimulationStepper` и предоставляет read-only observations вместо raw authoritative mutators.

## Cache

Derived state, сохраняемое ради избежания recomputation.

Cache никогда не является вторым authoritative owner. У текущих Navigation/TransitionCost нет persistent cache contract.

## Hot path

Code, выполняемый настолько часто в representative workload, что allocation count, boxing, locality или algorithmic cost заметно влияют на performance.

Path не считается hot только потому, что когда-нибудь может стать важным.

## Definition-driven content

Content, который можно добавить через source definitions, потому что существующие mechanics уже выражают его behavior.

Пример: новый terrain material с другим `traversal.cost` — это content, а не новая Movement mechanic.

## Mechanic

Semantic behavior/state owner, например Spatial, Terrain, Geometry, Movement, Health или Inventory.

Новая mechanic оправдана, когда существующие semantic owners/contracts не могут выразить требуемое behavior без нарушения ownership boundaries.

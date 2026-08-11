# Архитектурный контракт EvoForge

> Русский перевод для чтения. Канонический нормативный источник — [английская версия](../ARCHITECTURE.md). При расхождении формулировок приоритет имеет английский документ.

Статус: живой архитектурный контракт.

Этот документ содержит стабильные границы и инварианты EvoForge. Точные списки классов, текущие реализации, тестовые файлы, pull request и временные решения по оптимизации относятся к `TECHNICAL_REFERENCE.md`.

## 1. Словарь статусов

- **FIXED** — семантический контракт. Внутренняя реализация может меняться без изменения поведения для потребителей.
- **WORKING** — текущее направление проектирования. Оно может быть пересмотрено, когда реальный vertical slice даст более убедительные основания.
- **DEFERRED** — решение намеренно отложено. Существующие контракты должны оставлять пространство для последующего выбора.

## 2. Модель проекта

EvoForge — deterministic emergent simulation, построенная вокруг:

- object-oriented domain model;
- immutable definitions, собираемых композицией;
- специализированных owners authoritative state;
- event/scheduler-driven execution вместо `update(dt)` у каждого объекта;
- discrete three-dimensional world coordinates;
- общей Controller/Command boundary для Player, AI, scripts и scenarios;
- local structural Navigation со сменными pathfinding/movement policies;
- headless scenarios и invariant tests;
- selective data-oriented optimization только после workload evidence.

EvoForge — не pure ECS, не universal physics engine, не giant `WorldCell` model и не архитектура, где каждый object выполняется каждый tick.

## 3. Базовые инварианты [FIXED]

| ID | Инвариант |
|---|---|
| I-01 | Каждый individual runtime object имеет stable `ObjectId` identity. |
| I-02 | `ObjectRepository` владеет только identity/existence; mechanics там не накапливаются. |
| I-03 | Definitions — immutable runtime descriptions, compiled из composition-driven source data. |
| I-04 | У каждого mutable authoritative property ровно один owner. |
| I-05 | Systems зависят от narrow read contracts, а не от mutable internals других systems. |
| I-06 | Normal gameplay impossibility — structured domain rejection, а не JVM exception. |
| I-07 | Events описывают факты после authoritative mutation; это не hidden commands. |
| I-08 | Authoritative simulation mutation выполняется на simulation thread, пока contract явно не пересмотрен. |
| I-09 | Scheduler управляет time/order activation и не знает domain semantics. |
| I-10 | Public semantic contracts должны переживать replacement internal storage/algorithms. |
| I-11 | Hot paths избегают unnecessary scans, allocations, boxing и temporary collections после подтверждения нагрузки. |
| I-12 | Новые fundamental systems появляются вместе с headless correctness tests и diagnostic strategy. |
| I-13 | Command пересекает external-intent boundary; continuing internal processes и internal state producers используют narrow domain APIs напрямую, а не превращают Command в internal RPC. |
| I-14 | Generic Control routes/observes commands, но не зависит от world-domain types; world domains не зависят от Control. |
| I-15 | Structural Navigation решает существование edge; actor-independent TransitionCost оценивает уже valid directed edge; Movement переводит эту цену в timed execution. |
| I-16 | Timed Movement не создаёт вторую authoritative position: Spatial остаётся в source до completion-time revalidation и разрешённого `SpatialSystem.move`. |
| I-17 | Scheduler process identity/routing — infrastructure; domain Action/process identity и state остаются owned соответствующей domain mechanic. |

## 4. Координаты мира [FIXED REPRESENTATION / DEFERRED BOUNDS]

Authoritative world positions представлены как:

```text
(int x, int y, int z)
```

`int` — public representation координаты. Это **не** обещание, что каждое значение от `Integer.MIN_VALUE` до `Integer.MAX_VALUE` является valid world coordinate.

Valid world bounds — отдельная world/storage policy и остаются **DEFERRED**, пока не появятся concrete region/chunk/world-generation requirements.

Следствия:

- local algorithms не должны silently wrap координаты у supported bounds;
- future internal packed keys допустимы, если bounds policy сделает их полезными;
- выбор packed/chunk storage не должен менять normal coordinate consumer API `int x, int y, int z`.

## 5. Владение spatial state объектов [FIXED]

`SpatialSystem` владеет позициями только `WorldObject` instances.

```text
ObjectId -> XYZ
```

`ObjectSpatialIndex` содержит только indexes, derived от object position.

Landscape, terrain, water, temperature и другое environmental state не становятся `WorldObject` и не входят в object spatial indexes только потому, что используют XYZ.

Shared XYZ — address, а не shared owner всего cell state.

Timed Movement Action не владеет interpolated или alternate authoritative coordinate. До successful completion Spatial остаётся в source; после completion Spatial atomically владеет destination.

## 6. Landscape и terrain [FIXED]

Base landscape content хранится отдельно:

```text
XYZ -> LandscapeDefinitionId | absence
```

Absence не является definition вроде `core:open`.

`TerrainSystem` владеет terrain storage и terrain-specific mutation invariants. Concrete terrain storage replaceable. Normal conflicts, вызванные current terrain state, возвращаются structured results; invalid definitions и broken programming/configuration inputs остаются exceptions.

Terrain и Geometry остаются separate authoritative concerns. `TerrainSystem` не должен зависеть от `GeometrySystem` только ради lifecycle coordination.

Public coordinated landscape write capability — `LandscapeMutations`. Она владеет semantic operation, когда одна logical landscape mutation должна сохранять coherence нескольких owners.

Current terrain lifecycle:

```text
placeTerrain
    -> create terrain only when position empty
    -> clear stale geometry override
    -> present terrain resolves to default FullShape

replaceTerrain
    -> change definition of existing terrain
    -> preserve geometry override

removeTerrain
    -> remove terrain
    -> remove geometry override
```

Non-default Shape не переживает remove + later re-place в том же XYZ. Shape принадлежит lifetime конкретной terrain cell, а не координате навсегда.

Landscape definitions могут иметь mechanic-specific immutable data, например actor-independent `traversal.cost`. Terrain cell по-прежнему хранит только `LandscapeDefinitionId`; traversal configuration компилируется в mechanic-owned definition store.

Internal producers — future world generation, erosion, continuing Actions — могут вызывать narrow landscape/domain write capability напрямую и не обязаны создавать Commands. Write capabilities выдаются explicit при bootstrap/composition и должны оставаться narrow/reviewable.

Новая environmental mechanic обычно получает specialized state owner вместо полей universal landscape cell.

## 7. Geometry [FIXED]

Geometry — separate mechanic поверх present terrain. Она не владеет material identity.

Terrain без geometry override означает `FullShape.INSTANCE`. Sparse geometry state хранит только non-default Shape overrides.

`Shape` — open declarative local-geometry contract:

- нет enum всех shapes;
- central Shape catalog не нужен для runtime composition;
- нет `instanceof`/`switch` по concrete Shape внутри Navigation или TransitionCost calculation;
- Shape не запрашивает World, neighbors, Navigation, ObjectId или pathfinding;
- Shape получает только source position относительно собственного terrain anchor и local direction при запросе directed traversal characteristic.

Shape владеет двумя связанными, но отдельными contributions:

```text
structural topology roles
    -> departures / arrivals / blocks

intrinsic traversal geometry
    -> departureTraversalFactor / arrivalTraversalFactor
```

Traversal factors используют **тот же departure/arrival role ownership и relative-coordinate law**, что topology. Shape вносит только source-side departure factor или destination-side arrival factor и не рассчитывает contribution соседнего Shape.

Current traversal factors используют fixed-point scale `1000 = 1.0`. `0` означает, что Shape не владеет requested traversal role. Current `FullShape` и cardinal `RampShape` используют neutral factors для topology roles; arbitrary extra ramp penalty не является частью current contract.

Новый Shape добавляется implementation + tests без изменений `NavigationSystem`, `TransitionCostCalculator` или existing Shapes только ради распознавания concrete type.

## 8. Алгебра structural transitions [FIXED ALGEBRA / WORKING SHAPE MODEL]

Structural transition соединяет source XYZ ровно с одним из 26 immediate three-dimensional neighbors.

Один structural edge может одновременно менять X, Y и Z, пока каждая delta в `[-1, 1]`, а total delta не `(0,0,0)`.

Shape вносит три independent facts:

```text
departures
arrivals
blocks
```

Contributions нескольких Shapes разрешаются generically:

```text
resolved = departures & arrivals & ~blocks
```

Public resolved mask всегда ограничена 26 valid directions.

Contributions OR-ятся до resolution, поэтому composition не зависит от concrete Shape type или processing order.

Departure и arrival — independent roles. Один Shape может предложить departure со своей supported surface, другой independently подтвердить arrival на своей supported surface. Missing confirmation с любой стороны означает отсутствие structural edge. Shapes не query друг друга.

**Current production structural Shape model** (`FullShape` и primitive cardinal `RampShape`) использует одну supported navigation position:

```text
anchor + (0, 0, 1)
```

В этой model:

- departures originate only from supported position;
- arrivals confirm only directions whose destination is that position;
- Shape не утверждает existence neighboring Shape или foreign supported surface;
- occupied solid terrain coordinates не являются ordinary navigation positions.

Та же supported-position relationship используется current TransitionCost support-owner lookup. Для directed edge `A -> B` source support = `A - (0,0,1)`, destination support = `B - (0,0,1)`; source Shape query-ится за departure role, destination Shape — за arrival role.

One-supported-position rule — **WORKING**, не вечное ограничение. Если real future Shape потребует multiple supported positions или другой local representation, его contract, Navigation read envelope и TransitionCost support-owner lookup пересматриваются вместе, а не обходятся type-specific logic.

## 9. Топология Navigation [FIXED]

Navigation предоставляет только structural adjacency:

```java
int transitions(int x, int y, int z)
```

Result — 26-bit neighbor mask.

Navigation:

- читает только Geometry;
- не знает concrete Shape types;
- не знает ObjectId;
- не знает mover abilities;
- не назначает transition/path cost;
- не выполняет pathfinding;
- не mutate-ит world state.

Transition **distance** и Geometry **read distance** — разные concepts. Structural edges ограничены 26 immediate neighbors:

```text
dx, dy, dz in [-1, 1]
```

Для одного source XYZ current resolver читает Geometry на source-relative offsets:

```text
dx in [-1, 1]
dy in [-1, 1]
dz in [-2, 1]
```

Это максимум `3 * 3 * 4 = 36` local Geometry lookups. Extra lower Z layer нужен current one-supported-position Shape model: для transition с `dz = -1` Shape, supporting destination, может иметь terrain anchor ещё на одну cell ниже destination. Чтение anchor позволяет внести destination-side arrival без neighbor knowledge внутри Shape и concrete Shape logic внутри Navigation.

Read envelope derived из Shape role contract, а не path length. При изменении structural Shape model envelope заново выводится и тестируется.

Structural topology truly 3D: Shape может expose elevation-changing neighbor edges без Shape-specific Navigation rules.

### 9.1 Directed graph [FIXED]

Structural Navigation — **directed graph**.

Если `transitions(A)` содержит `d`, это не означает, что `transitions(A + d)` содержит `-d`.

Symmetric movement возникает только если оба directed edges independently supported.

TransitionCost тоже directed: разные departure/arrival factors могут дать `cost(A -> B) != cost(B -> A)` даже если оба edges существуют.

### 9.2 Caching [DEFERRED IMPLEMENTATION]

Persistent Navigation cache contract пока отсутствует.

Possible future implementations:

- no cache;
- bounded cache;
- chunk-local topology;
- region-derived topology;
- другой representation по profiling evidence.

Cache — derived state и остаётся invisible за stable Navigation read contract. Lifecycle/invalidation проектируются вместе с workload/world-region lifecycle, который реально этого потребует.

## 10. Границы Movement, traversal и pathfinding [FIXED BOUNDARIES / WORKING DETAILS]

Current semantic chain:

```text
Navigation
    -> существует ли directed adjacent structural edge A -> B?

TransitionCost
    -> какова actor-independent intrinsic price valid edge?

MovementRate + Movement timing state
    -> сколько simulation ticks требуется mover?

MovementAction
    -> start, sleep, completion-time revalidation, Spatial commit или interruption

Pathfinder (future)
    -> выбирает среди valid edges, используя ТУ ЖЕ TransitionCost semantics
```

### 10.1 Timed adjacent Movement [FIXED CURRENT SEMANTICS]

`MoveStepCommand` запускает one adjacent timed attempt; acceptance не мутирует Spatial немедленно.

Movement проверяет object capability, placement, adjacency и Navigation до start Action. У object может быть максимум один ordinary Movement Action.

Пока Action active:

```text
authoritative Spatial position = source
```

Action plan-ит future completion через narrow `ProcessScheduler`. На completion Movement revalidate-ит, что object ещё существует, всё ещё в recorded source и Navigation всё ещё содержит directed edge. Только после этого можно вызвать `SpatialSystem.move`; иначе Action interrupted и удаляется без изменения position.

Между start и scheduled completion current Action dormant. Он пока не подписан на terrain/geometry mutation notifications; changed topology обнаруживается completion-time revalidation.

Каждый ordinary Movement transition длится минимум один simulation tick. Authoritative fractional/interpolated position между cells отсутствует.

### 10.2 Movement capability и timing [FIXED CURRENT SEMANTICS]

Ordinary self-propelled Movement capability definition-backed:

```text
ObjectDefinitionId -> MovementRate
```

`MovementRate` — positive integer в traversal-cost units per simulation tick. Absence movement aspect означает unavailable current ordinary movement capability.

Transition-cost units переводятся в ticks через deterministic per-object fractional carry. Carry сохраняется между adjacent steps, чтобы repeated rounding не искажал fast movers или diagonal travel.

Current timed Movement independent от wall-clock/render FPS. Presentation speed может менять, как быстро simulation ticks проходят в real time; она не переопределяет `MovementRate`, `TransitionCost` или simulation tick ordering.

### 10.3 Actor-independent TransitionCost [FIXED CURRENT MODEL]

TransitionCost считается только после того, как Navigation подтвердил valid adjacent directed edge.

Для edge `A -> B` с direction `d` conceptual model:

```text
localA = surfaceCost(A) * departureFactor(shapeA, d)
localB = surfaceCost(B) * arrivalFactor(shapeB, d)

TransitionCost(A -> B)
    = lengthFactor(d)
      * average(localA, localB)
```

Current model использует обе supporting landscape cells.

Ownership:

- `LandscapeDefinitionId` mechanic data даёт positive actor-independent `SurfaceTraversalCost`;
- source Shape даёт только directed departure factor;
- destination Shape даёт только directed arrival factor;
- grid direction даёт cardinal/double-diagonal/triple-diagonal length;
- Movement не branch-ится по concrete Shape type;
- `MovementRate` применяется после TransitionCost и не определяет edge price.

Authoritative cost arithmetic — fixed-point integer arithmetic. Current neutral scales `1000` для surface cost, Shape factor и grid-length scale; grid lengths `1000`, `1414`, `1732`. Combined TransitionCost round-ится deterministic один раз на output boundary, затем Movement carry отдельно обрабатывает cost-to-tick remainder.

Current TransitionCost **actor-independent**. Movers с разными rates видят одинаковое intrinsic edge ranking. Actor/surface interactions вроде wheels vs stairs или swamp affinity остаются **DEFERRED** до real capability consumer.

### 10.4 Scheduler/process boundary [FIXED]

Scheduler знает activation time, handler и opaque process id; domain Action state ему не принадлежит.

Domain process identity (`MovementActionId`) отличается от infrastructure `TaskHandle`.

Timed mechanic обычно получает narrow `ProcessScheduler`, already bound к registered handler, вместо raw authority над `Scheduler + HandlerId + SimulationClock`.

Current production simulation step:

```text
clock.advance()
Scheduler.dispatchDue(clock.tick())
```

owned by `SimulationStepper`. Scenario fixture и future presentation вызывают этот production contract, а не создают отдельную tick semantics.

### 10.5 Occupancy и pathfinding [DEFERRED DETAILS]

Occupancy остаётся separate от structural Navigation. Current Movement не reserve-ит destination, поэтому multi-agent conflict semantics пока не fixed.

Pathfinding — replaceable consumer Navigation и TransitionCost. A*, Dijkstra, hierarchical search, flow fields — implementation choices, а не global architecture.

Future Pathfinder не должен иметь second independent edge-price model, расходящуюся с authoritative Movement.

Early Movement cancellation, actor-specific surface affinity, multi-step `MoveTo`, climbing/swimming/flying overlays и involuntary falling остаются deferred до real consumers.

## 11. Детерминизм [FIXED PRINCIPLE]

При одинаковом initial authoritative state, одинаковой submitted command sequence и одинаковом simulation RNG seed/state EvoForge должен выдавать одинаковый authoritative result в supported runtime contract.

Rules:

1. Authoritative randomness берётся из explicitly owned simulation RNG state с reproducible seed/state. `Math.random()` и `ThreadLocalRandom` не authoritative sources.
2. Authoritative behavior не зависит от unspecified iteration order `HashMap`, `HashSet` и аналогичных containers.
3. Если несколько valid choices и order влияет на result, используется explicit stable tie-break: sequence number, stable id или defined ordering.
4. Background workers могут вычислять read-only results, но не mutate authoritative World напрямую. Returned work validate-ится перед application.
5. Floating-point глобально не запрещён, но authoritative branching не должен accidentally зависеть от unstable iteration/reduction order. Current Movement/TransitionCost deliberately uses integer/fixed-point arithmetic; stricter project-wide bit-identical numeric policy остаётся **DEFERRED** до другой реальной mechanic.
6. Одинаковое число production simulation ticks должно давать одинаковый authoritative result независимо от caller batching; presentation FPS не является simulation semantics.

## 12. Control boundary [FIXED PRINCIPLE / WORKING DELIVERY]

Player, AI, scripts, scenarios и other external controllers сходятся к одному command path:

```text
external intent -> Command -> delivery -> dispatcher -> handler -> authoritative domain APIs
```

Command — immutable intent. Continuing Action/process — runtime state и не представляется stream internal Commands только потому, что позднее mutate-ит systems.

Command — **external-intent boundary**, не universal internal RPC. Internal state producers и accepted processes используют narrow domain APIs authoritative owners напрямую.

Normal world-state impossibility — structured data. Invalid programming/bootstrap/configuration state остаётся exceptional.

All operation outcomes имеют minimal neutral observation floor:

```text
accepted
namespaced result code
```

Codes, например:

```text
terrain:position_occupied
movement:already_moving
movement:transition_unavailable
```

Global enum rejection reasons нет. Concrete domains могут иметь richer typed results, generic Control видит common floor.

Generic Control не знает world-domain. Dependency law:

```text
simulation.control.core  -X-> world.*
simulation.control.sync  -X-> world.*
world.*                   -X-> simulation.control.*
```

Concrete adapters под `simulation/control/<use-case>/` могут зависеть от narrow domain APIs нужного use-case. Commands группируются по intent/use-case.

Current delivery synchronous: submission немедленно dispatch-ит и выполняет handler. Это **не** значит, что accepted domain operation обязана сразу завершиться. `MoveStepCommand` synchronously запускает Movement Action и возвращает result, пока Spatial остаётся в source; Scheduler позже продолжает domain Action напрямую, не возвращая continuation через Control.

Для deterministic callers submitted command order равен call order.

Future queued/asynchronous delivery может переиспользовать Command/Handler/Dispatcher, но обязана explicit определить queue order, flush point и state visibility. Changing delivery policy не предполагает automatic preservation within-tick visibility.

Player-only shortcut не может напрямую mutate mechanics internals.

## 13. Модель производительности [FIXED PRINCIPLE]

Optimization priority:

1. не делать unnecessary work;
2. bound search locality/indexes;
3. reuse derived results при real workload benefit;
4. убрать hot-path allocations/boxing;
5. вводить specialized primitive/DOD structures только для stable measured hot paths;
6. рассматривать SIMD/parallelism после предыдущих шагов и profiling.

Low-level structure не justified только hypothetical future workload.

Timed Movement следует event-driven model: active step schedule-ит completion, а не требует `update` каждого mover каждый tick.

## 14. Рабочий масштаб [WORKING]

Current design envelope:

```text
total persistent objects:       ~1,000,000
positioned world objects:       ~100,000+
simultaneously active agents:   ~10,000
```

Это architecture scale targets, не FPS/latency promises.

Design избегает mandatory per-tick O(total objects) work и global scans для common operations.

Exact loaded terrain-cell counts, region/chunk sizes остаются **DEFERRED** до chunk/world-generation model.

Scale numbers могут быть revised по representative scenarios/benchmarks без silent redefinition semantic ownership contracts.

## 15. Правила расширения [FIXED]

### Existing mechanic, new content

Добавляйте definition data, если existing aspect/mechanic уже выражает content.

Например новый ordinary landscape material может задать другое positive `traversal.cost` без изменений Movement или TransitionCost code.

### New object mechanic

При необходимости добавляйте specialized definition compiler/store, runtime owner/system, tests и explicit bootstrap registration. Не создавайте giant central runtime state map.

### New landscape mechanic

Добавляйте собственный owner/system вместо превращения Terrain в universal environment structure.

### New Shape

Добавляйте Shape implementation + topology/composition tests. Не меняйте Navigation или `TransitionCostCalculator` для recognition concrete type. Если intrinsic geometry требует non-neutral traversal factor, override только Shape-owned departure/arrival contribution по тому же role law.

Если Shape больше не fits one-supported-position model, explicitly revise Shape contract, resolver envelope и cost support-owner lookup вместо concrete-type exception.

### New spatial query

Если query зависит только от object position, добавьте specialized object spatial index. Если от domain mechanics — query belongs этой mechanic.

### New Pathfinder или AI algorithm

Добавляйте replaceable implementation за existing semantic boundary. Pathfinder consumes Navigation + shared TransitionCost semantics и не становится authoritative Movement mutator.

### New timed mechanic

Domain process state остаётся в domain. Регистрируется один scheduled handler на process family, а start system получает narrow bound scheduling capability. Не добавляйте global Scheduler switch или universal Action framework только из-за shared time infrastructure.

### New Command

Добавляйте concrete immutable Command, typed CommandResult и один handler в appropriate `control/<use-case>/`. Handler может зависеть от narrow domain APIs; generic Control не знает new domain type. Не создавайте Command для internal mutation только ради routing system calls.

## 16. Явно отложенные решения

Architecture намеренно пока не фиксирует:

- exact world coordinate bounds;
- chunk/region dimensions и terrain packing;
- unloaded/not-generated vs absent terrain semantics;
- world generation algorithms и persistence integration;
- water/temperature/weather details;
- occupancy/reservation representation и collision precision;
- richer mover-specific capability model и actor-specific surface affinity;
- early Movement cancellation/reactive wake-up semantics;
- multi-step `MoveTo`/route-execution lifecycle;
- richer ramp/stair topology beyond current primitive cardinal ramp;
- involuntary falling semantics;
- Navigation caching/cache lifecycle;
- pathfinding algorithm, hierarchy и path cache;
- background pathfinding revision/snapshot mechanism;
- full object lifecycle orchestration;
- persistence format и region save boundaries;
- final EventBus implementation;
- queued/asynchronous command batching и within-tick visibility policy;
- multithreading architecture beyond one authoritative mutation thread;
- exact AI planner family;
- final renderer/Z-level UX и art pipeline.

Deferred choice successful только если позже может быть implemented без разрушения fixed ownership/semantic boundaries выше.

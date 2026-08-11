# Структура проекта

EvoForge — multi-module Gradle project. Граница модулей архитектурная: authoritative simulation code должен оставаться независимым от libGDX, чтобы запускаться headless в тестах и позже в tools, servers или deterministic scenario runners.

## Корень репозитория

```text
EvoForge/
├── assets/
├── core/
├── docs/
│   ├── ARCHITECTURE.md
│   ├── TECHNICAL_REFERENCE.md
│   ├── ru/
│   └── wiki/
├── lwjgl3/
├── simulation/
├── .github/workflows/
├── build.gradle
├── settings.gradle
├── gradle.properties
├── gradlew
└── gradlew.bat
```

## `simulation`

Это authoritative domain module и главный architecture target.

Current package structure включает:

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

Package tree растёт только при появлении реальной подсистемы. Empty packages не создаются как roadmap placeholders.

## `core`

`core` — shared libGDX application/presentation layer. Он может читать simulation state через public contracts и отправлять external intent через Control boundary, но не становится владельцем simulation state.

Полезное правило: закрытие окна игры концептуально не равно уничтожению authoritative World. Presentation — клиент simulation.

Следующий visual/debug milestone должен читать Movement/Spatial/Terrain/Geometry state, а не дублировать authoritative simulation logic.

## `lwjgl3`

`lwjgl3` содержит desktop launcher и platform startup helpers. Platform integration принадлежит сюда, а не в simulation module.

## `assets`

Assets содержат presentation assets и source data definitions.

Current roots:

```text
assets/definitions/object/
assets/definitions/landscape/
```

Definitions используют stable keys, а не filenames или runtime integer ids как persistence identity.

Landscape content может иметь aspect:

```json
"traversal": {
  "cost": 1000
}
```

а object content — `movement.rate`.

## `docs`

У документации три роли.

`ARCHITECTURE.md` — компактный normative contract со stable semantic boundaries, invariants и deferred decisions.

`TECHNICAL_REFERENCE.md` отслеживает current implementation и меняется чаще.

`docs/wiki/` содержит long-form Wiki source. `docs/ru/` содержит поддерживаемые Russian counterparts. GitHub Wiki и VitePress генерируются из repository sources после попадания изменений в `main`.

Основное подробное описание timed Movement и TransitionCost находится на странице [Movement System](Movement-System.md).

## `result`

`simulation/result` — neutral infrastructure, общая для domain operations и Control.

Current types:

```text
OperationResult
ResultCode
OperationResults
```

Пакет задаёт только минимальный accepted/rejected observation floor и namespaced result code. Domain semantics ему не принадлежат.

## `control`

Control surface находится под одним корнем:

```text
control/
├── core/
├── sync/
├── terrain/
└── movement/
```

`core` содержит generic Command/Handler/Dispatcher contracts и не импортирует world-domain types. `sync` содержит current immediate delivery implementation.

Concrete vertical slices:

```text
terrain/
    PlaceTerrainCommand
    PlaceTerrainResult
    PlaceTerrainHandler

movement/
    MoveStepCommand
    MoveStepResult
    MoveStepHandler
```

World packages не зависят от Control. Internal mechanics могут напрямую вызывать narrow domain APIs, а не создавать Commands как internal RPC.

## `definition`

Generic definition package предоставляет composition-driven infrastructure: stable `DefinitionId`, compiler registration, file reading, loading и runtime registries/catalogs.

Object и landscape domains оборачивают generic definition ids в typed ids.

Mechanic-specific compiled stores остаются у mechanics:

```text
world/mechanics/movement/MovementDefinitions
world/mechanics/traversal/LandscapeTraversalDefinitions
```

Поэтому generic definition package не превращается в central universal schema.

## `time`

Time package теперь является production infrastructure, реально используемой Movement.

Important types:

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

`SimulationStepper` владеет current production one-tick phase order. `ProcessScheduler` — narrow domain-facing capability для scheduling process after delay; `BoundProcessScheduler` связывает её с одним registered handler.

Scheduler знает activation timing/routing, но не domain meaning Movement/Crafting/Growth process.

## `world/object`

Пакет владеет runtime object identity/existence. `ObjectRepository` использует slot/generation и реализует read-only `ObjectLookup`. `ObjectFactory` создаёт definition-backed objects.

`WorldObject` не накапливает movement speed, position, action state или terrain-specific fields: это responsibilities специализированных owners/mechanics.

## `world/spatial`

Пакет владеет позициями только `WorldObject`. `TransformState` хранит ObjectId-to-XYZ; spatial indexes дают derived position queries; `SpatialSystem` координирует authoritative mutations и обновление indexes.

Timed Movement не владеет второй позицией. Пока `MovementAction` active, Spatial остаётся в source; completion позже может вызвать `SpatialSystem.move` после revalidation.

## `world/landscape`

Landscape не представляется миллионами `WorldObject`:

```text
XYZ -> LandscapeDefinitionId | absence
```

`TerrainSystem` владеет terrain storage и terrain-specific invariants. `LandscapeMutations`, реализованный `LandscapeSystem`, — coordinated write capability для операций, где lifetime terrain должен оставаться coherent с Geometry.

Current storage sparse и replaceable.

Landscape definitions могут дополнительно иметь mechanic-owned compiled aspects вроде actor-independent `traversal.cost`; сама terrain cell по-прежнему хранит только `LandscapeDefinitionId`.

## `world/mechanics/geometry`

Geometry layered поверх present terrain. Она определяет local structural topology, но не material identity.

Current core types включают:

```text
Shape
FullShape
RampShape
GeometrySystem
GeometryState
GeometryLookup
TransitionMask
TransitionPorts
TransitionComposition
SolidCellBlocking
GridTransitionLength
ShapeTraversalFactor
```

`Shape` владеет local topology roles и может предоставлять local intrinsic departure/arrival traversal factor. Для topology и cost используется один role law. Geometry не знает actor identity или MovementRate.

`GridTransitionLength` представляет fixed-point длину immediate cardinal/double-diagonal/triple-diagonal directions.

## `world/mechanics/traversal`

Traversal — actor-independent price layer directed structural edge.

Current types:

```text
SurfaceTraversalCost
LandscapeTraversalDefinitions
LandscapeTraversalDefinitionCompiler
TransitionCost
TransitionCostLookup
TransitionCostCalculator
```

`TransitionCostCalculator` объединяет:

```text
source landscape surface cost
source Shape departure factor
destination landscape surface cost
destination Shape arrival factor
grid direction length
```

Он не решает, существует ли edge, и не знает concrete mover. Movement вызывает его только после того, как Navigation подтвердил directed edge. Future Pathfinder должен потреблять тот же `TransitionCostLookup` вместо собственной таблицы цен.

## `world/mechanics/movement`

Movement владеет выполнением одного timed adjacent object transition.

Current types:

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

Responsibility split:

```text
MovementSystem
    -> validate/start one adjacent action
    -> obtain TransitionCost
    -> convert cost to ticks using MovementRate + carry
    -> schedule completion

MovementStateStore
    -> active MovementAction state
    -> per-object timing carry
    -> one-active-action-per-object invariant

MovementActionProcessor
    -> resume scheduled process
    -> completion-time revalidation
    -> call SpatialSystem.move or interrupt
```

Movement не выполняет pathfinding и пока не владеет Occupancy.

## `world/navigation`

Navigation потребляет только `GeometryLookup` и предоставляет structural adjacency через `NavigationLookup.transitions(x,y,z)`.

Она не знает ObjectId, actor abilities, traversal cost, concrete Shape types или pathfinding algorithms.

Это намеренно отдельная граница от `TransitionCostLookup`: одна отвечает, **какие structural edges существуют**, другая — какова цена уже valid edge.

## Тесты

Simulation tests зеркалят domain areas под `simulation/src/test/java`. Unit tests проверяют local contracts; integration tests — границы subsystems; property/reference tests — generic laws против independent expectations.

Current Movement/Traversal coverage включает:

```text
movement definition compilation
exact delayed completion timing
different MovementRate values
diagonal grid length
fractional timing carry
one active action per object
completion-time revalidation
landscape traversal definition compilation
two-cell TransitionCost formula
directed Shape departure/arrival factors
scenario-level proof that cost changes authoritative duration
batch tick advancement equivalence
```

Control и Landscape также имеют executable dependency/boundary contract tests.

Полный simulation suite:

```bash
./gradlew :simulation:test --rerun-tasks --console=plain
```

Windows:

```bat
.\gradlew.bat :simulation:test --rerun-tasks --console=plain
```

Routine `clean` намеренно не используется: incremental builds обычно достаточно и дешевле.

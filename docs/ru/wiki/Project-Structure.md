# Структура проекта

EvoForge — multi-module Gradle project. Граница модулей архитектурная: авторитетный simulation code должен оставаться независимым от libGDX, чтобы запускаться headless в тестах и позже в tools, servers или deterministic scenario runners.

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

Авторитетный domain module и главный target архитектуры.

Текущая package structure:

```text
io.github.evoforge.simulation
├── result/
├── control/
│   ├── core/
│   ├── sync/
│   └── terrain/
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
    │   └── geometry/
    └── navigation/
```

Package tree растёт только при появлении реальной подсистемы. Empty packages не создаются как roadmap placeholders.

## `core`

`core` — общий application/presentation layer libGDX. Он может читать simulation state через public contracts и отправлять внешнее намерение через Control boundary, но не становится владельцем simulation state.

Полезное правило: закрытие окна игры концептуально не равно уничтожению авторитетной модели World. Presentation — клиент simulation.

## `lwjgl3`

`lwjgl3` содержит desktop launcher и platform startup helpers. Platform integration принадлежит сюда, а не в simulation module.

## `assets`

Assets содержат presentation assets и source data definitions.

Текущие roots:

```text
assets/definitions/object/
assets/definitions/landscape/
```

Definitions используют stable keys, а не filenames или runtime integer ids как persistence identity.

## `docs`

У документации три роли.

`ARCHITECTURE.md` — компактный нормативный документ со стабильными semantic boundaries, invariants и deferred decisions.

`TECHNICAL_REFERENCE.md` отслеживает текущую реализацию и меняется чаще.

`docs/wiki/` содержит long-form Wiki source. `docs/ru/` содержит поддерживаемые русские counterparts. GitHub Wiki и VitePress генерируются из repository sources после попадания изменений в `main`.

## `result`

`simulation/result` — нейтральная инфраструктура, общая для domain operations и Control.

Текущие типы:

```text
OperationResult
ResultCode
OperationResults
```

Этот пакет задаёт только минимальный observation floor accepted/rejected и namespaced result code. Domain semantics ему не принадлежат.

## `control`

Command surface собрана под одним обозримым корнем:

```text
control/
├── core/
├── sync/
└── terrain/
```

`core` содержит generic Command/Handler/Dispatcher contracts и не импортирует world-domain types. `sync` содержит текущую immediate delivery implementation. Concrete commands группируются по intent/use-case; первый пример — terrain placement slice.

World packages не зависят от Control. Внутренние mechanics могут напрямую вызывать узкие domain APIs, а не создавать Commands как внутренний RPC.

## `definition`

Generic definition package даёт инфраструктуру composition-driven definitions: stable `DefinitionId`, compiler registration, file reading, loading и runtime registries/catalogs.

Object и landscape domains оборачивают generic definition ids в typed ids, чтобы Java API не смешивали домены.

## `time`

Time package содержит simulation clock и scheduler foundation. Handler registration отделена от domain semantics.

## `world/object`

Пакет владеет runtime object identity/existence. `ObjectRepository` использует slot/generation и реализует read-only `ObjectLookup`. `ObjectFactory` создаёт definition-backed objects.

## `world/spatial`

Пакет владеет позициями только `WorldObject`. `TransformState` хранит ObjectId-to-XYZ; `CellSpatialIndex` — производный reverse lookup; `SpatialSystem` координирует авторитетную мутацию и обновление indexes.

## `world/landscape`

Landscape не представляется миллионами `WorldObject`:

```text
XYZ -> LandscapeDefinitionId | absence
```

`TerrainSystem` владеет terrain storage и terrain-specific invariants. `LandscapeMutations`, реализованный `LandscapeSystem`, — согласованная write-capability для операций, где lifetime terrain должен оставаться согласованным с Geometry.

Текущее storage sparse и заменяемо.

## `world/mechanics/geometry`

Geometry layered поверх present terrain. Она определяет локальную structural topology, но не material identity.

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
```

## `world/navigation`

Navigation потребляет только `GeometryLookup` и предоставляет structural adjacency через `NavigationLookup.transitions(x,y,z)`.

Она не знает ObjectId, actor abilities, path cost, concrete Shape types или pathfinding algorithms.

## Тесты

Simulation tests зеркалят domains под `simulation/src/test/java`. Unit tests проверяют локальные contracts, integration tests — границы подсистем, property/reference tests — общие законы против независимого resolver.

Control добавляет явный dependency-contract test, поэтому правила направления packages становятся исполняемой архитектурой, а не только документацией.

Полный suite:

```bash
./gradlew :simulation:test --rerun-tasks --console=plain
```

Windows:

```bat
.\gradlew.bat :simulation:test --rerun-tasks --console=plain
```

Обычный `clean` намеренно не используется: incremental builds обычно достаточно и они дешевле.

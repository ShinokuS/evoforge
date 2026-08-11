# Глоссарий

Определения проектных терминов в том смысле, в котором они используются в документации EvoForge.

## Authoritative state

Состояние, значение которого определяет результат симуляции и имеет ровно одного изменяемого владельца. Derived caches, indexes, rendered views и diagnostics не являются authoritative, если явно не сказано обратное.

## Authoritative owner

Подсистема, отвечающая за изменение и определение одного авторитетного факта. Например: `ObjectRepository` владеет существованием объектов; `SpatialSystem` — object XYZ; `TerrainSystem` — terrain content.

## Derived state

Состояние, которое можно восстановить из authoritative owners. Reverse spatial indexes и будущие Navigation caches — примеры. Derived state не может молча стать конкурирующим source of truth.

## `ObjectId`

Стабильная runtime identity индивидуального объекта. Текущая реализация сочетает slot и generation, чтобы stale ids не разрешались в последующее reuse slot.

## Definition

Неизменяемое runtime-описание, скомпилированное из source data. Source identity использует stable keys вроде `namespace:name`; runtime typed ids — implementation references, а не persistence identity.

## Aspect

Единица композиции definition, компилируемая mechanic-specific `DefinitionAspectCompiler`. Aspects позволяют content подключать mechanics без универсального definition class со всеми возможными свойствами.

## Landscape

Environmental world content, адресуемый координатой, а не индивидуальной `WorldObject` identity. Terrain — текущий реализованный landscape owner.

## Terrain

Базовый landscape content в XYZ:

```text
XYZ -> LandscapeDefinitionId | absence
```

Terrain владеет material/content identity, а не navigation topology.

## Geometry

Механика поверх present terrain, отображающая наличие terrain плюс sparse overrides в `Shape`. Geometry не владеет landscape material identity.

## Shape

Контекстно-независимое объявление local topology, anchored в одной terrain coordinate. Shape вносит transition ports и blocks только из source position относительно своего anchor.

## Terrain anchor

XYZ-coordinate terrain, geometry которого описывает Shape.

## Standing position

World position, поддерживаемая terrain geometry, где object обычно может стоять. Текущие production Shapes используют `anchor + (0,0,1)` как единственную standing position.

## Relative source

Navigation query source относительно Shape anchor:

```text
relative source = source XYZ - Shape anchor XYZ
```

## Structural edge

Направленная adjacency из одной XYZ позиции в одного из 26 immediate 3D neighbors. Structural edges описывают geometry, не actor capabilities и не occupancy.

## Transition direction

Дельта `(dx,dy,dz)` одного structural edge. Каждый компонент в `[-1,1]`, `(0,0,0)` недопустим.

## Departure

Вклад Shape, разрешающий transition direction покинуть текущий source. В текущей Shape model departures исходят из собственной standing position Shape.

## Arrival

Вклад Shape, подтверждающий, что destination transition заканчивается на позиции, поддерживаемой этим Shape. Поэтому отсутствие destination-supporting Shape естественно удаляет edge.

## Block

Вклад Shape, означающий, что solid geometry перекрывает transition direction. Blocks имеют приоритет над совпадающим departure/arrival при composition.

## Transition ports

Packed `long`, содержащий независимые departure и arrival masks одного Shape contribution.

## Transition mask

Primitive `int` bit mask структурных направлений. `TransitionMask.ALL` содержит только 26 valid neighbor directions.

## Transition composition

Общее разрешение edge:

```text
resolved = departures & arrivals & ~blocks
```

Contributions OR-накапливаются до resolution.

## Navigation

Слой structural adjacency queries. Navigation читает Geometry и возвращает immediate neighbor edges. Она не делает pathfinding и actor-specific traversal checks.

## Movement

Будущая подсистема, решающая, может ли и как конкретный actor выполнить structural edge. Пока не реализована.

## Occupancy

Будущее transient ограничение world-object, определяющее, доступна ли structural destination из-за других объектов. Намеренно отделено от terrain topology.

## Falling

Будущая involuntary movement mechanic. Empty space сейчас не ordinary Navigation edge и не должен неявно трактоваться как falling.

## Pathfinder

Будущий consumer Navigation, ищущий последовательность structural edges до target. Algorithm и cost representation deferred.

## Controller

Будущий внешний источник решений: player input, AI, script или scenario. Controllers отправляют Commands, а не мутируют authoritative systems напрямую.

## Command

Intent, отправленный через будущий Control Backbone. Command не равен long-running Action/process.

## Scheduler

Generic simulation-time infrastructure, упорядочивающая registered handlers. Управляет timing, не domain semantics.

## FIXED

Статус архитектуры: semantic contract стабилен; внутренняя реализация может меняться без изменения consumers.

## WORKING

Статус: полезное текущее направление, которое может быть пересмотрено после появления реального vertical slice.

## DEFERRED

Статус: решение намеренно отложено. Существующие границы должны позволять принять его позже без speculative infrastructure сейчас.

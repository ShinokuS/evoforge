# Spatial System

Spatial subsystem владеет позициями экземпляров `WorldObject`. Она не владеет terrain, geometry или любой mechanic, индексируемой по координате.

## Авторитетное отображение

Базовый факт:

```text
ObjectId -> (x,y,z)
```

`TransformState` хранит mapping. `SpatialSystem` — mutation boundary, координирующая transform state с derived spatial indexes.

## Read boundary

`TransformLookup` предоставляет read-only position access. Consumers, которым нужны позиции, не зависят от mutable `TransformState` internals.

## Reverse spatial indexes

Запрос часто начинается с coordinate и ищет objects. Для этого существуют `ObjectSpatialIndex` implementations.

Текущий `CellSpatialIndex` даёт exact-cell reverse lookup, производный от object positions.

```text
TransformState
ObjectId -> XYZ

CellSpatialIndex
XYZ -> ObjectId(s)
```

Reverse index — derived state. `TransformState` остаётся authoritative для object position.

## Mutation через `SpatialSystem`

Position changes должны согласованно обновлять authoritative state и indexes. Callers не должны независимо мутировать `TransformState` и `CellSpatialIndex`.

Это пример системы, координирующей свой authoritative store и derived indexes за одной mutation boundary.

## Terrain — не spatial-object state

Terrain тоже использует XYZ, но не принадлежит `CellSpatialIndex`.

```text
Objects:   ObjectId -> XYZ
Terrain:   XYZ -> LandscapeDefinitionId
```

Помещение terrain в object index смешало бы lifetime models и заставило terrain cells расходовать object identity/index space.

## Domain-specific indexes

Будущей механике могут понадобиться queries:

```text
all hungry agents in region
all heat sources near XYZ
all path blockers in chunk
```

Они не становятся автоматически обязанностью `SpatialSystem`. Если query зависит от domain semantics сверх position, mechanic обычно владеет своим specialized derived index и читает Transform/Spatial boundaries.

## Occupancy ещё не реализовано

Object position не означает Navigation occupancy policy автоматически.

Navigation сейчас описывает только structural terrain adjacency. Будущие Movement/Occupancy решат, может ли actor войти в structurally connected destination с другими objects.

Не заставляйте `Shape` или terrain geometry спрашивать `CellSpatialIndex` ради premature occupancy.

## Представление координат

Spatial APIs используют signed integer XYZ. Exact valid world bounds не зафиксированы. Packed coordinate keys можно ввести позже внутри, если bounds и profiling это оправдают, не меняя semantic integer coordinates у consumers.

## Масштаб

Архитектура ожидает >100k positioned objects. Поэтому common spatial queries требуют indexes, а не global scans.

Текущий exact-cell index — фундамент, не финальный ответ на каждый query. Region/chunk/radius indexes появятся с consumers.

## Lifecycle

Object existence и object position — независимые authoritative domains. Higher-level lifecycle action может координировать:

```text
create object
place object
remove object from space
remove object identity
```

Эта orchestration не должна становиться circular dependencies между repository и spatial system.

## Тестирование

```text
place/move/remove behavior
state/index consistency
invalid or stale ObjectId handling
multiple objects in cells when supported by current contract
reverse index updates
integration with ObjectRepository lifecycle
boundary coordinates
```

Тесты защищают правило: authoritative transform и derived indexes не расходятся через public operations.

# Модель объектов

EvoForge использует real domain objects со stable runtime identity, но намеренно не превращает `WorldObject` в universal mutable mechanics bag.

Current timed Movement — concrete пример этого правила: `WorldObject` по-прежнему хранит только identity + definition identity, а Movement capability, active action state, timing carry и Spatial position принадлежат отдельным owners.

## Identity

Каждый individual runtime object получает `ObjectId`.

Current representation упаковывает два non-negative integers в `long`:

```text
high 32 bits -> generation
low  32 bits -> slot
```

Концептуально:

```text
ObjectId[slot:generation]
```

Slot даёт efficient repository addressing. Generation защищает от stale references после remove/reuse slot.

## Зачем нужен generation

Пусть A = `ObjectId[7:3]`. После удаления slot может быть reused как B = `ObjectId[7:4]`.

Старый `ObjectId[7:3]` остаётся dead. Это важно и для mechanic state keyed by `ObjectId`: новый object в reused slot не должен наследовать Movement state старого object.

## `ObjectRepository`

`ObjectRepository` владеет только identity/existence.

Current internal storage:

```text
WorldObject[] objects
int[] generations
int[] freeSlots
```

Creation выделяет `ObjectId` и проверяет identity созданного object. Removal очищает slot, increment-ит generation и возвращает slot в free list.

## Read boundary

Consumers, которым нужна только existence/definition identity, зависят от `ObjectLookup`, а не mutable repository internals.

Current Movement использует `ObjectLookup` для validation existing object и получения `ObjectDefinitionId`; это не даёт Movement право mutate `ObjectRepository`.

## `WorldObject`

Current shape:

```text
WorldObject
    ObjectId
    ObjectDefinitionId
```

`WorldObject` не хранит все mutable mechanics. `ObjectId` является stable join key между specialized owners.

Future health, hunger, inventory, AI state, reproduction, disease и другие mechanics следуют тому же ownership rule.

## Current Movement как ownership example

```text
ObjectRepository / WorldObject
    -> existence
    -> immutable ObjectDefinitionId

MovementDefinitions
    -> ObjectDefinitionId -> MovementRate

MovementStateStore
    -> active MovementAction for ObjectId
    -> per-object fractional timing carry

SpatialSystem
    -> authoritative ObjectId -> XYZ

Scheduler
    -> when MovementAction completion wakes
```

Movement не добавляет в `WorldObject` fields вроде:

```text
x / y / z
speed
currentAction
moveProgress
```

У этих facts разные semantic owners.

## Definition-backed movement capability

Ordinary self-propelled Movement задаётся на `ObjectDefinitionId` через `movement` aspect:

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

Compiled representation:

```text
ObjectDefinitionId -> MovementRate
```

Rate одинаков для всех instances definition и поэтому является immutable definition data. Timing carry меняется independently per `ObjectId` и остаётся runtime state.

General rule:

```text
same for every instance of type
    -> definition data

changes independently for one instance
    -> system-owned runtime state
```

Подробнее: [Definitions](Definitions.md) и [Movement System](Movement-System.md).

## `ObjectFactory`

`ObjectFactory` отвечает за definition-backed creation и связывает identity allocation с object definition catalog, не позволяя молча создать object с unknown definition.

Scenario arrange использует настоящий `ObjectFactory`, а после `start()` runtime assertions не обходят normal ownership.

## Existence независимо от position

```text
ObjectRepository   ObjectId -> existence / WorldObject
SpatialSystem      ObjectId -> XYZ
```

Object может существовать без Spatial position.

Current Movement отражает это явно:

```text
object exists but no transform
    -> structured movement:not_placed rejection
```

Accepted MovementAction также не создаёт intermediate authoritative position: тот же `ObjectId` остаётся в source Spatial coordinate до completion commit.

## Repository — не mechanics registry

Нельзя превращать repository в API вроде:

```text
getHealth(id)
getInventory(id)
getMovementRate(id)
getMovementAction(id)
getAIState(id)
```

Вместо этого specialized owners:

```text
Health state/system       ObjectId -> health
Inventory state/system    ObjectId -> inventory
MovementStateStore         ObjectId -> movement runtime state
SpatialSystem              ObjectId -> XYZ
```

## Lifecycle orchestration

Full create/delete object eventually потребует coordination нескольких mechanics. Это должно жить выше individual owners, когда real consumer определит semantics.

Current sleeping MovementAction показывает одну future проблему: forced deletion во время action потребует explicit cancellation/stale-process policy. Проект не решает её сегодня circular dependencies между ObjectRepository, Movement, Spatial и Scheduler.

## Performance

Repository уже использует slot addressing без primary hash lookup. Это не требует от каждой mechanic копировать тот же representation заранее.

`MovementStateStore` current implementation оптимизирован для correctness/direct ownership. Representative multi-agent workload определит, нужен ли DOD/specialized storage.

## Тестовые инварианты

Object model:

```text
created object uses supplied ObjectId
removed id becomes dead
reused slot gets newer generation
stale id does not resolve to new object
size tracks live objects
repository growth preserves identities
```

Movement integration дополнительно фиксирует:

```text
object without transform cannot start ordinary movement
object definition without movement capability cannot start ordinary movement
active movement does not mutate ObjectRepository identity
Spatial remains authoritative for XYZ until scheduled completion
```

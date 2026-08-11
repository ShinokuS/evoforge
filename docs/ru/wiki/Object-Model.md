# Модель объектов

EvoForge использует настоящие domain objects со стабильной runtime identity, но намеренно не превращает `WorldObject` в универсальный контейнер mutable mechanics.

## Identity

Каждый индивидуальный runtime object получает `ObjectId`.

Текущее представление упаковывает два non-negative integers в `long`:

```text
high 32 bits -> generation
low  32 bits -> slot
```

Концептуально:

```text
ObjectId[slot:generation]
```

Slot даёт эффективную адресацию repository. Generation защищает от stale references после удаления и reuse slot.

## Зачем нужен generation

Пусть object A занимает slot 7, generation 3:

```text
A = ObjectId[7:3]
```

После удаления A repository увеличивает generation. Будущий B может стать:

```text
B = ObjectId[7:4]
```

Старый `ObjectId[7:3]` остаётся dead и не начинает ссылаться на другой object.

## `ObjectRepository`

`ObjectRepository` владеет только object identity/existence.

Текущее storage использует parallel primitive/object arrays:

```text
WorldObject[] objects
int[] generations
int[] freeSlots
```

Slots берутся из free stack или repository расширяется. Creation передаёт allocated ObjectId factory function и проверяет, что созданный object использует именно этот id.

Removal очищает slot, увеличивает generation когда возможно и возвращает slot в free list.

## Read boundary

Consumers, которым нужно только existence, зависят от `ObjectLookup`, а не mutable repository implementation.

Это позволяет менять representation repository без превращения каждой механики в collaborator repository.

## `WorldObject`

`WorldObject` — domain object с identity и immutable definition identity. Он не должен накапливать все возможные mutable properties.

Будущие health, hunger, inventory, AI state, reproduction, disease и другие mechanics получают специализированных owners.

`ObjectId` служит стабильным join key между такими owners без giant mutable object.

## `ObjectFactory`

`ObjectFactory` отвечает за definition-backed creation. Он сочетает allocation identity repository с object definition catalog, поэтому creation не может молча ссылаться на unknown definition.

## Existence независимо от position

Object lifetime и spatial position — разные concerns:

```text
ObjectRepository   ObjectId -> existence / WorldObject
SpatialSystem      ObjectId -> XYZ
```

Object концептуально может существовать без позиции. Удаление/move spatial state не переопределяет identity, если higher-level lifecycle action явно не координирует owners.

## Repository — не mechanics registry

Repository не должен обрастать методами:

```text
getHealth(id)
getHunger(id)
getInventory(id)
getAIState(id)
```

Иначе identity storage превращается в центральную mutable world database.

Вместо этого:

```text
HealthSystem      ObjectId -> health
InventorySystem   ObjectId -> inventory
...
```

с узкими read boundaries.

## Lifecycle orchestration

Создание/удаление полностью оснащённого object со временем потребует координации нескольких mechanics. Эта координация должна жить выше отдельных owners — вероятно в Control/lifecycle orchestration layer.

Не решайте future lifecycle coordination прямыми circular dependencies между systems.

## Производительность

Repository уже избегает hash lookup для primary identity resolution благодаря slot addressing. Это не означает, что каждая object mechanic обязана сразу копировать storage strategy.

Каждая mechanic может выбрать arrays, maps, sparse sets и т.п. за своей semantic boundary, когда известны density/workload.

## Тестовые инварианты

```text
created object uses supplied ObjectId
null/invalid factories rejected
removed id becomes dead
reused slot gets newer generation
stale id does not resolve to new object
size tracks live objects
repository growth preserves identities
```

Это semantic identity guarantees, а не случайные implementation tests.

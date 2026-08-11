# Архитектурные принципы

Эта страница объясняет правила, ограничивающие архитектуру EvoForge. Они важнее конкретной раскладки классов: реализации можно заменить, но владение и семантические границы должны сохраняться.

## Один авторитетный владелец на изменяемый факт

У каждого изменяемого авторитетного свойства ровно один владелец.

Примеры:

```text
object existence        -> ObjectRepository
object XYZ               -> SpatialSystem / TransformState
terrain content at XYZ   -> TerrainSystem
geometry override        -> GeometrySystem / GeometryState
simulation time          -> SimulationClock
scheduled activation     -> Scheduler
```

Read-consumer может объединять данные нескольких владельцев, но не становится вторым source of truth.

Это предотвращает типичную ошибку, когда одна концепция хранится в нескольких изменяемых местах и каждая мутация требует ручной синхронизации.

## Узкие read- и write-capabilities

Системы открывают узкие contracts вместо изменяемых внутренностей.

Примеры read contracts:

```text
ObjectLookup
TransformLookup
TerrainLookup
GeometryLookup
NavigationLookup
```

Consumer должен зависеть от минимального семантического контракта. То же правило действует для мутаций: write-capability выдаётся явно и должна оставаться у небольшого обозримого числа владельцев.

`LandscapeMutations` — первая согласованная write-capability. Она находится над Terrain и Geometry, когда одна логическая landscape operation должна сохранить lifecycle-семантику обоих owners, не создавая обратной зависимости одного owner на другой.

## Общие координаты — адреса, а не владение

Objects, terrain, weather, water, temperature, geometry и будущие mechanics могут ссылаться на один `(x,y,z)`. Это не оправдывает универсальный mutable `Cell` с полями каждой механики.

Предпочтительный pattern:

```text
XYZ -> terrain owner
XYZ -> temperature owner
XYZ -> water owner
ObjectId -> XYZ object position owner
```

Composite query собирает view только когда он нужен consumer.

## Definitions — неизменяемая композиция

Persistent content identity выражается стабильными ключами вроде `namespace:name`. Source definitions собираются композицией и при bootstrap компилируются в typed runtime ids и mechanic-owned immutable data.

Runtime numeric ids — ссылки реализации, а не persistence identity. Save format должен хранить стабильные keys и восстанавливать runtime ids при load/bootstrap.

## Идентичность объекта стабильна

Каждый индивидуальный runtime object получает стабильный `ObjectId`. Текущая реализация использует slot + generation, поэтому stale id не может незаметно разрешиться в новый object, переиспользовавший slot.

`ObjectRepository` владеет только identity/existence. Mechanics не накапливаются в нём из-за наличия id у каждого объекта.

## Scheduler управляет временем, а не семантикой

Scheduler отвечает, *когда* зарегистрированный handler выполняется. Он не содержит enum всех gameplay mechanics и не становится владельцем доменного смысла task.

Это избегает `object.update(dt)`, где неактивные объекты всё равно расходуют CPU и связаны с global frame cadence.

## Commands выражают внешнее намерение

Player input, AI, scripts, scenarios и другие внешние controllers сходятся в один control path:

```text
external controller
    ↓
Command
    ↓
delivery / dispatcher
    ↓
handler
    ↓
authoritative domain APIs
```

Command пересекает external-intent boundary. Это не универсальный внутренний RPC.

После принятия intent продолжающиеся Actions/processes и внутренние producers, например будущие world generation или erosion, могут напрямую вызывать узкие domain APIs. Так ownership остаётся видимым, а каждая внутренняя мутация не превращается в сообщение command bus.

Обычная невозможность из-за world state — structured result, а не JVM exception. Некорректный programming/bootstrap/configuration input остаётся исключением.

Все command results имеют маленький общий observation floor:

```text
accepted
namespaced result code
```

Примеры: `terrain:position_occupied` и будущий `movement:blocked`. Глобального enum всех причин rejection нет.

## Generic Control маршрутизирует, domain решает

Generic Control core знает, как регистрировать, dispatch и наблюдать commands, но не знает семантику terrain, movement или construction.

Закон зависимостей:

```text
simulation.control.core  -X-> world.*
simulation.control.sync  -X-> world.*
world.*                   -X-> simulation.control.*
```

Concrete use-case handlers в `control/<use-case>/` могут зависеть от узких domain APIs. Обратная dependency запрещена.

Текущий synchronous gateway выполняет command немедленно, поэтому успешная мутация видима до возврата из `submit`. Будущий queued/asynchronous gateway может переиспользовать тот же core, но его ordering и within-tick visibility должны быть определены явно.

## Events — факты после мутации

Event сообщает, что авторитетное изменение уже произошло. Это не скрытая command/request на другую мутацию.

Различие важно для determinism, debugging, replay и будущих asynchronous observers.

## Один поток авторитетной мутации

Текущий контракт предполагает один authoritative simulation mutation thread. Background work может вычислять read-only results, но результат проверяется перед применением, а workers не мутируют World напрямую.

Это сохраняет детерминированный порядок и оставляет пространство для parallel computation.

## Открытое поведение, закрытый центральный dispatch

Для расширяемого домена предпочитается open interface с новыми implementation types, а не центральный switch по всем типам.

Geometry показывает pattern:

```text
new Shape implementation
    ↓
existing Shape contract
    ↓
existing transition algebra
    ↓
existing NavigationSystem
```

Navigation не распознаёт `FullShape`, `RampShape` или будущие классы по типу.

Та же дисциплина действует в Control: новая concrete command не добавляет domain switch в `CommandDispatcher`; для её exact class регистрируется один handler.

## Не изобретать abstraction до появления consumer

EvoForge намеренно откладывает:

```text
navigation cache representation
path cost API
pathfinding algorithm
actor capability model
falling semantics
chunk size
world bounds
queued command batching semantics
multithreading model
```

Deferred decision — не отсутствие архитектуры. Это специально оставленная граница до появления измеримых требований или vertical slice.

## Порядок оптимизации

1. убрать лишнюю работу;
2. ограничить работу locality и indexes;
3. переиспользовать derived work, когда есть доказанная польза;
4. убрать allocations и boxing на hot path;
5. вводить primitive/data-oriented storage только для доказанных hot paths;
6. рассматривать parallelism/SIMD после профилирования.

Низкоуровневая структура не лучше автоматически. Она должна решать измеренную проблему.

## Тестировать архитектурные законы, а не только примеры

Unit tests должны покрывать конкретное поведение, но самые ценные тесты выражают законы: stale ObjectIds остаются dead, отсутствие terrain означает отсутствие geometry, Shape composition не зависит от порядка, тела terrain не являются обычными navigation positions, structural edges не выходят за 26-neighbor transition space, generic Control не зависит от world domains, а world domains не зависят от Control.

Property/reference tests предпочтительны, когда простая независимая реализация может проверять оптимизированный resolver на множестве детерминированных мутаций.

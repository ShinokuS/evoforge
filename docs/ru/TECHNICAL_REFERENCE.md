# Технический справочник EvoForge

> Русский перевод для чтения. Канонический источник текущей реализации — [английская версия](../TECHNICAL_REFERENCE.md). При расхождении формулировок приоритет имеет английский документ.

Этот файл описывает текущую реализацию. Она может меняться после обычных pull request без изменения семантической архитектуры в `ARCHITECTURE.md`.

Базовая платформа: Java 21, presentation-модули libGDX и чистый Java-модуль `simulation`.

## 1. Модули

```text
core/        слой приложения libGDX
lwjgl3/      desktop launcher
simulation/  детерминированный simulation/domain код без libGDX
assets/      definitions и presentation assets
```

Модуль simulation — главный авторитетный архитектурный target. Presentation не должен становиться владельцем состояния симуляции.

## 2. Реализованные области simulation

```text
io.github.evoforge.simulation
├── definition/
├── time/
└── world/
    ├── World
    ├── object/
    │   └── definition/
    ├── spatial/
    │   └── indexes/
    ├── landscape/
    │   ├── definition/
    │   └── terrain/
    │       └── storage/
    ├── mechanics/
    │   ├── physical/
    │   └── geometry/
    └── navigation/
```

Будущие пакеты не создаются только ради резервирования имён.

## 3. Объекты и идентичность

Реализованный фундамент включает:

- `ObjectId` с семантикой slot/generation;
- `WorldObject` как доменный объект;
- `ObjectRepository` для существования/идентичности;
- read-only object lookup;
- инфраструктуру создания объектов;
- object definitions, компилируемые отдельно от изменяемого runtime state.

`ObjectRepository` не используется как универсальный контейнер механик.

## 4. Definitions

Definitions строятся композицией и компилируются при bootstrap.

Текущие соглашения:

- source keys имеют стабильную строковую форму вроде `namespace:name`;
- runtime systems используют typed ids;
- runtime ids не являются persistence identity;
- loaders разрешают definitions в детерминированном startup flow;
- механики владеют собственными compiled definition data;
- добавление контента поверх существующих механик обычно должно требовать только данных.

В assets сейчас есть отдельные roots для object и landscape definitions.

## 5. Время и планирование

Реализованы:

- `SimulationClock`;
- фундамент Scheduler.

Scheduler — инфраструктура порядка времени/активации. Доменные механики не превращаются в типы scheduler task в центральном enum.

## 6. Spatial system объектов

Реализовано дискретное XYZ-позиционирование объектов:

- `TransformState`;
- `TransformLookup`;
- `SpatialSystem`;
- `ObjectSpatialIndex`;
- `CellSpatialIndex`.

Spatial хранит позиции только для WorldObject. Terrain не входит в `CellSpatialIndex`.

## 7. Landscape terrain

Базовое представление:

```text
XYZ -> LandscapeDefinitionId | absence
```

Реализованы:

- `LandscapeDefinitionId`;
- `TerrainSystem`;
- `TerrainLookup`;
- граница `TerrainStorage`;
- текущий `SparseTerrainStorage`.

`TerrainLookup.find(x,y,z)` возвращает `null` для отсутствующего terrain. `contains` выводится из этого lookup.

Текущее sparse storage — реализация, а не финальная chunk model.

## 8. Geometry

Пакет:

```text
world/mechanics/geometry/
```

Реализованы:

- `Shape`;
- `FullShape`;
- `RampShape`;
- `GeometryLookup`;
- `GeometryState`;
- `GeometrySystem`;
- `TransitionMask`;
- `TransitionPorts`;
- `TransitionComposition`;
- package-private `SolidCellBlocking`, общий для твёрдых terrain Shapes.

### 8.1 Владение Geometry

`GeometrySystem` читает `TerrainLookup`.

Для отсутствующего terrain:

```text
GeometryLookup.find(XYZ) -> null
```

Для существующего terrain без override:

```text
GeometryLookup.find(XYZ) -> FullShape.INSTANCE
```

В `GeometryState` хранятся только нестандартные Shape overrides.

### 8.2 Shape API

Текущий публичный контракт Shape:

```java
long transitionPorts(
        int relativeX,
        int relativeY,
        int relativeZ);

int transitionBlocks(
        int relativeX,
        int relativeY,
        int relativeZ);
```

По умолчанию `transitionBlocks` не возвращает блокировок.

Относительные координаты описывают текущую source-позицию Navigation относительно terrain-coordinate Shape.

Shape не имеет world lookup и не получает информацию о соседних Shape.

Текущие production structural Shapes (`FullShape` и все четыре ориентации примитивного `RampShape`) открывают одну поддерживаемую navigation-позицию в `anchor + (0,0,1)`. Соглашение ролей:

```text
departures -> исходят из этой поддерживаемой позиции
arrivals   -> подтверждают только переходы, заканчивающиеся в этой позиции
```

Это тестируется как текущий structural Shape role contract. Он не запрещает будущий Shape с действительно иной моделью поддерживаемых позиций; такой Shape потребует явного пересмотра контракта/review, а не type-specific исключения в Navigation.

### 8.3 TransitionMask

Структурный шаг — один из 26 нецентральных offsets в `3x3x3` окрестности направлений.

`TransitionMask` отображает эти offsets в биты `int`; центральный бит исключён из `ALL`.

Основные операции primitive и allocation-free:

```text
TransitionMask.of(dx,dy,dz)
TransitionMask.contains(mask,dx,dy,dz)
```

### 8.4 TransitionPorts

Departure и arrival masks упакованы в один `long` в двух непересекающихся 27-битных областях.

Helpers:

```text
of(departures, arrivals)
departuresOnly(mask)
arrivalsOnly(mask)
departures(ports)
arrivals(ports)
```

### 8.5 Композиция

Текущая структурная композиция:

```text
resolved = departures & arrivals & ~blocks
```

Navigation OR-накапливает вклады всех релевантных Shape перед вызовом `TransitionComposition.resolve`.

`resolve` дополнительно маскирует результат через `TransitionMask.ALL`, поэтому некорректные raw ports не могут вывести центральный или не-соседний бит в публичную Navigation mask.

Для внешнего edge один Shape может дать departure, а другой независимо arrival. Если одного вклада нет, edge не существует. Ни один Shape не спрашивает другой Shape о существовании соседа.

### 8.6 Блокировка твёрдой клетки

`FullShape` и `RampShape` оба представляют занятые твёрдым terrain координаты. Их общее blocking-поведение реализовано package-private `SolidCellBlocking`.

Helper не позволяет Navigation считать занятую terrain coordinate обычным проходимым пространством и блокирует прямые переходы в твёрдую клетку из локальной окрестности. Это правило живёт в Geometry, чтобы разные solid Shapes переиспользовали его без знания конкретных типов в Navigation.

### 8.7 Поведение FullShape

`FullShape.INSTANCE` — Shape по умолчанию для существующего terrain.

Текущее поведение включает:

- восемь горизонтальных departure candidates из поддерживаемой позиции прямо над Full coordinate;
- четыре cardinal `dz=+1` departure candidates, используемых только когда другой Shape даёт совпадающий arrival (например нижняя сторона Ramp);
- arrivals в верхнюю поддерживаемую позицию из соседних source-позиций того же уровня;
- cardinal downward arrivals из соседней позиции уровнем выше для независимого подтверждения спуска обратно на Full-supported position;
- строгий same-level side/corner blocking;
- прямую блокировку любого одношагового перехода, чья destination входит в занятую Full coordinate, включая vertical и diagonal-vertical entry.

Дополнительные cardinal-up departures **не** создают бесплатные Full-to-Full ступени: в плоском мире только из Full отсутствует совпадающий arrival, поэтому итоговая топология остаётся ровно из восьми горизонтальных переходов.

Реализация намеренно не заявляет универсальную модель непрерывного пересечения линий.

### 8.8 Поведение RampShape

`RampShape` — первый production Shape, меняющий Z через обычную структурную Navigation.

Есть четыре общих неизменяемых ориентации:

```text
RampShape.POSITIVE_X
RampShape.NEGATIVE_X
RampShape.POSITIVE_Y
RampShape.NEGATIVE_Y
```

Знак показывает направление подъёма ramp.

Первая production-модель намеренно примитивна: ramp — твёрдый terrain block с линейным двунаправленным структурным проходом по одной cardinal axis. Side entry и XY-diagonal entry отсутствуют.

Для `POSITIVE_Y` с terrain coordinate `(0,1,0)`:

```text
lower supported position = (0,0,0)
ramp supported position  = (0,1,1)
upper supported position = (0,2,1)
```

При наличии соответствующих соседних Shape разрешаются edges:

```text
(0,0,0) <-> (0,1,1) <-> (0,2,1)
```

Вход снизу меняет и Y, и Z одним immediate-neighbor переходом:

```text
lower -> ramp = (0,+1,+1)
ramp -> lower = (0,-1,-1)
```

Верхнее соединение горизонтально на поднятом уровне:

```text
ramp -> upper = (0,+1,0)
upper -> ramp = (0,-1,0)
```

`POSITIVE_X`, `NEGATIVE_X` и `NEGATIVE_Y` — повороты/смены знака той же топологии.

Ramp не владеет и не утверждает существование соседних поверхностей. Его ports разделены по ролям:

- Ramp arrivals подтверждают корректный вход на его поддерживаемую позицию;
- Ramp departures описывают корректные выходы из неё;
- соседний Shape должен независимо предоставить вторую роль для внешнего edge.

Поэтому удаление верхней платформы удаляет верхнее соединение, а удаление нижнего supporting Shape удаляет и ascent с этой стороны, и descent в отсутствующую нижнюю позицию. Navigation не интерпретирует эти случаи как falling.

Ramp также может предложить cardinal `dz=+1` departure к следующему Ramp. Непосредственно следующий Ramp даёт matching arrival, образуя непрерывный многоуровневый slope без искусственной Full-клетки между ramp. Если следующий Shape — поднятая Full-платформа, разрешается только горизонтальное верхнее соединение.

Ramp использует `SolidCellBlocking`, поэтому его terrain anchor сам не является navigation position и в него нельзя войти через твёрдое тело.

Общий orientation framework, fractional heights, continuous slope geometry и side movement не вводились.

### 8.9 Результат расширяемости Shape

Ключевое свойство остаётся:

```text
new Shape implementation
    -> existing Shape contract
    -> existing Transition algebra
    -> generic NavigationSystem
```

Production Ramp потребовал общего уточнения локального Geometry read envelope resolver, но конкретный тип Shape не появился в Navigation и Ramp-specific branch там не добавлялся.

## 9. Navigation

Пакет:

```text
world/navigation/
```

Реализованы:

- `NavigationLookup`;
- `NavigationSystem`.

Публичная read boundary:

```java
int transitions(
        int x,
        int y,
        int z);
```

### 9.1 Resolver

Направления структурных переходов остаются 26 непосредственными соседями:

```text
dx, dy, dz in [-1,1]
excluding (0,0,0)
```

Для одного source XYZ Navigation сейчас рассматривает Geometry в source-relative read envelope:

```text
dx in [-1,1]
dy in [-1,1]
dz in [-2,1]
```

Это максимум 36 Geometry lookups.

Дополнительный нижний Z-слой не является более длинным movement edge. Он нужен, чтобы для одношагового перехода с `dz=-1` Shape, чей terrain anchor поддерживает destination, всё ещё мог внести matching arrival. В текущей structural Shape model поддерживаемая позиция находится на одну клетку выше terrain anchor; поэтому destination на один Z ниже source может поддерживаться anchor на два Z ниже source.

Для каждого Shape в read envelope:

```text
relative source = source XYZ - Shape terrain coordinate
ports  |= shape.transitionPorts(relative source)
blocks |= shape.transitionBlocks(relative source)
```

Затем:

```text
TransitionComposition.resolve(ports, blocks)
```

В логике Navigation нет конкретных типов Shape.

### 9.2 Ориентированная топология

Navigation edges направленные. Forward transition не создаёт reverse автоматически.

Двунаправленное поведение Full и Ramp получается из независимого наличия требуемых ролей для обоих directed edges.

### 9.3 Текущее состояние cache

В текущей реализации **нет постоянного Navigation cache**.

Ранний prototype примитивного open-addressing cache был удалён во время architecture review, потому что ещё не было Movement/Pathfinder workload, оправдывающего его представление, lifecycle и memory policy.

Текущие topology queries видят актуальную Geometry на следующем вызове и не требуют ручной Navigation invalidation.

Caching может вернуться только после измерения репрезентативной нагрузки.

## 10. Тестирование Navigation и Geometry

Текущее покрытие включает:

- null dependency и стабильный lookup;
- нет geometry -> нет transitions;
- generic Shape composition без знания типов;
- плоская Full-окрестность -> ровно восемь resolved horizontal transitions;
- отсутствие поддержки плоской destination;
- строгий side blocking и corner crossing;
- direct Full blocking vertical и diagonal-vertical entry;
- локальное transition-and-lower-support read envelope (`dx/dy [-1,1]`, `dz [-2,1]`, максимум 36 lookups);
- защиту локальной арифметики от coordinate wrap на границах реализации;
- интеграцию Terrain -> Geometry -> Navigation;
- видимость удаления terrain на следующем query;
- видимость geometry override на следующем query;
- contract directed edge;
- topology `RampShape` для всех четырёх ориентаций;
- отсутствие side/XY-diagonal Ramp entry;
- solid terrain coordinate Ramp не navigable;
- missing upper Shape -> нет upper Ramp connection;
- missing lower Shape -> нет ни ascent onto Ramp, ни descent в отсутствующую lower position;
- реальный lower -> ramp -> upper traversal через Geometry + Navigation;
- reverse Ramp traversal;
- непосредственно последовательные ramps, соединяющие следующие Z-levels;
- Full blocking ascent на Ramp, когда destination terrain cell занята;
- sweep production structural Shape role-contract для `FullShape` и всех четырёх Ramp orientations;
- occupied-terrain navigation sweep в Ramp hardening scenario;
- sanitization center bit в `TransitionComposition`;
- seeded randomized comparison с намеренно более простым reference resolver.

Randomized reference test использует:

- synthetic table-driven Shapes;
- `FullShape.INSTANCE`;
- все четыре production `RampShape`.

Mutation radius выходит за locality Navigation, поэтому distant changes также проверяются на отсутствие влияния. Failure messages содержат воспроизводимый seed, mutation step и source XYZ.

## 11. Примечание о координатах

Публичные координаты сейчас используют signed `int`.

Тесты на `Integer.MIN_VALUE`/`Integer.MAX_VALUE` защищают локальную арифметику от случайного wrap в текущем resolver. Они **не** задают допустимые размеры мира EvoForge.

Границы мира и любой packed internal coordinate key остаются нерешёнными.

## 12. Текущие известные пробелы

### Lifecycle geometry override

Нестандартный Geometry override может остаться в `GeometryState`, если terrain удалить, а затем снова разместить на том же XYZ. Политика remove/re-place ещё не принадлежит lifecycle/orchestration boundary.

Не решать это обратной зависимостью `TerrainSystem -> GeometrySystem`.

### Unloaded и absent terrain

Текущие read contracts представляют отсутствие terrain как `null`. Будущая chunk/region model должна различать реальное отсутствие и not-loaded/not-generated state, если эти понятия появятся.

### Диагностика

`NavigationLookup.transitions` намеренно возвращает только primitive mask. Он не объясняет, почему направление отсутствует.

Будущий diagnostic/Inspector path может показывать departures, arrivals, blocks и contributing geometry, если реальная отладка Movement/Pathfinder этого потребует. Это не часть текущего hot read contract.

### Movement и costs

Navigation сейчас представляет только структурную топологию. Actor capabilities, occupancy, movement duration и path cost не реализованы.

### Falling

Production vertical topology существует через `RampShape`, но falling не представлен Navigation.

Отсутствующий соседний Shape не создаёт обычного structural edge. Будет ли falling моделироваться как involuntary Movement process, отдельное traversal rule или иной механизм — намеренно не решено до Basic Movement. Pathfinder не должен получать free-fall routes только из факта существования вертикальных координат.

### Более богатая семантика ramp

Текущее поведение Ramp намеренно узкое:

- одна cardinal axis;
- двунаправленный линейный passage;
- нет side entry;
- нет XY-diagonal entry;
- нет fractional surface state;
- нет общего stair/orientation framework.

Расширять это следует только по требованию реального consumer.

### Caching

Cache policy не выбрана. Будущее профилирование должно определить, лучше ли topology reuse представить отсутствием cache, chunk-local arrays, bounded maps или другой derived structure.

## 13. Детерминизм по мере появления систем

Стабильная архитектура требует:

- явный simulation RNG seed/state для авторитетной случайности;
- стабильный tie-break ordering;
- отсутствие авторитетной зависимости от порядка итерации `HashMap`/`HashSet`;
- проверку background results перед авторитетным применением.

Общего RNG service пока нет, потому что текущим механикам не нужна авторитетная случайность. Его следует вводить вместе с первым реальным random consumer, а не как неиспользуемую инфраструктуру.

## 14. Точки контроля производительности

Текущие sparse реализации Geometry/Terrain используют maps с object keys. `GeometryState.find` и `SparseTerrainStorage.find` могут аллоцировать временные cell keys в зависимости от JVM escape analysis.

Не заменять их заранее. Когда Pathfinder создаст репрезентативную Navigation workload, сначала измерить lookup allocation и throughput; это известная цель профилирования.

Текущий resolver выполняет максимум 36 локальных Geometry lookups на source query. Это намеренный correctness envelope, выведенный из текущего supported-position/arrival contract, а не performance target, который следует уменьшать ценой ослабления topology semantics.

## 15. Текущая дорожная карта

```text
DONE  Object/Definition/Scheduler/Spatial foundation
DONE  Landscape terrain core
DONE  Geometry foundation and transition algebra
DONE  Local directed Navigation resolver
DONE  Architecture/test hardening after external review
DONE  Production primitive RampShape with real Z transitions
NOW   Final Ramp/Navigation hardening and documentation alignment
NEXT  Control Backbone
LATER Scenario Harness -> Basic Movement -> Occupancy -> Pathfinder -> first agent vertical slice
```

До начала Basic Movement необходимо явно решить ownership falling. До оптимизации Pathfinder нужно измерить стоимость lookup в Navigation/Terrain/Geometry под репрезентативной нагрузкой.

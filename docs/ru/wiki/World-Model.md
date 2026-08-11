# Модель мира

World в EvoForge — не универсальный контейнер, напрямую владеющий каждой механикой. Это composition root для авторитетных владельцев подсистем и узких read boundaries.

## Текущий World

Сейчас `World` владеет `ObjectRepository`, `ObjectFactory` и `TerrainSystem`. Публичная поверхность открывает object lookup, создание объектов и terrain lookup. Geometry, Navigation, Control, Movement и будущие mechanics компонуются вокруг этих границ, а не втискиваются в один гигантский класс.

## Два основных домена

```text
WORLD
├── Objects
│   ObjectId -> WorldObject
│   ObjectId -> XYZ
│
└── Landscape
    XYZ -> LandscapeDefinitionId | absence
```

Разделение фундаментально. Индивидуальные runtime entities имеют stable identity. Terrain не нужен ObjectId только потому, что он занимает XYZ.

## Общее пространство координат

Все текущие position API используют signed integer XYZ:

```text
(int x, int y, int z)
```

Это адреса, а не общий cell owner. Одна координата в будущем может относиться к terrain, temperature, water, illumination, positioned objects, geometry и navigation при раздельном владении.

## Границы координат

Java `int` не обещает, что весь integer range является valid world space. Точные bounds deferred до требований chunk, region и world generation.

Local algorithms обязаны избегать accidental wrap. Тесты Navigation проверяют integer boundaries, чтобы арифметика не создавала ложных соседей.

## Objects

Каждый индивидуальный runtime object имеет stable `ObjectId`. Existence живёт в `ObjectRepository`, position — в spatial subsystem. Поэтому identity может существовать без position, а position storage не владеет lifetime.

Текущая repository identity — slot + generation:

```text
ObjectId = [generation:32 bits][slot:32 bits]
```

При удалении reusable slot generation увеличивается. Stale ObjectId не может незаметно ссылаться на новый object в том же slot.

## Landscape

Terrain представлен как content at coordinates:

```text
XYZ -> LandscapeDefinitionId | absence
```

Absence — семантическое отсутствие, а не special `core:air`/`core:open`. `TerrainSystem` использует заменяемую границу `TerrainStorage` и текущий `SparseTerrainStorage`. Chunking и packed region storage пока не фиксированы.

## Geometry поверх terrain

Geometry выводится из наличия terrain плюс sparse overrides:

```text
terrain absent
    -> GeometryLookup.find(XYZ) == null

terrain present, no override
    -> FullShape.INSTANCE

terrain present, custom override
    -> custom Shape
```

Material identity отделена от geometry: разные materials могут иметь один Shape, а один material позже — разные Shapes без изменения landscape definition identity.

## Navigation поверх Geometry

```text
Terrain
    ↓
Geometry
    ↓
Shape contributions
    ↓
Navigation structural edges
```

Navigation не хранит вторую authoritative terrain map. Она запрашивает `GeometryLookup` и композиционно собирает local topology.

## Направление мутации

Авторитетная мутация входит во владельца, после чего derived readers наблюдают состояние.

```text
TerrainSystem mutation
    ↓
TerrainLookup changes
    ↓
GeometryLookup reflects presence/absence
    ↓
Navigation query reflects new geometry
```

Постоянного Navigation cache сейчас нет, поэтому следующий query видит текущую geometry напрямую.

## Известный lifecycle gap

Custom geometry override сейчас может пережить удаление terrain в `GeometryState`. Если terrain затем поставить в ту же coordinate, старый override может проявиться снова.

Это lifecycle/orchestration problem. Нельзя исправлять его обратной зависимостью `TerrainSystem -> GeometrySystem`. Политика будет определена, когда terrain lifecycle commands получат корректную orchestration boundary.

## Loaded и absent

Текущий terrain lookup использует `null` для absence. Будущий chunked world может различать present, true absence и not-loaded/not-generated. Решение deferred, чтобы фундамент не изобретал chunk semantics раньше world-generation consumer.

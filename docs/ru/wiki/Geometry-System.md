# Geometry System

Geometry — mechanic поверх наличия terrain. Она владеет local shape overrides и предоставляет `Shape`, представляющий terrain geometry по coordinate.

## Почему Geometry отделена от Terrain

Terrain отвечает:

> Какой landscape content существует в этой XYZ?

Geometry отвечает:

> Какую local structural geometry открывает существующий terrain?

Это разные semantics. Granite, soil, wood и metal могут быть full blocks. Один material позже может использовать ramp или другой Shape через geometry override.

Разделение не даёт navigation topology просочиться в material definitions или `TerrainSystem` storage.

## Public read contract

```java
public interface GeometryLookup {
    Shape find(int x, int y, int z);
}
```

Текущая семантика:

```text
terrain absent
    -> null

terrain present, no custom override
    -> FullShape.INSTANCE

terrain present, custom override
    -> custom Shape
```

Consumers не обязаны отдельно спрашивать Terrain только ради факта geometry existence.

## `GeometrySystem`

`GeometrySystem` зависит только от `TerrainLookup` и владеет `GeometryState` для sparse non-default overrides.

Главная ответственность — разрешить три случая выше, оставляя default Full geometry implicit.

## `GeometryState`

Хранятся только non-default Shape overrides.

Мир из ordinary Full terrain не требует explicit Shape reference на каждую cell в geometry layer.

```text
Terrain present + no GeometryState entry
    => FullShape.INSTANCE
```

Custom RampShape хранится только там, где default заменён.

## Default geometry — семантика

`FullShape.INSTANCE` как default для present terrain — часть текущей geometry semantics, не cache optimization. Present terrain cell является solid Full block, если geometry не overridden.

Definition-driven default Shapes в будущем потребуют явного contract change.

## Shape context-free

Geometry возвращает `Shape`, но сам Shape не получает `TerrainLookup`, `GeometryLookup` или World context.

Navigation позже вычисляет его относительно Shape anchor.

```text
Geometry chooses the Shape instance
Shape declares local topology
Navigation composes many Shapes
```

Так geometry implementations не выполняют hidden world scans.

## Shared immutable Shapes

Текущие production Shapes:

```text
FullShape.INSTANCE
RampShape.POSITIVE_X
RampShape.NEGATIVE_X
RampShape.POSITIVE_Y
RampShape.NEGATIVE_Y
```

Они immutable, зависят только от relative coordinates/orientation и переиспользуются на любом числе anchors.

## Solid-cell blocking

`SolidCellBlocking` содержит common obstruction behavior для Full и Ramp terrain bodies. Helper вычисляет local block masks без world context.

Он существует потому, что уже есть несколько реальных Shape с одинаковой solid-volume semantics. Это не требование к любому будущему Shape быть solid cube.

## Geometry -> Navigation

Navigation зависит от `GeometryLookup`, а не `TerrainLookup` или internals `GeometrySystem`.

```text
TerrainLookup
    ↓
GeometrySystem / GeometryLookup
    ↓
NavigationSystem
```

Поэтому Navigation не знает mapping terrain identity -> default/custom geometry.

## Mutation

Изменение Shape override меняет topology на следующем Navigation query. Persistent Navigation cache пока нет.

Custom override можно установить только там, где terrain существует по текущей validation. Removing terrain скрывает Shape, потому что `GeometryLookup.find` сначала видит absence.

## Известный lifecycle gap override

Sparse override entry может пережить removal terrain. Если terrain re-place на том же XYZ, старый override может снова стать visible.

Проект намеренно не исправляет это callback/dependency `TerrainSystem -> GeometrySystem`.

Будущий lifecycle/orchestration layer должен определить policy:

```text
clear geometry override
preserve geometry override
restore from persisted landscape state
apply another explicit policy
```

До этого поведение документировано как known gap.

## Extension boundary

Новый Shape, совместимый с контрактом, не требует concrete-type изменений в GeometrySystem или NavigationSystem. Geometry хранит `Shape` interface reference, Navigation потребляет interface.

См. [Контракт Shape](Shape-Contract.md) и [Добавление Shape](Adding-a-Shape.md).

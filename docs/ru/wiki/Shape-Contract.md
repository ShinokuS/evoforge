# Контракт Shape

`Shape` — локальный декларативный geometry-контракт, используемый structural Navigation. Он описывает, как geometry одной terrain-клетки вносит возможные переходы вокруг своего anchor, не запрашивая мир и не зная соседей.

## Публичный API

```java
public interface Shape {
    long transitionPorts(
            int relativeX,
            int relativeY,
            int relativeZ);

    default int transitionBlocks(
            int relativeX,
            int relativeY,
            int relativeZ) {
        return TransitionMask.NONE;
    }
}
```

Аргументы описывают source position Navigation относительно terrain anchor Shape:

```text
relative source = source XYZ - shape anchor XYZ
```

Shape не получает absolute world state. Один и тот же экземпляр Shape поэтому переиспользуется в любой точке мира.

## Terrain anchor

Anchor — XYZ coordinate terrain, geometry которого описывает Shape.

Для обычной solid terrain cell:

```text
anchor        = terrain cell
standing pos  = anchor + (0,0,1)
```

Object занимает standing position над телом terrain, а не terrain coordinate.

Это особенно важно для ramps: ramp остаётся terrain cell со специальной верхней поверхностью, а не пустым navigation node с geometry вокруг него.

## Текущая structural Shape model

Production Shapes сейчас соблюдают сильное соглашение:

```text
departures открываются только из собственной standing position Shape
arrivals подтверждают переходы, чья destination равна этой standing position
```

В текущей модели:

```text
S = (0,0,1) relative to the Shape anchor
```

Departure имеет смысл только при `rel = S`.

Для movement direction `d` arrival contribution Shape появляется при source relative position:

```text
rel = S - d
```

И наоборот, arrival bit при `rel` может быть только направлением:

```text
d = S - rel
```

Так у каждого внешнего edge два независимых владельца: Shape, поддерживающий source, предлагает departure; Shape, поддерживающий destination, подтверждает arrival.

## Почему роли должны быть независимыми

Рассмотрим нижнюю Full surface, соединённую с rising Ramp.

```text
Full A  ↗  Ramp B
```

Для `A -> B`:

```text
Full A  supplies departure
Ramp B  supplies arrival
```

Если Ramp отсутствует, одного departure недостаточно.

Для `B -> A`:

```text
Ramp B  supplies departure
Full A  supplies arrival
```

Если нижний Full отсутствует, Ramp не может сам создать descent в empty space.

Это основная защита от ramp-to-nowhere и phantom topology.

## Shape не запрашивает соседей

Текущий контракт запрещает designs вроде:

```java
shape.hasNeighbor(...)
shape.findWorld(...)
shape.navigation(...)
shape instanceof SomeOtherShape
```

Shape знает только собственную local geometry. Generic Navigation resolver получает contributions от всех Shape в нужном read window и объединяет их.

## Blocks

`transitionBlocks` объявляет transition direction геометрически запрещённым из-за solid body.

Blocking независим от departure/arrival. Resolution всегда применяет blocks последними:

```text
resolved = departures & arrivals & ~blocks
```

`FullShape` и `RampShape` используют общий `SolidCellBlocking` для обычной solid-cell volume semantics.

Terrain coordinate, занятая solid Shape, не является обычной standing/navigation position. Вход в solid body из соседнего source блокируется.

## Locality, вытекающая из ролей Shape

Structural movement directions остаются immediate-neighbor:

```text
dx, dy, dz ∈ [-1,1]
not (0,0,0)
```

Для arrivals:

```text
rel = (0,0,1) - d
```

Следовательно, текущая Shape model может требовать:

```text
rel.x ∈ [-1,1]
rel.y ∈ [-1,1]
rel.z ∈ [0,2]
```

Поскольку Navigation вызывает Shape с `rel = -offset`, это даёт часть read range resolver:

```text
offset.x ∈ [-1,1]
offset.y ∈ [-1,1]
offset.z ∈ [-2,0]
```

Blocks всё ещё требуют локальную `[-1,1]^3` окрестность, а departures — Shape прямо под source. Совмещение требований даёт текущее generic read window:

```text
offset.x ∈ [-1,1]
offset.y ∈ [-1,1]
offset.z ∈ [-2,1]
```

Асимметричное Z-window — следствие контракта Shape, а не специальная проверка Ramp.

## Соглашение явное, но не вечное

Текущая production model предполагает одну standing position `anchor + (0,0,1)`. Это полезный и тестируемый инвариант, но не утверждение о любой будущей geometry.

Если реальный Shape потребует нескольких supported positions или другой связи с anchor, Shape contract и вывод read-window resolver пересматриваются вместе. Нельзя тихо обкладывать устаревший инвариант исключениями.

## Добавление нового Shape

Обычно требуется:

```text
new Shape implementation
+ topology unit tests
+ integration tests with neighboring generic Shapes
+ role-contract tests
+ solid-volume tests when applicable
```

Изменения `NavigationSystem` для распознавания concrete type не требуются.

См. [Добавление Shape](Adding-a-Shape.md) и [Алгебру переходов](Transition-Algebra.md).

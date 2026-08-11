# Контракт Shape

`Shape` — локальный декларативный geometry-контракт, используемый structural Navigation и actor-independent расчётом traversal cost. Он описывает, как geometry одной terrain-клетки вносит возможные переходы и intrinsic directed traversal-характеристики вокруг своего anchor, не запрашивая мир и не зная соседей.

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

    default int departureTraversalFactor(
            int relativeX,
            int relativeY,
            int relativeZ,
            int directionX,
            int directionY,
            int directionZ) {
        ...
    }

    default int arrivalTraversalFactor(
            int relativeX,
            int relativeY,
            int relativeZ,
            int directionX,
            int directionY,
            int directionZ) {
        ...
    }
}
```

Relative-position аргументы описывают source position Navigation относительно terrain anchor Shape:

```text
relative source = source XYZ - shape anchor XYZ
```

Direction arguments описывают один immediate directed transition:

```text
direction = destination XYZ - source XYZ
```

где:

```text
dx, dy, dz ∈ [-1, 1]
not (0,0,0)
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

Тот же ownership-закон теперь распространяется на traversal-cost factors. Source geometry вносит только свой departure factor, а destination geometry — только свой arrival factor. Ни один Shape не рассчитывает цену другого Shape.

## Shape не запрашивает соседей

Текущий контракт запрещает designs вроде:

```java
shape.hasNeighbor(...)
shape.findWorld(...)
shape.navigation(...)
shape instanceof SomeOtherShape
```

Shape знает только собственную local geometry. Generic Navigation resolver получает topology contributions от всех Shape в нужном read window. `TransitionCostCalculator` позже получает только contributions source-support и destination-support Shape для edge, который Navigation уже подтвердил.

Так Shape остаётся deterministic, reusable и независимым от world storage.

## Blocks

`transitionBlocks` объявляет transition direction геометрически запрещённым из-за solid body.

Blocking независим от departure/arrival. Resolution всегда применяет blocks последними:

```text
resolved = departures & arrivals & ~blocks
```

`FullShape` и `RampShape` используют общий `SolidCellBlocking` для обычной solid-cell volume semantics.

Terrain coordinate, занятая solid Shape, не является обычной standing/navigation position. Вход в solid body из соседнего source блокируется.

## Traversal factors

Shape может дополнительно вносить intrinsic geometry multiplier в actor-independent traversal price directed edge.

Текущая fixed-point шкала:

```text
ShapeTraversalFactor.NONE    = 0
ShapeTraversalFactor.NEUTRAL = 1000
```

Положительный factor масштабирует только local contribution, которым владеет данный Shape. Концептуально:

```text
source local contribution
    = source surface cost
      * source departure factor

destination local contribution
    = destination surface cost
      * destination arrival factor
```

Полная формула transition описана в [Movement System](Movement-System.md).

### Поведение default factor

Default methods выводятся напрямую из собственных `transitionPorts` Shape:

```text
если Shape предоставляет requested departure role:
    departureTraversalFactor = NEUTRAL
иначе:
    departureTraversalFactor = NONE

если Shape предоставляет requested arrival role:
    arrivalTraversalFactor = NEUTRAL
иначе:
    arrivalTraversalFactor = NONE
```

То есть Shape не может случайно внести neutral traversal factor в role, которым его topology не владеет, если concrete implementation специально не нарушит контракт.

Production role-contract tests проверяют, что current Shapes сохраняют alignment topology и traversal ownership.

### Override factor

Shape может override traversal factor, если intrinsic geometry действительно имеет actor-independent effect, который уже не выражается grid direction length.

Override обязан сохранять тот же role law:

```text
нет departure factor без departure port
нет arrival factor без arrival port
нет neighbor/world lookup
нет inspection concrete foreign Shape
```

Новый Shape должен менять только положительное значение factor для тех roles, которыми реально владеет.

### Текущие `FullShape` и `RampShape`

Current production `FullShape` и cardinal `RampShape` используют neutral default factor для своих owned roles.

Это сознательное решение. Ramp topology уже меняет фактический discrete direction, включая elevation change, а `GridTransitionLength` учитывает one-axis, two-axis и three-axis displacement. Дополнительный произвольный uphill/downhill multiplier сейчас не придумывается.

Future intrinsic Shape penalties можно добавить локально, когда появится реальная причина. Actor-specific различия вроде wheels vs stairs не являются universal Shape geometry и остаются отдельным future capability interaction.

## Координаты traversal roles

Для valid edge `A -> B` с direction `d` traversal calculator спрашивает тех же owners, что использует topology model.

Source support Shape:

```text
relative source = S = (0,0,1)
role            = departure
direction       = d
```

Destination support Shape:

```text
relative source = S - d
role            = arrival
direction       = d
```

Это ровно существующая departure/arrival relationship geometry. Cost-model не вводит вторую систему координат.

Для ramps это особенно важно: Shape может выставлять directed roles из разных relative source positions, оставаясь локальным к своему terrain anchor.

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

Traversal-cost calculation не повторяет 36-cell scan. После того как Navigation подтвердил edge, текущая single-standing-position model позволяет напрямую адресовать support anchors:

```text
source support      = source standing position - (0,0,1)
destination support = destination standing position - (0,0,1)
```

## Соглашение явное, но не вечное

Текущая production model предполагает одну standing position `anchor + (0,0,1)`. Это полезный и тестируемый инвариант, но не утверждение о любой будущей geometry.

Если реальный Shape потребует нескольких supported positions или другой связи с anchor, Shape contract, вывод Navigation read-window и traversal-cost support-owner lookup пересматриваются вместе. Нельзя тихо обкладывать устаревший инвариант исключениями.

## Добавление нового Shape

Обычно требуется:

```text
new Shape implementation
+ topology unit tests
+ integration tests with neighboring generic Shapes
+ role-contract tests
+ traversal-factor tests when non-neutral
+ solid-volume tests when applicable
```

Изменения `NavigationSystem` или `TransitionCostCalculator` для распознавания concrete type не требуются.

См. [Добавление Shape](Adding-a-Shape.md), [Алгебру переходов](Transition-Algebra.md) и [Movement System](Movement-System.md).

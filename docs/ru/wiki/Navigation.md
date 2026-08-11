# Navigation

Navigation предоставляет structural adjacency world positions. Она отвечает на один вопрос:

> Какие непосредственные XYZ-соседи structural connected с этой source XYZ текущей geometry?

Она намеренно не отвечает, способен ли конкретный actor пройти edge, какова цена edge, занята ли destination другим object и как найти path к далёкой цели.

## Публичный контракт

```java
public interface NavigationLookup {
    int transitions(int x, int y, int z);
}
```

Result — `TransitionMask` с нулём или несколькими из 26 immediate XYZ neighbor directions.

## Граница зависимостей

`NavigationSystem` зависит только от `GeometryLookup`:

```text
NavigationSystem
    ↓
GeometryLookup
    ↓
Shape
```

Navigation не знает:

```text
FullShape
RampShape
ObjectId
WorldObject
MovementRate
actor abilities
occupancy
TransitionCost
pathfinding algorithm
falling
terrain material identity
```

Concrete Shape checks внутри Navigation — архитектурное нарушение.

## Resolver

Для source position Navigation читает Shapes в current generic geometry read window. Для каждого anchor:

```text
relative source = source XYZ - Shape anchor XYZ
ports  |= shape.transitionPorts(relative source)
blocks |= shape.transitionBlocks(relative source)
```

Затем:

```text
TransitionComposition.resolve(ports, blocks)
```

формирует final mask.

## Movement locality и read locality

Structural edge всегда один из 26 immediate neighbors:

```text
movement delta:
    dx ∈ [-1,1]
    dy ∈ [-1,1]
    dz ∈ [-1,1]
```

Resolver read window сейчас глубже вниз:

```text
shape-anchor offset from source:
    dx ∈ [-1,1]
    dy ∈ [-1,1]
    dz ∈ [-2,1]
```

Это не разрешает longer movement. Extra lower layer нужен, чтобы destination-supporting Shape смог внести arrival под current standing-position role law.

## Почему нужен `dz = -2`

Для Ramp source `B`, descending к lower Full standing position `A`:

```text
B -> A = (0,-1,-1)

B source       z = 2
A standing     z = 1
Full anchor    z = 0
```

Full support anchor оказывается на два Z ниже source. Navigation должна прочитать его, чтобы получить arrival для `B -> A`.

Current `[-2,+1]` Z range derived из generic Shape role model, а не является Ramp-specific exception.

## Directed topology

Navigation — directed graph.

```text
transitions(A) contains d
```

не означает:

```text
transitions(A + d) contains -d
```

Оба direction разрешаются independently. Symmetric Full movement и bidirectional Ramp traversal возникают только потому, что оба edges получают свои valid contributions.

## Missing destination и solid obstruction

Нет generic check `geometryAtDestination != null`. Destination-supporting Shape обязан дать arrival. Без arrival edge исчезает через transition algebra.

Navigation также OR-ит `transitionBlocks`, поэтому solid geometry может отменить otherwise-valid departure/arrival pair без type-specific logic в Navigation.

## Связь с TransitionCost и Movement

Current runtime chain намеренно разделён:

```text
Navigation
    -> существует ли structural A -> B?

TransitionCostLookup
    -> какова actor-independent intrinsic price valid edge?

MovementSystem
    -> может ли object начать edge и сколько ticks требует его MovementRate?
```

`MovementSystem` сначала проверяет Navigation и только потом запрашивает `TransitionCost`. Cost не может создать topology, а Navigation не кодирует material/actor price.

На scheduled completion `MovementActionProcessor` снова спрашивает Navigation. Если terrain/geometry изменился и edge исчез, `SpatialSystem.move` не выполняется.

Future Pathfinder должен получать candidate edges из Navigation и price каждого edge через **тот же** `TransitionCostLookup`, что authoritative Movement.

Подробнее — [Movement System](Movement-System.md).

## Traversal factors не являются Navigation cost

`Shape` теперь имеет `departureTraversalFactor` / `arrivalTraversalFactor`, но Navigation их не читает.

Они используют тот же local role law, что topology, чтобы cost и ports соглашались об ownership. Numeric factor применяется только `TransitionCostCalculator` после подтверждения edge.

## Falling отсутствует

Missing structural edge просто отсутствует. Navigation не трактует missing floor как permission to fall.

Будущий falling должен быть отдельной involuntary mechanic/process.

## Нет persistent cache

Current Navigation вычисляет topology из актуальной Geometry на каждом query. Persistent cache/invalidation contract отсутствует.

Timed Movement уже является correctness consumer, но representative high-volume topology workload появится с Pathfinder. Только после измерений стоит решать, нужен ли:

```text
no cache
bounded query cache
chunk-local derived topology
region-derived topology
another measured representation
```

Public `NavigationLookup` должен пережить любую такую implementation replacement.

## Boundary arithmetic

Resolver защищает local coordinate arithmetic от wrap у `Integer.MIN_VALUE`/`Integer.MAX_VALUE`. Это implementation-safety contract, а не world-size declaration.

## Тестирование

Coverage включает:

```text
local resolver tests
Terrain/Geometry integration
directed edge contracts
Ramp hardening
mutation visibility
seeded independent reference resolver
integer-boundary arithmetic
Movement completion revalidation after edge removal
```

Shape role-contract tests отдельно фиксируют alignment topology roles и traversal-factor ownership.

Подробнее — [Стратегия тестирования](Testing-Strategy.md).

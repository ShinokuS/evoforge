# Navigation

Navigation предоставляет structural adjacency world positions. Она отвечает на один вопрос:

> Какие непосредственные XYZ-соседи структурно соединены с этой source XYZ текущей geometry?

Она намеренно не отвечает, способен ли конкретный actor пройти edge, какова его стоимость, занята ли destination другим object и как найти путь к далёкой цели.

## Публичный контракт

```java
public interface NavigationLookup {
    int transitions(int x, int y, int z);
}
```

Результат — `TransitionMask` с нулём или несколькими из 26 immediate XYZ neighbor directions.

## Граница зависимостей

`NavigationSystem` зависит только от `GeometryLookup`.

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
actor abilities
occupancy
pathfinding algorithm
path cost
falling
terrain material identity
```

Concrete Shape checks внутри Navigation — архитектурное нарушение.

## Алгоритм resolver

Для одной source position Navigation читает каждый Shape в текущем generic geometry read window. Для каждого Shape anchor вычисляется:

```text
relative source = source XYZ - Shape anchor XYZ
```

и накапливается:

```text
ports  |= shape.transitionPorts(relative source)
blocks |= shape.transitionBlocks(relative source)
```

После чего:

```text
TransitionComposition.resolve(ports, blocks)
```

формирует публичную mask.

## Movement locality и read locality

Это разные понятия.

Structural edge всегда один из 26 immediate neighbors:

```text
movement delta:
    dx ∈ [-1,1]
    dy ∈ [-1,1]
    dz ∈ [-1,1]
```

Read window resolver сейчас глубже вниз:

```text
shape-anchor offset from source:
    dx ∈ [-1,1]
    dy ∈ [-1,1]
    dz ∈ [-2,1]
```

Это не разрешает более длинное движение. Оно позволяет услышать Shape, terrain anchor которого находится ниже standing position, на которой заканчивается edge.

## Почему нужен `dz = -2`

Рассмотрим Ramp standing position `B`, спускающуюся диагонально к lower Full standing position `A`:

```text
B -> A = (0,-1,-1)
```

Full terrain cell, поддерживающая `A`, находится на одну coordinate ниже `A`. Относительно source `B` её anchor может оказаться на два Z уровня ниже:

```text
B source       z = 2
A standing     z = 1
Full anchor    z = 0
```

Navigation должна прочитать этот Full, чтобы он внёс arrival bit для `B -> A`. При scan только `z-1 .. z+1` reverse ramp edge исчез бы, хотя само движение остаётся одним neighbor step.

Текущий asymmetric `[-2,+1]` Z range вытекает из Shape role model, а не является Ramp-specific exception.

## Directed topology

Navigation — ориентированный граф.

```text
transitions(A) contains d
```

не означает:

```text
transitions(A + d) contains -d
```

Каждый source query разрешается независимо. Симметричный Full movement и bidirectional Ramp traversal возникают потому, что оба directions отдельно получают valid contributions.

## Отсутствующие destination

Нет generic проверки:

```java
if (geometryAtDestination == null) reject;
```

Destination-supporting Shape отвечает за arrival. Если destination не поддержан Shape, arrival отсутствует и edge естественно исчезает через transition algebra.

## Solid obstruction

Navigation также накапливает `transitionBlocks`. Shape может отменить корректную departure/arrival пару, если solid body перекрывает direction.

Так обычные terrain bodies остаются non-navigable без знания типов solid block в Navigation.

## Falling отсутствует

Отсутствующий structural edge просто отсутствует. Navigation не трактует missing floor как разрешение fall.

```text
Ramp -> empty lower space
```

не имеет normal navigation edge, потому что lower Shape не даёт required arrival.

Будущий falling должен быть отдельной involuntary mechanic/process, а не ordinary structural adjacency в empty space.

## Нет persistent cache

Текущая Navigation вычисляет topology из актуальной Geometry на каждом query. Persistent topology cache и cache invalidation contract отсутствуют.

Caching будет проектироваться после появления representative Movement/Pathfinder workload и измерений.

Возможные реализации:

```text
no cache
bounded query cache
chunk-local derived topology
region-derived topology
another measured representation
```

Публичный `NavigationLookup` должен пережить эти внутренние замены.

## Boundary arithmetic

Resolver защищает локальную coordinate arithmetic от wrap на `Integer.MIN_VALUE`/`Integer.MAX_VALUE`. Это implementation-safety tests, а не объявление полного signed-int диапазона valid world coordinates.

## Тестирование

Navigation покрыта несколькими уровнями:

- local resolver unit tests;
- integration с Geometry и Terrain;
- directed-edge contract tests;
- Ramp integration/hardening scenarios;
- mutation visibility tests;
- seeded randomized comparison с независимым reference resolver;
- integer-boundary arithmetic tests.

Подробнее — [Стратегия тестирования](Testing-Strategy.md).

# Алгебра переходов

Structural Navigation EvoForge построена на небольшой bit-mask algebra. Shapes не возвращают финальный набор moves напрямую — они вносят факты, которые затем композиционно разрешаются общим алгоритмом.

## Пространство переходов

Один structural transition идёт из source XYZ к одному из 26 непосредственных трёхмерных соседей.

```text
dx ∈ {-1,0,+1}
dy ∈ {-1,0,+1}
dz ∈ {-1,0,+1}
(dx,dy,dz) != (0,0,0)
```

Это допускает horizontal, vertical и diagonal-vertical edges при сохранении локальности каждого edge.

Подъём по ramp, например, может быть одним immediate edge:

```text
(0,+1,+1)
```

Это не прыжок через промежуточные navigation cells: source и destination отличаются максимум на единицу по каждой координате.

## `TransitionMask`

`TransitionMask` отображает локальный `3x3x3` cube направлений в биты `int`.

Center direction существует в raw indexing space, но исключён из `TransitionMask.ALL`. Публичный Navigation result не может содержать `(0,0,0)` как переход.

Основные операции:

```java
TransitionMask.of(dx, dy, dz)
TransitionMask.contains(mask, dx, dy, dz)
```

Представление primitive и allocation-free.

## Три независимых факта

Каждый Shape может внести три логически разных masks для текущего source:

```text
departures  направления, в которые source-supporting geometry разрешает выход
arrivals    направления, для которых destination geometry принимает переход
blocks      направления, перекрытые solid geometry
```

Эти роли нельзя свести в один `allowed`, потому что разные Shapes часто владеют разными половинами edge.

## `TransitionPorts`

Departure и arrival masks упакованы в один `long` в двух непересекающихся bit regions.

Концептуально:

```text
long ports = [ arrivals ][ departures ]
```

Helpers:

```text
TransitionPorts.of(departures, arrivals)
TransitionPorts.departuresOnly(mask)
TransitionPorts.arrivalsOnly(mask)
TransitionPorts.departures(ports)
TransitionPorts.arrivals(ports)
```

Packed representation сохраняет hot composition path primitive, не теряя семантическое различие ролей.

## Композиция

Для одного Navigation source contributions всех релевантных Shape OR-накапливаются:

```text
departures = dep(A) | dep(B) | dep(C) | ...
arrivals   = arr(A) | arr(B) | arr(C) | ...
blocks     = blk(A) | blk(B) | blk(C) | ...
```

Затем:

```text
resolved = departures & arrivals & ~blocks
```

и ограничение valid neighbor directions:

```text
resolved &= TransitionMask.ALL
```

Результат не зависит от порядка обработки Shape, потому что до финального boolean expression все contributions объединяются OR.

## Пример: плоский Full-to-Full edge

Object стоит над Full A и движется на восток к Full B.

```text
source A standing position       destination B standing position
          ●  ───────────────→               ●
          █                                  █
       Full A                             Full B
```

Source Full даёт east departure, destination Full — east arrival при вычислении от source относительно своего anchor.

```text
departures contains east
arrivals   contains east
blocks     does not contain east
```

East переживает composition.

Если Full B отсутствует, arrival исчезает и edge исчезает без специальной проверки “destination exists”.

## Пример: нижний Full -> Ramp

Для `POSITIVE_Y` Ramp:

```text
A -> B = (0,+1,+1)
```

Lower Full даёт diagonal-up departure, Ramp — matching arrival.

```text
Full A: departure (0,+1,+1)
Ramp B: arrival   (0,+1,+1)
```

Оба обязательны.

Для reverse:

```text
B -> A = (0,-1,-1)
```

Ramp даёт departure, lower Full — arrival. Поэтому resolver должен читать Full anchor ниже destination standing position.

## Пример: блокировка solid volume

Permission сама по себе не разрешает пройти сквозь terrain. Shape, solid body которого лежит на пути, вносит block bit. Даже при matching departure и arrival:

```text
1 & 1 & ~1 = 0
```

Direction удаляется.

`SolidCellBlocking` централизует общие solid-cell volume rules для `FullShape` и `RampShape`.

## Directed edges

Алгебра разрешает каждый source независимо. Edge не зеркалится автоматически.

```text
A -> B
```

не означает:

```text
B -> A
```

Bidirectional topology существует только если reverse query независимо получает departure, arrival и отсутствие block.

## Зачем нужна эта алгебра

Она заменяет type-specific pair logic вроде:

```java
if (sourceShape instanceof FullShape
        && destinationShape instanceof RampShape) {
    ...
}
```

локальными декларациями независимых Shape. Поэтому новая совместимая geometry автоматически компонуется с существующей.

Самые сильные тесты проверяют саму algebra: order independence, sanitization center bit, removal missing endpoint, block precedence и comparison с независимым reference resolver.

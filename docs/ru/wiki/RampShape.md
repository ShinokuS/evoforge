# RampShape

`RampShape` — первый production Shape, создающий ordinary structural Navigation edges с изменением Z. Он представляет solid terrain cell, поддерживаемая поверхность которой соединяет lower/higher neighboring positions вдоль одной cardinal axis.

Current timed Movement использует Ramp topology так же, как topology любого другого Shape. `RampShape` не содержит Movement logic и не special-case-ится в `MovementSystem`, `NavigationSystem` или `TransitionCostCalculator`.

## Ориентации

Есть четыре immutable shared instances:

```java
RampShape.POSITIVE_X
RampShape.NEGATIVE_X
RampShape.POSITIVE_Y
RampShape.NEGATIVE_Y
```

Знак задаёт horizontal direction подъёма. General orientation framework пока не нужен.

## Ментальная модель

Ramp — solid block со sloped top surface, а не empty cell, внутри которой находится object.

Для positive-Y:

```text
lower surface       ramp surface       upper surface
      ●                  ●------------------●
      █                / █                  █
      █               /  █                  █
```

Object/navigation position находится над supporting terrain geometry.

## Канонические координаты

Для Ramp anchor `(0,1,0)`, rising в `+Y`:

```text
lower standing position = (0,0,0)
ramp standing position  = (0,1,1)
upper standing position = (0,2,1)
```

Ramp terrain body занимает `(0,1,0)` и не является navigation position.

`RampShape` context-free: translation world coordinates ничего не меняет; Shape видит relative source coordinates и local direction для traversal characteristic.

## Structural edges

```text
lower <-> ramp <-> upper
```

Для positive Y:

```text
lower -> ramp = (0,+1,+1)
ramp  -> lower = (0,-1,-1)

ramp  -> upper = (0,+1,0)
upper -> ramp  = (0,-1,0)
```

Все edges остаются среди 26 immediate neighbors.

Lower connection меняет две оси, поэтому current `GridTransitionLength = 1414`; upper horizontal connection меняет одну ось и имеет length `1000`.

## Role ownership

Ramp не создаёт external edge обеими ролями самостоятельно.

### Lower ascent

```text
lower Full   departure (0,+1,+1)
Ramp         arrival   (0,+1,+1)
```

Без lower Full ascent исчезает.

### Lower descent

```text
Ramp         departure (0,-1,-1)
lower Full   arrival   (0,-1,-1)
```

Без lower Full descent тоже исчезает. Navigation не превращает отсутствие edge в falling.

### Upper connection

Для `Ramp <-> upper Full` source-side Shape даёт departure, destination-side Shape — arrival. В reverse direction роли определяются независимо.

Удаление upper platform поэтому удаляет upper edge, не разрушая существующую lower connection.

## Ramp в никуда

Ramp может существовать при missing surrounding terrain, но external edges требуют independently supported endpoints:

```text
lower Full present, upper missing:
    lower <-> Ramp     ✓
    Ramp -> upper void ✗

lower missing, upper present:
    Ramp -> lower void ✗
```

Ordinary structural movement в empty space отсутствует.

## Последовательные ramps

Два ramps могут соединяться directly:

```text
Ramp1 anchor = (0,1,0)
Ramp2 anchor = (0,2,1)

Ramp1 standing = (0,1,1)
Ramp2 standing = (0,2,2)

Ramp1 -> Ramp2 = (0,+1,+1)
Ramp2 -> Ramp1 = (0,-1,-1)
```

Source Ramp предоставляет departure, destination Ramp — matching arrival. Так строится continuous slope.

## Side entry и solid volume

Primitive Ramp — linear passage вдоль rise axis. Side entry и XY-diagonal mouth semantics отсутствуют.

Ramp terrain coordinate solid. `transitionBlocks` использует `SolidCellBlocking`, как Full, поэтому terrain body не становится empty space.

## Роль в TransitionCost

Ramp участвует в actor-independent `TransitionCost` через тот же local departure/arrival law, что любой Shape.

Shape API:

```text
departureTraversalFactor(...)
arrivalTraversalFactor(...)
```

Current `RampShape` не override-ит их и наследует default:

```text
owned topology role -> ShapeTraversalFactor.NEUTRAL = 1000
not owned           -> ShapeTraversalFactor.NONE    = 0
```

Это сознательно. Current displacement Ramp уже выражен самим directed transition и `GridTransitionLength`. Edge `(0,+1,+1)` автоматически длиннее horizontal `(0,+1,0)`, поэтому arbitrary extra uphill/downhill multiplier сейчас не придумывается.

Для valid Ramp-related edge `TransitionCostCalculator` объединяет:

```text
source landscape traversal.cost
source Shape departure factor
destination landscape traversal.cost
destination Shape arrival factor
grid direction length
```

Ни Movement, ни calculator не содержат `instanceof RampShape`.

Если future evidence покажет intrinsic actor-independent geometry penalty, Ramp сможет override только свой directed factor. Actor-specific rule вроде «wheeled object плохо идёт по ramp» относится к future mover/geometry capability interaction, а не universal Ramp factor.

Полная formula — в [Movement System](Movement-System.md).

## Current Movement semantics на Ramp

`MoveStepCommand` на structurally valid Ramp edge проходит общий timed lifecycle:

```text
Navigation confirms edge
    ↓
TransitionCost prices edge
    ↓
MovementRate + carry derive duration
    ↓
MovementAction sleeps
    ↓
completion revalidates Navigation
    ↓
SpatialSystem.move commits destination if still valid
```

Пока action active, authoritative Spatial position остаётся в source standing position. Continuous slope coordinate или per-tick interpolation в simulation state отсутствуют.

Первая debug-визуализация поэтому может показывать discrete jump между standing cells только на completion tick.

## Нет actor semantics и world lookup

Ramp не знает, может ли mover ходить, climb, fly, fit physically или какой у него `MovementRate`.

Он также не спрашивает, существует ли Full/другой Ramp у mouths. Shape объявляет только свои departures, arrivals, blocks и local traversal factors. Других owners независимо получает Navigation/TransitionCost.

## Тестирование

Покрытие включает:

```text
all four orientations
exact local port masks
solid body blocking
no side/XY-diagonal entry
lower <-> Ramp <-> upper integration
missing upper endpoint
missing lower endpoint for ascent/descent
consecutive Ramp chains
reverse traversal
occupied destination blocking
production role-contract sweep
traversal factor == NEUTRAL exactly for owned roles
```

Missing-lower-descent test защищает от descent в unsupported void. Separate TransitionCost tests доказывают extensibility non-neutral custom Shape factors без concrete-type branching. Current Ramp остаётся neutral by design.

# RampShape

`RampShape` — первый production Shape, создающий обычные structural Navigation edges с изменением Z. Он представляет solid terrain cell, поддерживаемая поверхность которой соединяет lower и higher neighboring positions вдоль одной cardinal axis.

## Ориентации

Есть четыре immutable shared instances:

```java
RampShape.POSITIVE_X
RampShape.NEGATIVE_X
RampShape.POSITIVE_Y
RampShape.NEGATIVE_Y
```

Знак задаёт horizontal direction подъёма ramp.

Общего orientation framework нет: четыре cardinal instances прямо выражают всё текущее требуемое поведение.

## Ментальная модель

Ramp — solid block со sloped top surface, похожий на простую stair/ramp клетку игры. Это не пустая cell, внутри которой находится object.

Для positive-Y:

```text
lower surface       ramp surface       upper surface
      ●                  ●------------------●
      █                / █                  █
      █               /  █                  █
```

Object/navigation position остаётся над supporting terrain geometry.

## Канонические локальные координаты

Для Ramp anchor `(0,1,0)`, rising в `+Y`:

```text
lower standing position = (0,0,0)
ramp standing position  = (0,1,1)
upper standing position = (0,2,1)
```

Terrain body Ramp занимает:

```text
(0,1,0)
```

и не является navigation position.

Перенос всех координат на один vector ничего не меняет: `RampShape` context-free и видит только relative source coordinates.

## Structural edges

Базовый connector:

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

Каждый edge остаётся среди 26 immediate-neighbor directions.

## Владение ролями

Ramp не создаёт external edges, голосуя за обе стороны.

### Lower ascent

```text
Full lower surface -> Ramp
```

```text
lower Full   departure (0,+1,+1)
Ramp         arrival   (0,+1,+1)
```

Без lower Full отсутствует departure и ascent исчезает.

### Lower descent

```text
Ramp -> lower Full surface
```

```text
Ramp         departure (0,-1,-1)
lower Full   arrival   (0,-1,-1)
```

Без lower Full descent тоже исчезает. Navigation не превращает его в falling.

### Upper connection

Для `Ramp <-> upper Full surface` Ramp даёт departure при выходе со своей standing position, а upper Full — destination arrival. В reverse direction upper Full даёт departure, Ramp подтверждает arrival.

Удаление upper platform удаляет upper edge, сохраняя корректную связь назад к существующей lower surface.

## Ramp в никуда

Ramp может геометрически существовать при отсутствии окружающего terrain, но Navigation edges требуют независимо поддерживаемых endpoints.

```text
lower Full present, upper missing:
    lower <-> Ramp     ✓
    Ramp -> upper void ✗

lower missing, upper present:
    Ramp -> lower void ✗
```

Обычного structural movement в empty space нет.

## Последовательные ramps

Два ramps могут соединяться непосредственно без fake flat Full между ними.

```text
Ramp1 anchor = (0,1,0)
Ramp2 anchor = (0,2,1)
```

Standing positions:

```text
Ramp1 standing = (0,1,1)
Ramp2 standing = (0,2,2)
```

Connection:

```text
Ramp1 -> Ramp2 = (0,+1,+1)
Ramp2 -> Ramp1 = (0,-1,-1)
```

Source Ramp предлагает соответствующий Ramp-to-Ramp departure, destination Ramp — matching arrival. Так строится continuous slope из повторяющихся Ramp cells.

## Side entry

Primitive Ramp — линейный passage вдоль rise axis. Side entry и XY-diagonal mouth semantics отсутствуют.

Для positive-Y Ramp обычный вход с ±X не входит в Shape topology.

## Solid volume

Terrain coordinate Ramp solid. `RampShape.transitionBlocks` делегирует `SolidCellBlocking`, как Full.

Это защищает от бага, когда тело Ramp ведёт себя как empty space, хотя topology существует над ним.

## Нет actor semantics

Ramp не знает, может ли moving object ходить, карабкаться, ползать, летать или физически помещаться.

Shape сообщает только structural connection terrain geometry. Будущая Movement/capability logic решит, может ли конкретный actor использовать edge.

## Нет world lookup

Ramp не спрашивает, существует ли Full, другой Ramp или что-то ещё у mouths. Он объявляет только собственные departures, arrivals и solid blocks. Navigation независимо получает остальные Shape и делает composition.

## Тестирование

Покрытие включает:

```text
all four orientations
exact local port masks
solid body blocking
no side/XY-diagonal entry
lower <-> Ramp <-> upper integration
missing upper endpoint
missing lower endpoint for ascent and descent
consecutive Ramp chains
reverse traversal
occupied transition destination blocking
```

Особенно важен missing-lower-descent test: Ramp не должен создавать normal descent в unsupported empty space сам по себе.

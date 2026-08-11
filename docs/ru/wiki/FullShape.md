# FullShape

`FullShape` — geometry по умолчанию для существующего terrain. Это solid terrain cell с одной поддерживаемой standing position прямо над terrain anchor.

## Instance

```java
FullShape.INSTANCE
```

Shape immutable и общий для всего мира. Поведение зависит только от relative source coordinates.

## Anchor и standing position

```text
terrain anchor   F = (x,y,z)
standing pos     S = (x,y,z+1)
```

```text
S  ●   object/navigation position
   █
F  █   Full terrain anchor
```

Terrain coordinate — solid volume и не ordinary navigation position.

## Horizontal departures

Из собственной standing position Full открывает восемь horizontal directions:

```text
(-1,-1,0)  (0,-1,0)  (+1,-1,0)
(-1, 0,0)             (+1, 0,0)
(-1,+1,0)  (0,+1,0)  (+1,+1,0)
```

Это только departures. Neighboring destination-supporting Shape должен предоставить matching arrival.

Поэтому удаление одного neighboring Full удаляет только соответствующий flat transition.

## Cardinal upward departures

Full также предлагает четыре cardinal diagonal-up departures:

```text
(-1,0,+1)
(+1,0,+1)
(0,-1,+1)
(0,+1,+1)
```

Они **не** создают бесплатные one-block stairs между Full blocks.

Это source-side offers. Edge появляется только если destination Shape даёт matching arrival. Нижние mouths текущего `RampShape` дают такие arrivals; обычный Full — нет.

Поэтому flat Full world всё ещё имеет ровно восемь horizontal edges, а Full-to-Full step-up недоступен.

## Arrivals на верхнюю поверхность

Когда Navigation рассматривает Full из соседнего source на той же top plane, Full вносит единственный horizontal arrival, заканчивающийся на собственной standing position.

```text
source Full       destination Full
 departure   +       arrival
              ↓
          resolved edge
```

## Arrivals для спуска с более высокого соседа

Для reverse Ramp descent Full принимает cardinal diagonal-down transition, который приходит на его standing position из source на один horizontal step и один Z выше.

Такой source может быть на два Z выше Full terrain anchor, поэтому generic read window Navigation достигает `sourceZ - 2`.

Это следствие destination support, а не special case Navigation.

## Solid volume

`FullShape` делегирует blocking в `SolidCellBlocking`.

Helper не позволяет входить в occupied terrain body и применяет строгий same-level side/corner blocking, чтобы diagonal movement не прорезал solid side.

Примеры:

```text
horizontal entry into the Full cell       blocked
vertical entry into the Full cell         blocked
diagonal-vertical entry into the cell     blocked
corner crossing past an occupied side     blocked
```

## Нет автоматического falling

Full surface не предлагает обычные downward edges только потому, что рядом empty space. Cliff является границей structural navigation graph:

```text
●  -> empty space
█     no supported destination

result: no structural edge
```

Falling при появлении будет отдельной involuntary mechanic.

## Нет бесплатной Full-ступени

Два Full blocks на разных Z не образуют автоматически climbable stairs.

Lower Full может предложить cardinal-up departure, но higher Full не даёт Ramp-style arrival. Composition остаётся zero.

Правило: изменение elevation требует geometry, явно поддерживающей его.

## Тесты

`FullShapeTest` проверяет:

```text
horizontal and cardinal-up departure masks
horizontal arrivals into the top surface
higher-source diagonal-down arrivals
solid side/corner blocking
vertical and diagonal-vertical body blocking
no ports/blocks outside the intended local domain
```

Navigation tests дополнительно доказывают восемь edges на flat Full и отсутствие Full-to-Full vertical steps.

# FullShape

`FullShape` is the default geometry for present terrain. It represents a solid terrain cell with one supported standing position directly above the terrain anchor.

## Instance

```java
FullShape.INSTANCE
```

The Shape is immutable and shared globally. Its behavior depends only on relative source coordinates.

## Anchor and standing position

For a Full terrain anchor:

```text
terrain anchor   F = (x,y,z)
standing pos     S = (x,y,z+1)
```

Diagram:

```text
S  ●   object/navigation position
   █
F  █   Full terrain anchor
```

The terrain coordinate itself is solid volume and is not an ordinary navigation position.

## Horizontal departures

From its own standing position, Full exposes the eight horizontal directions:

```text
(-1,-1,0)  (0,-1,0)  (+1,-1,0)
(-1, 0,0)             (+1, 0,0)
(-1,+1,0)  (0,+1,0)  (+1,+1,0)
```

These are departures only. A neighboring destination-supporting Shape must provide the matching arrival before Navigation resolves an edge.

This is why removing one neighboring Full surface removes only the corresponding flat transition.

## Cardinal upward departures

Current Full also exposes four cardinal diagonal-up departures:

```text
(-1,0,+1)
(+1,0,+1)
(0,-1,+1)
(0,+1,+1)
```

These do **not** create free one-block stairs between Full blocks.

They are only source-side offers. A structural edge appears only if some destination Shape contributes the matching arrival. Current `RampShape` lower mouths provide those arrivals; ordinary Full geometry does not.

Therefore a flat Full world still resolves exactly eight horizontal edges, and Full-to-Full step-up remains unavailable.

## Arrivals into the top surface

When Navigation evaluates a Full from a neighboring source position on the same top plane, Full contributes the single horizontal arrival direction that ends on its own standing position.

This gives flat movement independent endpoint ownership:

```text
source Full       destination Full
 departure   +       arrival
              ↓
          resolved edge
```

## Arrivals for descent from a higher neighbor

To support the reverse direction of Ramp descent, Full also accepts the cardinal diagonal-down transition that lands on its standing position from a source one horizontal step away and one Z higher.

Because that source can be two Z coordinates above the Full terrain anchor, Navigation's generic read window extends to Shape anchors at `sourceZ - 2`.

This is a consequence of destination support, not a special case in Navigation for Full or Ramp.

## Solid volume

`FullShape` delegates blocking to `SolidCellBlocking`.

The helper prevents transitions into the occupied terrain body and applies strict same-level side/corner blocking so a path cannot cut through a solid side while moving diagonally around it.

Examples include:

```text
horizontal entry into the Full cell       blocked
vertical entry into the Full cell         blocked
diagonal-vertical entry into the cell     blocked
corner crossing past an occupied side     blocked
```

## No automatic falling

A Full surface does not offer ordinary downward edges simply because empty space exists beside it. A cliff is therefore an edge of the structural navigation graph:

```text
●  -> empty space
█     no supported destination

result: no structural edge
```

Falling, if later introduced, is a separate involuntary mechanic.

## No free Full step

Two Full blocks at different Z levels do not automatically create climbable stairs.

```text
lower Full  ↗  higher Full
```

The lower Full may offer a cardinal-up departure, but the higher Full does not provide the Ramp-style arrival required for that edge. The composition therefore remains zero.

This preserves the semantic rule that elevation change requires geometry that explicitly supports it.

## Tests

`FullShapeTest` validates:

```text
horizontal and cardinal-up departure masks
horizontal arrivals into the top surface
higher-source diagonal-down arrivals
solid side/corner blocking
vertical and diagonal-vertical body blocking
no ports/blocks outside the intended local domain
```

Navigation tests additionally prove that flat Full neighborhoods still resolve exactly eight edges and that Full-to-Full vertical steps remain absent.

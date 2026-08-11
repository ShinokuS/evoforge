# RampShape

`RampShape` is the first production Shape that creates ordinary structural Navigation edges with a Z change. It represents a solid terrain cell whose supported surface connects lower and higher neighboring positions along one cardinal axis.

Ramp topology is consumed by current timed Movement exactly like any other structural Shape. Ramp does not contain Movement code and is not special-cased by `MovementSystem`, `NavigationSystem`, or `TransitionCostCalculator`.

## Orientations

Four immutable shared instances exist:

```java
RampShape.POSITIVE_X
RampShape.NEGATIVE_X
RampShape.POSITIVE_Y
RampShape.NEGATIVE_Y
```

The sign indicates the horizontal direction in which the ramp rises.

No general orientation framework exists because four cardinal instances currently express all required behavior directly.

## Mental model

Treat a Ramp like a solid block with a sloped top surface, similar to a simple game stair/ramp block.

It is not an empty cell that an object occupies internally.

For a positive-Y example:

```text
lower surface       ramp surface       upper surface
      ●                  ●------------------●
      █                / █                  █
      █               /  █                  █
```

The object/navigation position remains above supporting terrain geometry.

## Canonical local coordinates

For a Ramp anchor at `(0,1,0)` rising in `+Y`:

```text
lower standing position = (0,0,0)
ramp standing position  = (0,1,1)
upper standing position = (0,2,1)
```

The Ramp terrain body itself occupies:

```text
(0,1,0)
```

and is not a navigation position.

Translating every coordinate by the same vector changes nothing. `RampShape` is context-free and only sees relative source coordinates plus the local direction requested for traversal characteristics.

## Structural edges

The basic connector is:

```text
lower <-> ramp <-> upper
```

For positive Y:

```text
lower -> ramp = (0,+1,+1)
ramp  -> lower = (0,-1,-1)

ramp  -> upper = (0,+1,0)
upper -> ramp  = (0,-1,0)
```

Every edge remains within the 26 immediate-neighbor directions.

The lower connection changes two axes and therefore has current `GridTransitionLength = 1414`; the upper horizontal connection changes one axis and has length `1000`.

## Role ownership

Ramp does not create external edges by providing both sides of the vote.

### Lower ascent

```text
Full lower surface -> Ramp
```

Contributions:

```text
lower Full   departure (0,+1,+1)
Ramp         arrival   (0,+1,+1)
```

If the lower Full is absent, no departure exists and ascent disappears.

### Lower descent

```text
Ramp -> lower Full surface
```

Contributions:

```text
Ramp         departure (0,-1,-1)
lower Full   arrival   (0,-1,-1)
```

If the lower Full is absent, descent also disappears. Navigation does not convert this into falling.

### Upper connection

```text
Ramp <-> upper Full surface
```

Ramp provides the source-side departure when leaving its standing position. The upper Full provides the destination arrival. In the reverse direction, upper Full provides the departure and Ramp confirms the arrival.

Removing the upper platform therefore removes the upper edge while preserving the valid connection back down to an existing lower surface.

## Ramp to nowhere

A Ramp is allowed to exist geometrically even if surrounding terrain is missing, but its Navigation edges still require independently supported endpoints.

```text
lower Full present, upper missing:
    lower <-> Ramp     ✓
    Ramp -> upper void ✗

lower missing, upper present:
    Ramp -> lower void ✗
```

No ordinary structural movement into empty space exists.

## Consecutive ramps

Two ramps can connect directly without inserting a fake flat Full cell between them.

For two positive-Y ramps:

```text
Ramp1 anchor = (0,1,0)
Ramp2 anchor = (0,2,1)
```

standing positions:

```text
Ramp1 standing = (0,1,1)
Ramp2 standing = (0,2,2)
```

connection:

```text
Ramp1 -> Ramp2 = (0,+1,+1)
Ramp2 -> Ramp1 = (0,-1,-1)
```

The source Ramp offers the corresponding Ramp-to-Ramp departure, while the destination Ramp contributes the matching arrival from its lower mouth/higher relative source role.

This creates continuous slopes through repeated Ramp cells.

## Side entry

The primitive Ramp is a linear passage along its rise axis. It exposes no side entry and no XY-diagonal mouth semantics.

For a positive-Y Ramp, ordinary entry from ±X sides is not part of the Shape topology.

## Solid volume

The Ramp terrain coordinate is solid. `RampShape.transitionBlocks` delegates to `SolidCellBlocking`, the same common volume rule used by Full.

This prevents the bug where a Ramp's terrain body behaves like empty space while its upper topology exists above it.

## Traversal-cost role

Ramp participates in the current actor-independent `TransitionCost` through the same local departure/arrival law as every other Shape.

The Shape API provides:

```text
departureTraversalFactor(...)
arrivalTraversalFactor(...)
```

Current `RampShape` does **not** override them. It inherits the default behavior:

```text
owned topology role -> ShapeTraversalFactor.NEUTRAL = 1000
not owned           -> ShapeTraversalFactor.NONE    = 0
```

This is deliberate. The ramp's current geometric displacement is already represented by the actual directed transition and `GridTransitionLength`. A lower/ramp edge such as `(0,+1,+1)` is longer than a horizontal `(0,+1,0)` edge without inventing an arbitrary additional uphill/downhill multiplier.

For a valid Ramp-related edge, `TransitionCostCalculator` combines:

```text
source landscape traversal.cost
source Shape departure factor

destination landscape traversal.cost
destination Shape arrival factor

grid direction length
```

Neither Movement nor the calculator contains `instanceof RampShape`.

If future evidence shows an intrinsic actor-independent geometry penalty that distance alone cannot express, Ramp may override only its own directed factor. Actor-specific rules such as a wheeled object struggling on a ramp are a separate future mover/geometry capability interaction and must not be encoded as universal Ramp geometry by default.

See [Movement System](Movement-System.md) for the complete formula.

## Current Movement semantics on ramps

A `MoveStepCommand` targeting a structurally valid Ramp-related neighbor starts the same timed `MovementAction` as a flat edge:

```text
Navigation confirms the directed edge
    ↓
TransitionCost prices it
    ↓
MovementRate + carry derive duration
    ↓
MovementAction sleeps until scheduled completion
    ↓
Navigation is revalidated
    ↓
SpatialSystem.move commits destination if still valid
```

The object's authoritative position remains at the source standing position during the action. There is no special continuous slope coordinate or per-tick ramp interpolation in simulation state.

The planned first visual/debug view may therefore show the object jumping discretely from one standing cell to the next when the action completes.

## No actor semantics inside Ramp

Ramp does not know whether the moving object can walk, climb, crawl, fly, fit physically, or has a particular `MovementRate`.

Shape says what local terrain geometry structurally supports and what intrinsic local traversal characteristic it contributes. Movement capability and future actor-specific policy remain outside Shape.

## No world lookup

Ramp never asks whether Full terrain, another Ramp, or anything else exists at its mouths. It declares only its own source departures, destination arrivals, solid blocks, and local traversal factors. Navigation/TransitionCost obtain the other owners independently.

## Testing

Ramp coverage includes:

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
production role-contract sweep
traversal factor == NEUTRAL exactly for owned departure/arrival roles
```

The missing-lower-descent test is especially important: it ensures Ramp cannot create a normal descent into unsupported empty space by itself.

TransitionCost tests separately prove that non-neutral custom Shape factors can alter directed cost without teaching central code concrete Shape types. Current Ramp remains neutral by design.

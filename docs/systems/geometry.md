# Geometry and Shape

## Purpose

Describe local physical/structural geometry over Terrain without coupling generic consumers to concrete Shape classes.

## Ownership

Geometry is separate from terrain material identity.

A present terrain coordinate with no explicit Geometry override resolves to `FullShape.INSTANCE`; sparse Geometry state stores only non-default Shape overrides. The terrain coordinate is the Shape **anchor**. Under the current supported-position model, ordinary standing/navigation space is normally one Z above its supporting terrain anchor.

`WorldGeometryLookup` may wrap that terrain-backed Geometry with optional finite `WorldBounds`. Inside bounds it delegates normally; outside it returns `FullShape.INSTANCE`. This provides shared physical containment without storing fake boundary Terrain.

## Shape contract

`Shape` is an open declarative local contract with two independent fact families:

```text
physical cell geometry
    solidVolume()
    freeVolumeBelow(localHeight)
    boundaryOpeningFloor(CellFace)

structural traversal geometry
    transitionPorts(relativeSource)
    transitionBlocks(relativeSource)
    departureTraversalFactor(relativeSource, direction)
    arrivalTraversalFactor(relativeSource, direction)
```

Physical methods describe occupied/free cell-local space and physical face connectivity. Transition methods describe structural standing/movement roles. Consumers must not reinterpret one family as the other merely because both describe the same Shape.

The relative traversal source is:

```text
source standing XYZ - Shape anchor XYZ
```

and direction is one immediate delta in `[-1,1]` on each axis, excluding `(0,0,0)`.

A Shape receives no absolute World, neighbor lookup, Navigation, ObjectId, Water or Pathfinder. The same Shape value can therefore be reused at any translation.

## Fixed-point geometry scale

`CellVolume` is a deterministic material-agnostic fixed-point volume scale:

```text
EMPTY = 0
FULL  = 1_000_000
```

It is a fraction of one discrete cell volume, not litres or cubic metres.

`CellSpace` uses the same numeric resolution for normalized local height:

```text
EMPTY_HEIGHT = 0
FULL_HEIGHT  = 1_000_000
```

Height and volume are different quantities even though they share one resolution.

## Solid/free-space profile

`Shape.solidVolume()` reports approximate occupied volume in the anchor cell.

Current production Shapes include:

```text
FullShape  -> full solid cell
RampShape  -> approximately half-cell solid wedge
```

A scalar volume is not enough when a consumer cares where free space lies. `freeVolumeBelow(localHeight)` therefore reports how much free cell-local volume exists below a height.

For empty open space:

```text
freeVolumeBelow(h) = h
```

For `FullShape`: zero.

For current cardinal `RampShape`, whose solid surface rises linearly, the complementary free wedge follows:

```text
freeVolumeBelow(h) = h^2 / 2
```

`CellSpace.surfaceHeight(shape, volume)` deterministically inverts the monotonic free-space profile using integer binary search. Water is the first consumer, but the helper is neutral Geometry.

The default Shape implementation remains conservative/lightweight for custom/test Shapes: it derives total free capacity from solid volume and distributes that free capacity uniformly over height. Production Shapes with important internal geometry override the profile.

## Physical face openings

`CellFace` names six objective cell faces. It is not a movement direction enum.

`Shape.boundaryOpeningFloor(face)` reports the lowest local height at which Shape free space connects through that face, or `CellSpace.CLOSED` when there is no connection.

The default Shape implementation is conservative and closed: spare volume alone does not prove neighbor connectivity.

Current open-space semantics are:

```text
horizontal faces  opening floor 0
bottom face       opening floor 0
top face          full-cell height
```

Current Ramp semantics are:

```text
low face                0
perpendicular side faces 0
high face               CLOSED
bottom face             CLOSED
top face                full-cell height
```

This first-order sill model is enough for current Water. A future arch/tunnel/multiple-hole Shape that cannot be represented by one lower sill should extend a **neutral physical boundary profile**, not add `instanceof` logic inside Water and not reuse Navigation transition ports.

## Structural transition roles

`transitionPorts` contributes independent departure/arrival masks; `transitionBlocks` contributes obstruction.

Generic resolution is conceptually:

```text
resolved = departures & arrivals & ~blocks
```

Because contributions are accumulated before resolution, topology does not depend on processing order or concrete Shape type.

### Current supported-position law

Current `FullShape` and cardinal `RampShape` use one supported standing position relative to the terrain anchor:

```text
S = (0, 0, 1)
```

The source-support Shape offers departure from its supported position. For an edge `d`, the Shape supporting the destination confirms arrival when queried from:

```text
relative source = S - d
```

Every external edge therefore needs compatible source departure and destination arrival ownership.

This one-supported-position model is current, not eternal. A real future Shape needing multiple standing positions must drive a coordinated contract revision rather than local exceptions.

## Traversal factors

`ShapeTraversalFactor.NONE = 0` means the Shape does not own the requested role. `NEUTRAL = 1000` means the owned role adds no multiplier.

Default factor methods derive role ownership from transition ports. Specialized factors must obey the same role law and may not query neighbor/world state.

Current Full/Ramp factors are neutral. Ramp elevation already changes the discrete direction and therefore grid-length contribution; no arbitrary built-in uphill penalty exists.

Actor-specific preferences are not universal Shape geometry and remain separate mover mechanics.

## Navigation read locality

For current immediate directions and supported-position role `S`, arrival ownership can require Shape-relative source Z `0..2`. Combined with departure support/solid blocking, current Navigation reads Shape anchors in:

```text
X [-1, 1]
Y [-1, 1]
Z [-2, 1]
```

This is read locality, not movement distance. Structural edges remain immediate neighbors.

TransitionCost does not repeat the full Navigation scan after an edge is known valid; current support anchors are directly addressable one Z below source/destination standing cells.

## World boundary composition

`WorldGeometryLookup` wraps the ordinary terrain-backed Geometry used by production runtime composition.

```text
no configured WorldBounds
    -> delegate Geometry everywhere

configured WorldBounds
    inside  -> delegate Geometry
    outside -> FullShape
```

The outside result is physical/structural closure only. It does not create terrain material identity or mutable state beyond the runtime box.

This shared boundary is consumed naturally by Navigation, Water and Movement-related Geometry queries. Generic consumers must not add their own coordinate-edge special cases.

## Generic-consumer rule

Navigation, TransitionCost, Water and generic presentation renderers do not branch on current/future concrete Shape classes.

Simulation consumes `Shape` / `CellSpace` contracts. Presentation localizes concrete visual knowledge to typed `ShapePresentation<S>` bindings registered at the presentation composition root.

If a future Shape fits the current contract, existing generic consumers should not require changes simply to recognize the new concrete type.

## Diagnostics and tests

Tests cover Shape ports/blocks, solid/free-volume profiles, physical face-opening floors, role/factor ownership, terrain lifecycle, world-bound closure and integer/locality hardening. Navigation, TransitionCost and Water integration tests verify that the independent Shape fact families are consumed consistently.

See [Shape Transition Algebra decision](../decisions/002-shape-transition-algebra.md), [Typed Presentation Bindings decision](../decisions/004-typed-presentation-bindings.md), [Navigation](navigation.md) and [Water](water.md).

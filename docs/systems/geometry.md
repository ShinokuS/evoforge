# Geometry and Shape

## Purpose

Describe local structural and physical geometry over existing terrain without coupling generic consumers to concrete Shape classes.

## Ownership

Geometry is separate from terrain material identity. Presence of terrain with no override resolves to `FullShape.INSTANCE`; sparse geometry state stores only non-default Shape overrides.

A terrain coordinate is the Shape **anchor**. Under the current model, the ordinary standing/navigation position supported by that terrain is one Z above the anchor.

## Shape contract

`Shape` is an open declarative local contract. Its public responsibilities now include two independent families of facts:

```text
physical cell geometry
    solidVolume()
    freeVolumeBelow(localHeight)
    boundaryOpeningFloor(CellFace)

structural traversal geometry
    transitionPorts(relative source)
    transitionBlocks(relative source)
    departureTraversalFactor(relative source, direction)
    arrivalTraversalFactor(relative source, direction)
```

The physical methods describe objective cell-local occupied/free space. Transition ports/blocks/factors describe structural traversal roles. A consumer must not reinterpret one contract as the other simply because both describe the same Shape.

The relative traversal source is:

```text
source standing XYZ - Shape terrain anchor XYZ
```

and the direction is one immediate transition:

```text
destination XYZ - source XYZ
```

with each delta in `[-1,1]` and not all zero.

A Shape receives no absolute World, neighbor lookup, Navigation, ObjectId, Water or pathfinder. The same Shape value can therefore be reused at any translation.

## Cell-local volume and height

`CellVolume` is a deterministic material-agnostic fixed-point scale:

```text
CellVolume.EMPTY = 0
CellVolume.FULL  = 1_000_000
```

The number is a fraction of one discrete world-cell volume. It is deliberately **not** a statement that one cell is one cubic metre, one litre or any other physical unit. A future physical scale can convert the normalized cell volume without changing Shape geometry.

`CellSpace` uses the same numeric resolution for normalized local height:

```text
CellSpace.EMPTY_HEIGHT = 0
CellSpace.FULL_HEIGHT  = 1_000_000
```

Height and volume are different quantities even though they intentionally share one fixed-point resolution.

## Solid volume

`Shape.solidVolume()` reports the approximate amount of the Shape anchor cell occupied by solid terrain geometry:

```text
FullShape = 1.0 cell
RampShape = 0.5 cell
```

The ramp value is intentionally coarse. It captures the useful geometric fact that a wedge occupies approximately half of its anchor cell without introducing meshes or voxel subcells.

## Free-space profile

A scalar solid volume is not enough for consumers that care where free volume lies. `Shape.freeVolumeBelow(localHeight)` therefore answers how much free cell-local volume exists below a normalized height.

For an empty coordinate with no Shape:

```text
freeVolumeBelow(h) = h
```

For `FullShape` it is always zero.

For a cardinal `RampShape`, the solid surface rises linearly from local height `0` to `1`, so the free wedge below height `h` follows the triangular profile:

```text
freeVolumeBelow(h) = h^2 / 2
```

on the normalized fixed-point scale. At full height the result is `0.5` cell, matching `solidVolume()`.

The default Shape implementation remains deliberately lightweight: it derives total free capacity from `solidVolume()` and distributes that free capacity uniformly over height. This preserves existing/custom test Shapes without pretending that unknown geometry has richer physical detail. Production Shapes whose internal geometry matters should override the profile.

`CellSpace.surfaceHeight(shape, volume)` deterministically inverts the monotonic profile with integer binary search. It is a neutral geometry helper; Water is only the first consumer.

## Physical face openings

`CellFace` names the six objective faces of one discrete cell. It is not a movement direction enum and carries no traversal permission.

`Shape.boundaryOpeningFloor(face)` reports the lowest local elevation at which the Shape's free space connects through that physical face, or:

```text
CellSpace.CLOSED
```

when the face exposes no connection.

The default Shape implementation is conservative and returns `CLOSED`: spare volume alone does not prove that it connects to another cell.

Current empty-space semantics are:

```text
horizontal faces  opening floor = 0
bottom face       opening floor = 0
top face          opening floor = full cell height
```

Current `RampShape` semantics are:

```text
low face          opening floor = 0
perpendicular side faces = 0
high face         CLOSED
bottom face       CLOSED
top face          full cell height
```

This is intentionally a first-order boundary model. It is enough for the first finite Water flow solver while remaining objective and reusable. A future Shape with an arch, tunnel, multiple separated holes or another important opening that cannot be represented by one lower sill must drive a richer **neutral physical boundary profile**. The correct extension is not an `instanceof` branch in Water and not reuse of navigation ports.

Navigation `transitionPorts()` remain categorically separate from these physical openings.

## Current supported-position law

Current production `FullShape` and cardinal `RampShape` use one supported standing position:

```text
S = (0, 0, 1) relative to the terrain anchor
```

Departures are offered only from the Shape's own supported standing position. For an edge direction `d`, the Shape supporting the destination confirms the arrival when queried from:

```text
relative source = S - d
```

This creates two independent owners for every external edge:

```text
source-support Shape       departure
destination-support Shape  arrival
```

A departure cannot create a connection to nowhere, and an arrival cannot invent a source connection by itself.

This one-supported-position model is **current**, not eternal. A real future Shape requiring multiple standing positions or another anchor relationship must drive a coordinated revision of Shape roles, Navigation read-envelope derivation and TransitionCost support lookup rather than local exceptions.

## Transition ports and composition

`transitionPorts` contains independent departure and arrival masks. `transitionBlocks` contributes geometric obstruction.

The generic resolver accumulates all local contributions and applies:

```text
resolved = departures & arrivals & ~blocks
```

Blocks are independent of permission and apply last. `FullShape` and `RampShape` share the current solid-cell blocking semantics so occupied terrain volume does not become an ordinary standing position.

Because contributions are accumulated before resolution, topology does not depend on concrete Shape type or processing order.

## Traversal factors use the same role law

`ShapeTraversalFactor.NONE = 0` means the Shape does not own the requested role. `NEUTRAL = 1000` means the owned role contributes no additional multiplier.

Default factor methods derive role ownership from `transitionPorts`: an unowned departure/arrival returns `NONE`; an owned role is neutral unless the Shape deliberately overrides it.

A Shape-specific intrinsic factor must preserve the same rules:

- no departure factor without a departure role;
- no arrival factor without an arrival role;
- no neighbor/world queries;
- no foreign concrete Shape inspection.

Current `FullShape` and cardinal `RampShape` use neutral factors. Ramp elevation already changes the discrete direction and therefore the grid-length term; no arbitrary extra uphill/downhill multiplier exists today.

Actor-specific preferences such as wheels versus stairs are not universal Shape geometry and remain a separate future capability interaction.

## Locality implied by the role model

For immediate direction `d` and supported position `S`, arrival ownership requires:

```text
relative source = S - d
```

Under the current model this means Shape-relative arrival queries can reach Z `0..2`. Combined with local solid blocking and the departure owner directly below the standing source, Navigation needs the current generic Shape-anchor window:

```text
offset X: [-1, 1]
offset Y: [-1, 1]
offset Z: [-2, 1]
```

This is derived from generic roles, not a hard-coded Ramp exception. Structural edges remain immediate-neighbor transitions.

TransitionCost does not repeat the full Navigation scan after an edge is known valid. Under the current single-supported-position model, support anchors are directly addressable as:

```text
source support      = source standing XYZ - (0,0,1)
destination support = destination standing XYZ - (0,0,1)
```

## Current concrete Shapes

### FullShape

`FullShape.INSTANCE` represents the default full solid terrain cell. It occupies the whole anchor-cell volume, exposes no free physical cell space, supports the standing position above the cell, participates in ordinary horizontal topology and compatible local vertical/ramp connections through the generic traversal role law, and blocks traversal through its solid volume.

### RampShape

`RampShape` has four cardinal singleton orientations. `riseX()` / `riseY()` expose the objective cardinal direction in which it rises. The same objective vector drives its traversal topology, free-space wedge orientation and specialized presentation binding without coupling those consumers to one another.

A ramp occupies approximately half of its anchor-cell volume. Its free space is the complementary upper wedge. It remains terrain with Shape geometry; it is not an empty navigation node.

## Generic-consumer rule

Navigation, TransitionCost, Water and generic presentation renderers do not branch on `RampShape`, `FullShape` or future concrete Shape classes.

In simulation, generic behavior comes from the `Shape` / `CellSpace` contracts. In presentation, concrete visual/debug knowledge is localized to typed `ShapePresentation<S>` bindings registered by the presentation composition root.

If a future Shape fits the current contract, existing generic consumers must not need modification simply to recognize its concrete class.

## Diagnostics and tests

Geometry tests cover concrete Shape ports/blocks, solid volume, free-volume profiles, physical face-opening floors, role algebra, traversal-factor ownership, terrain lifecycle interaction and hardening around supported positions. Navigation, TransitionCost and Water integration tests verify that the independent contracts are consumed consistently.

See [Shape Transition Algebra decision](../decisions/002-shape-transition-algebra.md), [Typed Presentation Bindings decision](../decisions/004-typed-presentation-bindings.md), [Water Foundation note](../notes/water-foundation.md) and [Adding a Shape](../guides/adding-a-shape.md).

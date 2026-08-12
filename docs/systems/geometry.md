# Geometry and Shape

## Purpose

Describe local structural geometry over existing terrain without coupling generic consumers to concrete Shape classes.

## Ownership

Geometry is separate from terrain material identity. Presence of terrain with no override resolves to `FullShape.INSTANCE`; sparse geometry state stores only non-default Shape overrides.

A terrain coordinate is the Shape **anchor**. Under the current model, the ordinary standing/navigation position supported by that terrain is one Z above the anchor.

## Shape contract

`Shape` is an open declarative local contract. Its public responsibilities are:

```text
transitionPorts(relative source)
transitionBlocks(relative source)
departureTraversalFactor(relative source, direction)
arrivalTraversalFactor(relative source, direction)
```

The relative source is:

```text
source standing XYZ - Shape terrain anchor XYZ
```

and the direction is one immediate transition:

```text
destination XYZ - source XYZ
```

with each delta in `[-1,1]` and not all zero.

A Shape receives no absolute World, neighbor lookup, Navigation, ObjectId or pathfinder. The same Shape value can therefore be reused at any translation.

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

`FullShape.INSTANCE` represents the default full solid terrain cell. It supports the standing position above the cell, participates in ordinary horizontal topology and compatible local vertical/ramp connections through the generic role law, and blocks traversal through its solid volume.

### RampShape

`RampShape` has four cardinal singleton orientations. `riseX()` / `riseY()` expose the objective cardinal direction in which it rises. The same vector drives its topology and its specialized presentation binding.

A ramp remains terrain with Shape geometry; it is not an empty navigation node. Its departure/arrival contributions create a local lower/ramp/upper connection only when neighboring support Shapes independently provide the matching roles.

## Generic-consumer rule

Navigation, TransitionCost and generic presentation renderers do not branch on `RampShape`, `FullShape` or future concrete Shape classes.

In simulation, generic behavior comes from the `Shape` contract. In presentation, concrete visual/debug knowledge is localized to typed `ShapePresentation<S>` bindings registered by the presentation composition root.

If a future Shape fits the current contract, existing generic consumers must not need modification simply to recognize its concrete class.

## Diagnostics and tests

Geometry tests cover concrete Shape ports/blocks, role algebra, traversal-factor ownership, terrain lifecycle interaction and hardening around supported positions. Navigation and TransitionCost integration tests verify that the same local role law is consumed consistently.

See [Shape Transition Algebra decision](../decisions/002-shape-transition-algebra.md), [Typed Presentation Bindings decision](../decisions/004-typed-presentation-bindings.md) and [Adding a Shape](../guides/adding-a-shape.md).

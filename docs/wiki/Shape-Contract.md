# Shape Contract

`Shape` is the local declarative geometry contract used by structural Navigation and by actor-independent traversal-cost calculation. It describes how one terrain geometry contributes possible transitions and intrinsic directed traversal characteristics around its anchor without querying the world or knowing its neighbors.

## Public API

```java
public interface Shape {
    long transitionPorts(
            int relativeX,
            int relativeY,
            int relativeZ);

    default int transitionBlocks(
            int relativeX,
            int relativeY,
            int relativeZ) {
        return TransitionMask.NONE;
    }

    default int departureTraversalFactor(
            int relativeX,
            int relativeY,
            int relativeZ,
            int directionX,
            int directionY,
            int directionZ) {
        ...
    }

    default int arrivalTraversalFactor(
            int relativeX,
            int relativeY,
            int relativeZ,
            int directionX,
            int directionY,
            int directionZ) {
        ...
    }
}
```

The relative-position arguments describe the Navigation source position relative to the Shape terrain anchor:

```text
relative source = source XYZ - shape anchor XYZ
```

The direction arguments identify one immediate directed transition from that source:

```text
direction = destination XYZ - source XYZ
```

with:

```text
dx, dy, dz ∈ [-1, 1]
not (0,0,0)
```

A Shape never receives absolute world state. The same Shape instance can therefore be reused at any translation in the world.

## Terrain anchor

The anchor is the XYZ coordinate containing terrain whose geometry the Shape describes.

For a normal solid terrain cell:

```text
anchor        = terrain cell
standing pos  = anchor + (0,0,1)
```

The object occupies the standing position above the terrain body. It does not occupy the terrain coordinate itself.

This distinction is especially important for ramps. A ramp is still a terrain cell with a special upper surface; it is not an empty navigation node with geometry attached around it.

## Current structural Shape model

Current production Shapes follow one strong convention:

```text
departures are exposed only from the Shape's own standing position
arrivals confirm transitions whose destination is that standing position
```

For the current model, the standing position is:

```text
S = (0,0,1) relative to the Shape anchor
```

A departure contribution is therefore meaningful only at `rel = S`.

For a movement direction `d`, an arrival contribution for a Shape appears at the source relative position:

```text
rel = S - d
```

Equivalently, an arrival bit exposed at relative source `rel` can only be the direction:

```text
d = S - rel
```

That gives each external edge two independent owners: the Shape supporting the source position can offer a departure, while the Shape supporting the destination position can confirm the arrival.

## Why roles must remain independent

Consider a lower Full surface connected to a rising Ramp.

```text
Full A  ↗  Ramp B
```

For `A -> B`:

```text
Full A  supplies departure
Ramp B  supplies arrival
```

If the Ramp is missing, the departure alone cannot create the edge.

For `B -> A`:

```text
Ramp B  supplies departure
Full A  supplies arrival
```

If the lower Full is missing, the Ramp cannot create a descent into empty space by itself.

This is the key protection against ramp-to-nowhere and other phantom topology.

The same ownership rule now also applies to traversal-cost factors. Source geometry contributes only its departure factor, while destination geometry contributes only its arrival factor. Neither Shape computes the other Shape's price.

## Shape does not query neighbors

The following designs are forbidden by the current contract:

```java
shape.hasNeighbor(...)
shape.findWorld(...)
shape.navigation(...)
shape instanceof SomeOtherShape
```

A Shape knows only its own local geometry. The generic Navigation resolver obtains topology contributions from all Shapes in the necessary read window. `TransitionCostCalculator` later obtains only the source-support and destination-support Shape contributions for an edge that Navigation has already accepted.

This keeps Shape behavior deterministic, reusable, and independent of world storage.

## Blocks

`transitionBlocks` declares that a transition direction is geometrically forbidden because a solid body obstructs it.

Blocking is independent of departure and arrival permission. Resolution always applies blocks last:

```text
resolved = departures & arrivals & ~blocks
```

`FullShape` and `RampShape` share `SolidCellBlocking` for ordinary solid-cell volume semantics.

A terrain coordinate occupied by a solid Shape is not an ordinary standing/navigation position. Attempts to enter the solid body from neighboring sources are blocked.

## Traversal factors

A Shape can additionally contribute an intrinsic geometry multiplier to the actor-independent traversal price of a directed edge.

The current fixed-point scale is:

```text
ShapeTraversalFactor.NONE    = 0
ShapeTraversalFactor.NEUTRAL = 1000
```

A positive factor scales only the local contribution owned by that Shape. Conceptually:

```text
source local contribution
    = source surface cost
      * source departure factor

destination local contribution
    = destination surface cost
      * destination arrival factor
```

The complete transition formula is documented in [Movement System](Movement-System.md).

### Default factor behavior

The default methods are derived directly from the Shape's own `transitionPorts`:

```text
if this Shape exposes the requested departure role:
    departureTraversalFactor = NEUTRAL
else:
    departureTraversalFactor = NONE

if this Shape exposes the requested arrival role:
    arrivalTraversalFactor = NEUTRAL
else:
    arrivalTraversalFactor = NONE
```

Therefore a Shape cannot accidentally contribute a neutral traversal factor to a role that its topology does not own unless its implementation explicitly overrides the contract incorrectly.

Production role-contract tests verify that current Shapes keep topology and traversal ownership aligned.

### Overriding a factor

A Shape may override a traversal factor when intrinsic geometry has a real, actor-independent traversal effect that is not already represented by grid direction length.

An override must preserve the same role law:

```text
no departure factor where the Shape has no departure port
no arrival factor where the Shape has no arrival port
no neighbor/world lookup
no concrete foreign Shape inspection
```

A new Shape should normally delegate or reproduce the same ownership test and change only the positive factor value for the roles it actually owns.

### Current `FullShape` and `RampShape`

Current production `FullShape` and cardinal `RampShape` use the neutral default traversal factor for their owned roles.

This is deliberate. Ramp topology already changes the actual discrete transition direction, including elevation changes, and `GridTransitionLength` accounts for one-axis, two-axis and three-axis displacement. EvoForge does not currently invent an additional arbitrary uphill/downhill effort multiplier.

Future intrinsic Shape penalties may be added locally when justified. Actor-specific differences such as wheels versus stairs are not universal Shape geometry and remain a separate future capability interaction.

## Traversal role coordinates

For a valid edge `A -> B` with direction `d`, the traversal calculator asks the same owners used by the topology model.

Source support Shape:

```text
relative source = S = (0,0,1)
role            = departure
direction       = d
```

Destination support Shape:

```text
relative source = S - d
role            = arrival
direction       = d
```

That is exactly the existing departure/arrival geometry relationship. The cost model does not introduce a second coordinate convention.

This is important for ramps because a Shape can expose directed roles from different relative source positions while remaining fully local to its own terrain anchor.

## Current locality implied by Shape roles

Structural movement directions remain immediate-neighbor directions:

```text
dx, dy, dz ∈ [-1,1]
not (0,0,0)
```

For arrivals:

```text
rel = (0,0,1) - d
```

so the current Shape model can require:

```text
rel.x ∈ [-1,1]
rel.y ∈ [-1,1]
rel.z ∈ [0,2]
```

Because Navigation calls Shape with `rel = -offset`, this contributes to a resolver read range of:

```text
offset.x ∈ [-1,1]
offset.y ∈ [-1,1]
offset.z ∈ [-2,0]
```

Blocks still need the local `[-1,1]^3` neighborhood, and departures need the Shape directly below the source. Combining all requirements yields the current generic Navigation read window:

```text
offset.x ∈ [-1,1]
offset.y ∈ [-1,1]
offset.z ∈ [-2,1]
```

The asymmetric Z window is therefore a consequence of the current Shape contract, not a special Ramp check.

Traversal-cost calculation does not repeat this 36-cell scan. Once Navigation has confirmed one edge, the current single-standing-position model lets the calculator address the source and destination support anchors directly as:

```text
source support      = source standing position - (0,0,1)
destination support = destination standing position - (0,0,1)
```

## This convention is explicit, not eternal

The current production model assumes one standing position at `anchor + (0,0,1)`. That assumption is powerful and should be tested, but it is not a claim that every imaginable future geometry must have exactly one standing position.

If a real future Shape requires multiple supported positions or a different anchor relationship, the Shape contract, Navigation resolver read-window derivation, and traversal-cost support-owner lookup must be revised together. The system should not quietly add exceptions around an obsolete invariant.

Until such a consumer exists, current production Shapes are expected to obey the single-standing-position role law.

## Adding a new Shape

A new Shape should normally require:

```text
new Shape implementation
+ topology unit tests
+ integration tests with neighboring generic Shapes
+ role-contract tests
+ traversal-factor tests when non-neutral
+ solid-volume tests when applicable
```

It should not require changes to `NavigationSystem` or `TransitionCostCalculator` that inspect its concrete type.

See [Adding a Shape](Adding-a-Shape.md), [Transition Algebra](Transition-Algebra.md), and [Movement System](Movement-System.md).

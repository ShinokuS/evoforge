# Shape Contract

`Shape` is the local declarative geometry contract used by structural Navigation. It describes how one terrain geometry contributes possible transitions around its anchor without querying the world or knowing its neighbors.

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
}
```

The arguments describe the Navigation source position relative to the Shape terrain anchor:

```text
relative source = source XYZ - shape anchor XYZ
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

## Shape does not query neighbors

The following designs are forbidden by the current contract:

```java
shape.hasNeighbor(...)
shape.findWorld(...)
shape.navigation(...)
shape instanceof SomeOtherShape
```

A Shape knows only its own local geometry. The generic Navigation resolver is responsible for obtaining contributions from all Shapes in the necessary read window and combining them.

This keeps Shape behavior deterministic, reusable, and independent of world storage.

## Blocks

`transitionBlocks` declares that a transition direction is geometrically forbidden because a solid body obstructs it.

Blocking is independent of departure and arrival permission. Resolution always applies blocks last:

```text
resolved = departures & arrivals & ~blocks
```

`FullShape` and `RampShape` share `SolidCellBlocking` for ordinary solid-cell volume semantics.

A terrain coordinate occupied by a solid Shape is not an ordinary standing/navigation position. Attempts to enter the solid body from neighboring sources are blocked.

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

## This convention is explicit, not eternal

The current production model assumes one standing position at `anchor + (0,0,1)`. That assumption is powerful and should be tested, but it is not a claim that every imaginable future geometry must have exactly one standing position.

If a real future Shape requires multiple supported positions or a different anchor relationship, the Shape contract and resolver read-window derivation must be revised together. The system should not quietly add exceptions around an obsolete invariant.

Until such a consumer exists, current production Shapes are expected to obey the single-standing-position role law.

## Adding a new Shape

A new Shape should normally require:

```text
new Shape implementation
+ topology unit tests
+ integration tests with neighboring generic Shapes
+ role-contract tests
+ solid-volume tests when applicable
```

It should not require changes to `NavigationSystem` that inspect its concrete type.

See [Adding a Shape](Adding-a-Shape.md) and [Transition Algebra](Transition-Algebra.md).

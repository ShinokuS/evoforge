# Geometry System

Geometry is a mechanic layered over terrain presence. It owns local Shape overrides and exposes the `Shape` that represents terrain geometry at a coordinate.

## Why geometry is separate from terrain

Terrain answers:

> What landscape content exists at this XYZ?

Geometry answers:

> What local structural geometry does the present terrain expose?

These are different semantics. Granite, soil, wood, and metal can all be full blocks. The same terrain material may use a ramp or another Shape through a geometry override.

Separating them avoids encoding structural topology into material definitions or `TerrainSystem` storage.

## Public read contract

```java
public interface GeometryLookup {
    Shape find(int x, int y, int z);
}
```

Current semantics:

```text
terrain absent
    -> null

terrain present, no custom override
    -> FullShape.INSTANCE

terrain present, custom override
    -> custom Shape
```

Consumers therefore never need to ask Terrain separately merely to know whether geometry exists.

## `GeometrySystem`

`GeometrySystem` depends only on `TerrainLookup` and owns `GeometryState` for sparse non-default overrides.

Its important responsibility is resolving the three cases above while keeping default Full geometry implicit.

## `GeometryState`

Only non-default Shape overrides are stored.

This means a world filled with ordinary Full terrain does not need one explicit Shape reference per terrain cell in the geometry layer.

Conceptually:

```text
Terrain present + no GeometryState entry
    => FullShape.INSTANCE
```

A custom `RampShape` is stored only for coordinates where the default is replaced.

## Default geometry is semantic

`FullShape.INSTANCE` as the default for present terrain is part of current geometry semantics, not merely a cache optimization. A present terrain cell is a solid Full block unless its geometry is overridden.

If future terrain types need definition-driven default Shapes, that will require an explicit contract change rather than quietly overloading sparse override storage.

## Shape is context-free

Geometry returns a `Shape`, but the Shape itself does not receive `TerrainLookup`, `GeometryLookup`, World context, neighboring Shapes or moving-object identity.

Navigation evaluates topology with source coordinates relative to the Shape anchor. TransitionCost later asks the source- and destination-support Shapes for their own local directed traversal factors after Navigation has already established the edge.

```text
Geometry chooses the Shape instance
Shape declares local topology + intrinsic traversal geometry
Navigation composes topology
TransitionCost combines local Shape factors with landscape surface cost
```

This division prevents Shape implementations from performing hidden world scans or central code from learning concrete Shape types.

## Shared immutable Shapes

Current production Shapes are immutable shared instances:

```text
FullShape.INSTANCE
RampShape.POSITIVE_X
RampShape.NEGATIVE_X
RampShape.POSITIVE_Y
RampShape.NEGATIVE_Y
```

Because Shape behavior depends only on relative coordinates and immutable orientation, the same instance can represent any number of terrain anchors.

## Shape traversal factors

The Shape contract now contains actor-independent directed traversal characteristics in addition to topology:

```text
departureTraversalFactor(...)
arrivalTraversalFactor(...)
```

They use the same departure/arrival ownership and relative-coordinate law as `transitionPorts`.

Current fixed-point values are:

```text
ShapeTraversalFactor.NONE    = 0
ShapeTraversalFactor.NEUTRAL = 1000
```

The default implementation returns `NEUTRAL` only for a transition role the Shape's own ports actually expose; otherwise it returns `NONE`.

Current `FullShape` and cardinal `RampShape` therefore require no movement-specific switch and use neutral factors for all owned roles. A future Shape with a real intrinsic geometry penalty may override only its local factor without changing `NavigationSystem` or `TransitionCostCalculator`.

Actor-specific policy such as wheels versus stairs is not intrinsic Shape geometry and is intentionally not encoded here.

See [Shape Contract](Shape-Contract.md) and [Movement System](Movement-System.md) for the complete role and cost formula.

## Solid-cell blocking

`SolidCellBlocking` contains the shared obstruction behavior currently used by Full and Ramp terrain bodies. It computes local block masks without needing world context.

The helper exists because multiple real Shapes share solid-volume semantics. It is not a declaration that every future Shape must be a solid cube.

## Geometry to Navigation

Navigation depends on `GeometryLookup`, not `TerrainLookup` and not `GeometrySystem` internals.

```text
TerrainLookup
    ↓
GeometrySystem / GeometryLookup
    ↓
NavigationSystem
```

Navigation can therefore remain unaware of how terrain identity maps to default/custom geometry.

Navigation is also intentionally separate from traversal price: it resolves structural edges, while `TransitionCostCalculator` reads the already-selected source/destination support Shapes only after an edge exists.

## Mutation and lifecycle

Changing a Shape override changes the topology observed by the next Navigation query. There is currently no persistent Navigation cache.

A custom override can be set only where terrain exists according to current system validation.

Terrain lifetime and geometry-override lifetime are coordinated above both low-level owners by `LandscapeSystem` through `LandscapeMutations`:

```text
placeTerrain
    -> successful placement clears stale geometry override
    -> present terrain resolves to default FullShape

replaceTerrain
    -> successful replacement preserves current override

removeTerrain
    -> successful removal clears geometry override
```

This closes the former stale-override lifecycle gap without introducing a reverse `TerrainSystem -> GeometrySystem` dependency.

A non-default Shape therefore belongs to the lifetime of the current terrain cell and does not silently revive after remove/re-place at the same coordinate.

## Extension boundary

A new Shape compatible with the existing contract should require:

```text
new Shape implementation
+ topology tests
+ role-contract tests
+ traversal-factor tests if non-neutral
```

It should require no concrete-type change in `GeometrySystem`, `NavigationSystem` or `TransitionCostCalculator`.

If a future Shape no longer fits the current one-supported-position model, the general Shape contract, Navigation read envelope and TransitionCost support-owner lookup must be revised together rather than patched with a concrete-type exception.

See [Shape Contract](Shape-Contract.md), [Adding a Shape](Adding-a-Shape.md), and [Movement System](Movement-System.md).

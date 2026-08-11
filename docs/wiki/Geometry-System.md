# Geometry System

Geometry is a mechanic layered over terrain presence. It owns local shape overrides and exposes the `Shape` that represents terrain geometry at a coordinate.

## Why geometry is separate from terrain

Terrain answers:

> What landscape content exists at this XYZ?

Geometry answers:

> What local structural geometry does the present terrain expose?

These are different semantics. Granite, soil, wood, and metal can all be full blocks. The same terrain material may later use a ramp or another Shape through a geometry override.

Separating them avoids encoding navigation topology into material definitions or `TerrainSystem` storage.

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

Consumers therefore never need to ask Terrain separately just to know whether geometry exists.

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

A custom RampShape is stored only for coordinates where the default is replaced.

## Default geometry is semantic

`FullShape.INSTANCE` as the default for present terrain is part of current geometry semantics, not merely a cache optimization. A present terrain cell is a solid Full block unless its geometry is overridden.

If future terrain types need definition-driven default Shapes, that will require an explicit contract change rather than quietly overloading sparse override storage.

## Shape is context-free

Geometry returns a `Shape`, but the Shape itself does not receive `TerrainLookup`, `GeometryLookup`, or World context.

Navigation later evaluates the Shape with source coordinates relative to the Shape anchor.

```text
Geometry chooses the Shape instance
Shape declares local topology
Navigation composes many Shapes
```

This division prevents geometry implementations from performing hidden world scans.

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

## Solid-cell blocking

`SolidCellBlocking` contains the shared obstruction behavior currently used by Full and Ramp terrain bodies. It computes local block masks without needing world context.

The helper exists because there are now multiple real Shapes with identical solid-volume semantics. It is not a declaration that every future Shape must be a solid cube.

## Geometry to Navigation

Navigation depends on `GeometryLookup`, not `TerrainLookup` and not `GeometrySystem` internals.

```text
TerrainLookup
    ↓
GeometrySystem / GeometryLookup
    ↓
NavigationSystem
```

Navigation can therefore remain completely unaware of how terrain identity maps to default/custom geometry.

## Mutation

Changing a Shape override changes the topology observed by the next Navigation query. There is currently no persistent Navigation cache.

A custom override can be set only where terrain exists according to current system validation. Removing terrain hides the Shape because `GeometryLookup.find` first observes absence.

## Known override lifecycle gap

The sparse override entry itself can survive terrain removal. If terrain is later re-placed at the same XYZ, the old override can become visible again.

The project intentionally does not solve this by making `TerrainSystem` notify or mutate `GeometrySystem` directly.

A future lifecycle/orchestration layer must define whether remove/re-place should:

```text
clear geometry override
preserve geometry override
restore from persisted landscape state
apply another explicit policy
```

Until that policy exists, the behavior is documented as a known gap.

## Extension boundary

A new Shape compatible with the existing contract should require no concrete-type change in GeometrySystem or NavigationSystem. Geometry stores the `Shape` interface reference; Navigation consumes the interface.

See [Shape Contract](Shape-Contract.md) and [Adding a Shape](Adding-a-Shape.md).

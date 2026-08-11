# Spatial System

The spatial subsystem owns positions of `WorldObject` instances. It does not own terrain, geometry, or every possible coordinate-indexed mechanic.

## Authoritative mapping

The core authoritative fact is:

```text
ObjectId -> (x,y,z)
```

`TransformState` stores that mapping. `SpatialSystem` is the mutation boundary that coordinates transform state with derived spatial indexes.

## Read boundary

`TransformLookup` exposes read-only position access. Consumers that only need positions should not depend on mutable `TransformState` internals.

This keeps future storage replacement possible.

## Reverse spatial indexes

Many queries start with a coordinate and need objects rather than starting with an ObjectId. That is the purpose of `ObjectSpatialIndex` implementations.

The current `CellSpatialIndex` provides exact-cell reverse lookup derived from object positions.

Conceptually:

```text
TransformState
ObjectId -> XYZ

CellSpatialIndex
XYZ -> ObjectId(s)
```

The reverse index is derived state. `TransformState` remains authoritative for object position.

## Mutation through `SpatialSystem`

Position changes must update authoritative state and indexes consistently. Callers should not mutate `TransformState` and `CellSpatialIndex` independently.

This is an example of one system coordinating an authoritative store and its own derived indexes behind a single mutation boundary.

## Terrain is not spatial-object state

Terrain also uses XYZ, but it does not belong in `CellSpatialIndex`.

```text
Objects:   ObjectId -> XYZ
Terrain:   XYZ -> LandscapeDefinitionId
```

Putting terrain into the object index would mix lifetime models and make ordinary terrain cells consume object identity/index space.

## Domain-specific indexes

A future mechanic may need queries such as:

```text
all hungry agents in region
all heat sources near XYZ
all path blockers in chunk
```

Those are not automatically `SpatialSystem` responsibilities. If the query depends on domain semantics beyond position, the mechanic should normally own its specialized derived index and consume Transform/Spatial read boundaries.

## Occupancy is not yet implemented

Object position does not automatically mean Navigation occupancy policy.

Navigation currently describes structural terrain adjacency only. Future Movement/Occupancy will decide whether a particular actor can enter a structurally connected destination that may contain other objects.

Do not make `Shape` or terrain geometry query `CellSpatialIndex` to solve occupancy prematurely.

## Coordinate representation

Spatial APIs use signed integer XYZ. Exact valid world bounds are not fixed yet. Internal packed coordinate keys may be introduced later if world bounds make them safe and profiling shows value, but normal consumers should continue using semantic integer coordinates.

## Scale considerations

The architecture expects more than one hundred thousand positioned objects. Common spatial queries therefore need indexes rather than global scans.

The current exact-cell index is a foundation, not the final answer for every spatial query. Region/chunk/radius indexes should be introduced when their consumers exist.

## Lifecycle

Object existence and object position are independent authoritative domains. A higher-level lifecycle action may need to coordinate:

```text
create object
place object
remove object from space
remove object identity
```

That orchestration should not be encoded as circular direct dependencies between repository and spatial system.

## Testing

Spatial tests validate:

```text
place/move/remove behavior
state/index consistency
invalid or stale ObjectId handling
multiple objects in cells when supported by current contract
reverse index updates
integration with ObjectRepository lifecycle
boundary coordinates
```

These tests protect the ownership rule that the authoritative transform and derived indexes never diverge through public operations.

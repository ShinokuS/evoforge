# Spatial

## Purpose

Own authoritative positions of runtime `WorldObject` instances and maintain indexes derived from those positions.

## Owns

```text
ObjectId -> (x, y, z)
```

`SpatialSystem` is the sole authoritative object-position owner. `CellSpatialIndex` / `CellObjectLookup` are derived indexes used for efficient cell-oriented reads.

## Does not own

Terrain, Water, SoilMoisture, temperature or other environmental state merely because those systems also use XYZ addresses.

Finite runtime containment is likewise not a second Spatial coordinate owner. `SimulationAssembly` rejects setup placement outside configured `WorldBounds`, while shared Geometry closes out-of-bounds physical space for runtime consumers.

## Movement relationship

Timed Movement does not create a second authoritative position. While an action is in flight, Spatial remains at the source. Only successful completion-time revalidation authorizes the final `SpatialSystem.move` to the destination.

Occupancy derives physical `OCCUPIED` state from current object positions and owns only execution reservations; it does not duplicate authoritative XYZ.

## Presentation relationship

The visualizer reads objects by visible cells through `CellObjectLookup`; it does not scan the whole object repository to draw a viewport.

## Deferred

Multi-cell footprints, packed coordinates, chunk-aware indexes and streaming/persistence representations remain separate future decisions. They must preserve the same authoritative position contract.

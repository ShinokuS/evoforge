# Spatial

## Purpose

Own authoritative positions of runtime `WorldObject` instances and maintain indexes derived from those positions.

## Owns

```text
ObjectId → (x, y, z)
```

`SpatialSystem` is the sole authoritative object-position owner. `ObjectSpatialIndex` / cell lookup are derived indexes used for efficient spatial reads.

## Does not own

Terrain, water, temperature or other environmental state merely because those systems also use XYZ addresses.

## Movement relationship

Timed Movement does not create a second authoritative position. While an action is in flight, Spatial remains at the source. Only successful completion-time revalidation authorizes the final `SpatialSystem.move` to the destination.

## Presentation relationship

The visualizer reads objects by visible cells through `CellObjectLookup`; it does not scan the whole object repository to draw a viewport.

## Deferred

Occupancy/reservations are a separate dynamic-availability concern and must not be hidden inside Spatial indexes.

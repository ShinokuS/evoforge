# Spatial Position

## In plain language

Spatial answers one question: **where is each runtime object right now?**

A Cow may be walking toward another cell, a pathfinder may have planned a future route, and Occupancy may have reserved the destination, but until Movement actually commits a step the Cow has one authoritative XYZ position: its current Spatial position.

## Current status

`PositionSystem` is the sole authoritative owner of runtime object positions:

```text
ObjectId -> (x, y, z)
```

Cell-oriented indexes such as `CellPositionIndex` / `CellObjectLookup` are derived read accelerators, not another owner.

## Ownership and boundaries

Spatial owns:

- the current discrete XYZ of runtime objects;
- moving an object's authoritative coordinate;
- the derived object-by-cell index needed for efficient local reads.

Spatial does **not** own:

- Terrain/Geometry;
- Water or retained Soil liquids;
- Movement progress/duration;
- destination reservations;
- path routes;
- agent intentions;
- world-generation elevation.

Those systems may use the same coordinates without sharing ownership.

## Timed movement relationship

A timed move does not create an authoritative fractional coordinate.

```text
Movement starts source -> destination
        ↓
Spatial remains at source
        ↓ time passes
completion-time revalidation succeeds
        ↓
Spatial.move(object, destination)
        ↓
Spatial now reports destination
```

Presentation is free to interpolate a sprite between source and destination, but that interpolation is visual only.

This rule keeps collision/occupancy/navigation queries from having to reason about two simultaneous authoritative positions.

## Occupancy relationship

Occupancy reads current object positions to derive actual `OCCUPIED` state and separately owns execution reservations for destinations.

```text
Spatial position   = where object is
Occupancy claim    = dynamic exclusive use/reservation
```

A reservation never changes Spatial by itself.

## Optional world bounds

`SimulationAssembly` rejects setup placement outside configured `WorldBounds`. During runtime, shared Geometry presents out-of-bounds coordinates as closed physical space, so movement cannot legitimately commit outside the box through ordinary structural rules.

Bounds do not create a second coordinate owner.

## Presentation relationship

The visualizer queries visible cells through `CellObjectLookup` rather than scanning all objects in the repository for every frame. This is a read/performance concern and does not change Spatial authority.

## Invariants

- Each placed object has exactly one authoritative discrete XYZ.
- Timed movement keeps source position until successful commit.
- Derived cell indexes must agree with authoritative object positions.
- Occupancy/pathfinding/presentation do not mutate positions directly.
- Shared XYZ addressing does not imply a universal `WorldCell` owner.

## Current limitations

Spatial currently models one anchor coordinate per object. Multi-cell footprints, continuous physics coordinates, chunk-aware/packed storage, persistence IDs and streamed loaded/unloaded position semantics remain future design problems.

Any future representation must preserve the public one-authoritative-position meaning unless that semantic contract is deliberately revised.

## Code and tests

Primary code lives under:

```text
simulation/.../world/spatial/
```

Movement/Occupancy integration tests are especially important because they prove that in-flight movement, reservations and final Spatial commit remain separate.

## Sources

**Internal EvoForge design.** The discrete one-owner position model is project-specific architecture.

See [Objects](objects.md), [Movement](../traversal/movement.md), [Occupancy](../traversal/occupancy.md), and [Architecture](../../architecture.md).

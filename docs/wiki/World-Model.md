# World Model

The EvoForge World is not a universal container that directly owns every mechanic. It is a composition root for authoritative subsystem owners and narrow read boundaries.

## Current World

The current `World` owns `ObjectRepository`, `ObjectFactory`, and `TerrainSystem`. Its public surface exposes object lookup, object creation, and terrain lookup. Geometry, Navigation, Control, Movement, and later mechanics are composed around these boundaries rather than forced into one giant class.

## Two major domains

```text
WORLD
├── Objects
│   ObjectId -> WorldObject
│   ObjectId -> XYZ
│
└── Landscape
    XYZ -> LandscapeDefinitionId | absence
```

This split is fundamental. Individual runtime entities can have stable identity. Terrain does not need an ObjectId merely because it occupies XYZ coordinates.

## Shared coordinate space

All current world position APIs use signed integer XYZ:

```text
(int x, int y, int z)
```

These are addresses. They do not imply a shared cell owner. A future coordinate may be relevant to terrain, temperature, water, illumination, positioned objects, geometry, and navigation while those concepts remain owned by separate systems.

## Coordinate bounds

Using Java `int` does not promise that every integer coordinate is valid world space. Exact world bounds remain deferred until chunk, region, and world-generation requirements are known.

Local algorithms must avoid accidental arithmetic wrap. Current Navigation tests exercise integer boundaries so implementation arithmetic cannot create false neighbors.

## Objects

Every individual runtime object has stable `ObjectId` identity. Object existence lives in `ObjectRepository`; object position lives in the spatial subsystem. Identity can therefore exist without a position, and position storage does not own object lifetime.

The repository currently uses slot plus generation identity:

```text
ObjectId = [generation:32 bits][slot:32 bits]
```

When a reusable slot is removed, its generation increments. A stale ObjectId therefore cannot silently refer to a later object that reused the same slot.

## Landscape

Terrain is represented as content at coordinates:

```text
XYZ -> LandscapeDefinitionId | absence
```

Absence is semantic absence, not a special `core:air` or `core:open` definition. The current `TerrainSystem` uses a replaceable `TerrainStorage` boundary and `SparseTerrainStorage` implementation. Chunking and packed region storage are deliberately not fixed yet.

## Geometry over terrain

Geometry is derived from terrain presence plus sparse overrides:

```text
terrain absent
    -> GeometryLookup.find(XYZ) == null

terrain present, no override
    -> FullShape.INSTANCE

terrain present, custom override
    -> custom Shape
```

This keeps terrain material identity separate from geometry. Different materials can share the same Shape, and one material can later be represented by different Shapes without changing its landscape definition identity.

## Navigation over geometry

```text
Terrain
    ↓
Geometry
    ↓
Shape contributions
    ↓
Navigation structural edges
```

Navigation does not store a second authoritative terrain map. It asks `GeometryLookup` for relevant Shapes and composes their local topology.

## Mutation direction

Authoritative mutation flows into owners, then derived readers observe the state.

```text
TerrainSystem mutation
    ↓
TerrainLookup changes
    ↓
GeometryLookup reflects presence/absence
    ↓
Navigation query reflects new geometry
```

The current Navigation implementation has no persistent cache, so the next query observes current geometry directly.

## Known lifecycle gap

A custom geometry override can currently survive terrain removal in `GeometryState`. If terrain is later placed again at the same coordinate, that old override may become visible again.

This is intentionally treated as a lifecycle/orchestration problem. It must not be fixed by creating a reverse `TerrainSystem -> GeometrySystem` dependency. The policy will be decided when terrain lifecycle commands have a proper orchestration boundary.

## Loaded versus absent

Current terrain lookup uses `null` for absence. A future chunked world may need to distinguish present terrain, true absence, and not-loaded or not-generated state. That distinction is deferred so the current foundation does not invent chunk semantics before a world-generation consumer exists.

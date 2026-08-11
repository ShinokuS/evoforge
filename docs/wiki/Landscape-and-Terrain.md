# Landscape and Terrain

Landscape represents environmental content addressed by world coordinates. Terrain is the currently implemented base landscape owner.

## Core representation

```text
XYZ -> LandscapeDefinitionId | absence
```

A terrain cell stores landscape definition identity. Absence means no terrain exists at that coordinate.

## Absence is not a definition

EvoForge deliberately does not use a special content definition such as:

```text
core:air
core:empty
core:open
```

for ordinary absence.

This keeps content identity meaningful: if a coordinate has a `LandscapeDefinitionId`, landscape content actually exists there.

A future loaded/unloaded distinction may require a richer read result, but that is different from inventing an “empty terrain material.”

## `TerrainSystem`

`TerrainSystem` is the authoritative mutation owner for terrain content. Consumers read through `TerrainLookup`.

The system delegates storage to a `TerrainStorage` boundary so chunking/packing can change later without changing ordinary terrain consumers.

## `TerrainLookup`

The current lookup returns:

```text
LandscapeDefinitionId   terrain present
null                    terrain absent
```

A convenience `contains` check is derived from `find` rather than representing a second state source.

## `TerrainStorage`

`TerrainStorage` is an implementation boundary, not a domain promise about how terrain must be stored.

The current implementation is `SparseTerrainStorage`, appropriate for the present foundation and tests. Future region/chunk storage can replace it when world-generation/persistence requirements become concrete.

## Landscape definitions

Terrain stores typed `LandscapeDefinitionId` values compiled from composition-driven landscape definitions.

Material identity and geometry are intentionally separate:

```text
LandscapeDefinitionId  -> what terrain content/material this is
Shape                  -> what local geometry/topology it has
```

Two landscape definitions can use the same geometry. A terrain material can later be represented with a custom geometry override without changing its material identity.

## Geometry dependency direction

`GeometrySystem` reads `TerrainLookup`.

```text
TerrainSystem
    ↑ read boundary
GeometrySystem
```

Terrain does not depend on Geometry. This prevents base content storage from accumulating every mechanic layered over terrain.

For present terrain without a geometry override, Geometry returns `FullShape.INSTANCE`. For absent terrain, Geometry returns no Shape.

## Mutation visibility

Because Geometry and Navigation currently have no persistent cache that hides updates, terrain mutation becomes visible through the read chain on the next query:

```text
Terrain mutation
    ↓
TerrainLookup
    ↓
GeometryLookup
    ↓
NavigationLookup
```

Future caches must preserve the same semantic visibility through correct invalidation/revision handling.

## Landscape is not `WorldObject`

Creating one `WorldObject` for every terrain coordinate would impose object identity/lifetime overhead on base world content and pollute object spatial indexes.

Landscape therefore remains a separate domain even though both objects and terrain share XYZ coordinates.

## Future environmental mechanics

Water, temperature, weather, soil moisture, light, contamination, or other environment state should normally get their own specialized owner rather than fields in a universal terrain cell.

The physical storage behind several mechanics may later be co-located for performance, but semantic ownership should remain separate so systems can evolve independently.

## Chunking and regions

No chunk dimensions are currently fixed. Chunk/region concepts will eventually serve purposes such as:

```text
spatial storage
world generation
loading/unloading
persistence boundaries
activation boundaries
cache locality
```

Those concerns need to be designed together rather than choosing a chunk size now solely to optimize one early sparse map.

## Loaded versus absent

Current `null` means absent terrain. A streaming world may later need to distinguish:

```text
PRESENT
ABSENT
UNLOADED / UNKNOWN
```

That future distinction must be introduced deliberately because treating unloaded terrain as true empty space could create incorrect geometry/navigation results.

## Testing

Terrain tests cover placement/removal, lookup semantics, storage behavior, definition ids, and integration into World/Geometry/Navigation. Future region storage must pass the same semantic tests even if the data structure changes completely.

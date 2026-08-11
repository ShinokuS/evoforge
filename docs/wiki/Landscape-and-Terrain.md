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

`TerrainSystem` is the authoritative owner of terrain storage and terrain-specific mutation invariants. Consumers read through `TerrainLookup`.

The system delegates storage to a `TerrainStorage` boundary so chunking/packing can change later without changing ordinary terrain consumers.

Its mutation methods are result-based:

```text
place occupied position -> POSITION_OCCUPIED
replace absent terrain  -> TERRAIN_ABSENT
remove absent terrain   -> TERRAIN_ABSENT
```

Those are ordinary world-state conflicts. Invalid/null definition ids remain programming/configuration errors and throw.

## `LandscapeMutations`

Terrain and Geometry are separate authoritative owners, but the lifetime of one terrain cell has geometry consequences. The public coordinated write capability is `LandscapeMutations`, currently implemented by `LandscapeSystem`.

```text
external Command handler ─┐
world generation ─────────┤
erosion / internal Action ┤
                          v
                 LandscapeMutations
                    /           \
             TerrainSystem   GeometrySystem
```

This makes lifecycle semantics identical for external commands and internal producers without forcing every internal mutation through Command.

Current policy:

```text
placeTerrain
    -> create only when empty
    -> clear stale geometry override
    -> default geometry becomes FullShape

replaceTerrain
    -> change existing terrain definition
    -> preserve geometry override

removeTerrain
    -> remove terrain
    -> clear geometry override
```

A custom Shape therefore belongs to the lifetime of the terrain cell. It does not silently survive remove/re-place at the same XYZ.

## Result handling

Terrain mutation results implement the neutral `OperationResult` contract and expose:

```text
accepted
namespaced ResultCode
```

Examples:

```text
terrain:placed
terrain:position_occupied
terrain:terrain_absent
```

A caller that expects a possible world-state rejection can inspect the typed result. A deterministic internal producer whose own invariant requires success should express that expectation generically:

```java
OperationResults.requireAccepted(
        landscape.placeTerrain(...));
```

It does not need to compare against concrete success constants such as `PLACED`.

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

Terrain does not depend on Geometry. Cross-owner lifecycle coordination is performed above both systems by `LandscapeSystem`, rather than introducing a reverse `TerrainSystem -> GeometrySystem` dependency.

For present terrain without a geometry override, Geometry returns `FullShape.INSTANCE`. For absent terrain, Geometry returns no Shape.

## Mutation visibility

Because Geometry and Navigation currently have no persistent cache that hides updates, landscape mutation becomes visible through the read chain on the next query:

```text
Landscape mutation
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

Terrain/Landscape tests cover structured place/replace/remove results, lookup semantics, storage behavior, definition ids, geometry lifecycle, and integration into World/Geometry/Navigation. Future region storage must pass the same semantic tests even if the data structure changes completely.

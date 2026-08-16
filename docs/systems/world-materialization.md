# World Materialization

## Purpose

Convert durable generated world facts into authoritative runtime domain state without transferring ownership to the generator.

The current first materialization slice covers generated elevation -> Landscape Terrain only.

## Current pipeline

```text
WorldGenesis
    ↓
WorldAtlasGenerator
    ↓
ElevationField
    ↓
WorldTerrainMaterializer
    ├─ TerrainMaterialResolver
    ├─ DefinitionCatalog<LandscapeDefinitionId>
    └─ LandscapeMutations
            ↓
      LandscapeSystem Terrain
```

`WorldTerrainMaterializer` consumes `ElevationField`, not the whole `WorldAtlas`. Drainage and hydrologic climate remain separate facts with separate future/runtime consumers.

## Terrain volume

For each generated XY column:

```text
surfaceZ = ElevationField.elevationAt(x, y)
solid Terrain = [WorldBounds.minZ .. surfaceZ]
open world    = (surfaceZ .. WorldBounds.maxZ]
```

The world floor and surface are inclusive Terrain cells.

This is deliberate under current geometry semantics. A present Terrain cell defaults to full solid geometry; an absent in-bounds cell is open. A surface-only shell would therefore leave an artificial hollow volume immediately under the ground.

The high-resolution `elevationSubunitsAt` value remains an Atlas macro fact. Materialization uses the documented discrete `elevationAt` projection only.

## Material resolution

Material identity is injected through the pure read contract:

```java
LandscapeDefinitionId materialAt(int x, int y, int z)
```

The materializer contains no built-in knowledge of soil, stone, sand or geology.

For the first generated-world vertical slice, composition may deliberately use:

```java
TerrainMaterialResolver.uniform(baseMaterialId)
```

That helper means only “use this already registered Landscape definition for every generated solid cell in this composition.” It is not a balance rule and does not create a canonical EvoForge ground material.

A later geology stage can provide a resolver backed by generated geological facts and vary material by XYZ/depth without changing the Terrain materialization algorithm.

Resolvers are required to be deterministic and invocation-order independent because materialization may query coordinates during both validation and placement.

## Initialization boundary

Generated Terrain materialization requires an empty runtime Terrain.

It does **not**:

- replace existing Terrain;
- merge Atlas with a lived world;
- regenerate a save;
- keep Atlas and Landscape continuously synchronized.

Those operations would have different ownership and persistence semantics and require separate explicit contracts.

Before any cell is written, preflight validates:

- every discrete surface lies inside the source `WorldBounds` vertical range;
- every resolved material is non-null;
- every resolved material exists in the supplied Landscape definition catalog.

After preflight, all writes go through `LandscapeMutations.placeTerrain`. Direct `TerrainStorage` access is intentionally absent, so Terrain surfaces, Geometry behavior and traversal revisions remain owned by Landscape.

## Ownership

Before materialization:

```text
Atlas       owns generated elevation fact
Landscape   owns empty runtime Terrain
```

After materialization:

```text
Atlas       still owns immutable generated/provenance facts
Landscape   owns every concrete mutable runtime Terrain cell
```

Later runtime Terrain changes do not rewrite the original Atlas fact automatically. Atlas is not a shadow copy of lived Terrain.

## Performance boundary

The current implementation performs a deterministic preflight and then a placement pass over generated solid cells. No chunking, parallel writes, packed column storage or direct bulk-storage bypass is introduced before representative generated-world profiling demonstrates a need.

If this becomes a bottleneck, optimization must preserve the same public ownership and materialization semantics.

## Deferred

This slice intentionally does not materialize:

- initial Water;
- drainage channels as carved Terrain;
- Soil retained-liquid state;
- geology/material strata as generated facts;
- plants, actors or objects;
- climate/weather runtime scheduling;
- warmup or viability calibration;
- save/load reconstruction.

Those are later consumers/stages rather than reasons to broaden this bridge prematurely.

See [World Atlas](world-atlas.md), [Geometry and Shape](geometry.md), [Surface Hydrology](hydrology.md), and [Decision 016](../decisions/016-atlas-terrain-materialization.md).

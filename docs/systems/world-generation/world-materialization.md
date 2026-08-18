# World Materialization

## Purpose

Convert durable generated world facts into authoritative runtime domain state without transferring ownership to the generator.

The current materialization boundary covers generated elevation plus a generated semantic material field -> Landscape Terrain.

## Current pipeline

```text
WorldGenesis
    ↓
WorldAtlasGenerator
    ├─ ElevationField
    └─ DrainageField
            ↓
TerrainMaterialGenerator + TerrainPalette
            ↓
TerrainMaterialField (stable semantic keys)
            ↓
TerrainMaterialBindings (content composition)
            ↓
TerrainMaterialResolver (runtime ids)
            ↓
WorldTerrainMaterializer
    ├─ DefinitionCatalog<LandscapeDefinitionId>
    └─ LandscapeMutations
            ↓
      LandscapeSystem Terrain
```

`WorldTerrainMaterializer` still consumes `ElevationField` plus a pure `TerrainMaterialResolver`; it does not know how slope, drainage, soil depth or deposition were generated.

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

The materializer contains no built-in knowledge of soil, stone, sand or geology.

The normal generated-world path now derives materials as stable semantic keys first:

```java
TerrainMaterialKey materialAt(int x, int y, int z)
```

Content composition then provides explicit `TerrainMaterialBindings` from those keys to its already registered runtime `LandscapeDefinitionId` values. `TerrainMaterialResolver.resolved(...)` performs that conversion only at the materialization boundary.

This keeps generated facts independent from runtime registry ordering and keeps material physics in ordinary Landscape definitions.

The lower-level resolver contract remains:

```java
LandscapeDefinitionId materialAt(int x, int y, int z)
```

and specialized compositions may still use:

```java
TerrainMaterialResolver.uniform(baseMaterialId)
```

That helper means only “use this already registered Landscape definition for every generated solid cell in this composition.” It is not a balance rule and does not create a canonical EvoForge ground material.

Resolvers are deterministic and invocation-order independent because materialization may query coordinates during both validation and placement.

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
- every resolved runtime material exists in the supplied Landscape definition catalog.

After preflight, all writes go through `LandscapeMutations.placeTerrain`. Direct `TerrainStorage` access is intentionally absent, so Terrain surfaces, Geometry behavior and traversal revisions remain owned by Landscape.

## Ownership

Before materialization:

```text
Atlas / terrain generation    own immutable generated facts
Landscape                     owns empty runtime Terrain
```

After materialization:

```text
generated facts   remain immutable provenance/initialization facts
Landscape         owns every concrete mutable runtime Terrain cell
```

Later runtime Terrain changes do not rewrite the original generated facts automatically. Generated data is not a shadow copy of lived Terrain.

## Performance boundary

The materializer still performs deterministic preflight and placement passes over generated solid cells. The current terrain-material generator stores only compact per-column profile depths instead of a full 3D material array.

No chunking, parallel writes, packed bulk mutation or direct storage bypass is introduced before profiling demonstrates a need. Optimization must preserve the same ownership and materialization semantics.

## Deferred

This boundary still does not materialize:

- initial Water;
- drainage channels as carved Terrain;
- Soil retained-liquid state;
- caves/open underground volume;
- plants, actors or objects;
- save/load reconstruction.

Multiple geological rock families and shoreline-specific sediment are generation follow-ups, not reasons to broaden runtime ownership.

See [Terrain Generation](terrain-generation.md), [World Atlas](world-atlas.md), [Geometry and Shape](geometry.md), [Surface Hydrology](hydrology.md), [Decision 016](../decisions/016-atlas-terrain-materialization.md), and [Decision 020](../decisions/020-terrain-palettes-hide-generated-complexity.md).

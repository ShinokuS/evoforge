# World Materialization

## In plain language

World materialization turns generated descriptions into the **actual solid Terrain owned by the running simulation**.

Generation can say:

```text
surface at (x,y) is z=7
surface material = topsoil
below it = soil
then geology
```

but runtime systems such as Geometry, Navigation, Movement and Water operate on Landscape Terrain. Materialization is the one-way bridge that creates those runtime cells from the prepared facts.

The generator does not become the owner of Terrain. It supplies initial facts; Landscape owns the resulting mutable Terrain.

## Current status

The current materialization path handles:

- discrete solid Terrain volume from `ElevationField`;
- semantic generated material identities resolved to runtime Landscape definitions;
- prepared surface Shape overrides during generated-world bootstrap;
- generated Soil properties through a separate runtime compilation step;
- current compatibility initial Water through bootstrap, not `WorldTerrainMaterializer` itself.

Caves/partial underground void materialization are not yet part of this boundary.

## Core Terrain materialization pipeline

```text
ElevationField
      +
TerrainMaterialField
      ↓ stable TerrainMaterialKey
TerrainMaterialBindings
      ↓
TerrainMaterialResolver
      ↓ runtime LandscapeDefinitionId
WorldTerrainMaterializer
      ↓ via LandscapeMutations.placeTerrain(...)
LandscapeSystem owns Terrain
```

`WorldTerrainMaterializer` knows nothing about the algorithm that produced slope, Soil depth, deposition or geology. It only receives:

```text
ElevationField
TerrainMaterialResolver
Landscape definition catalog
Terrain extent lookup
Landscape mutation capability
```

## Generated solid volume

For each horizontal `(x,y)` column:

```text
surfaceZ = elevation.elevationAt(x,y)
solid runtime Terrain = z from WorldBounds.minZ through surfaceZ, inclusive
open space            = z above surfaceZ through WorldBounds.maxZ
```

This is not a hollow surface shell. Under current Geometry semantics a present Terrain cell is solid by default; filling every cell from the world floor prevents an artificial empty underground immediately below the surface.

The high-precision `elevationSubunitsAt(x,y)` remains a generated morphology fact. The current cell-volume materializer uses the documented discrete `elevationAt(x,y)` projection.

## Two-pass safety model

Materialization first performs a full deterministic **preflight**, then performs writes.

### Preflight

For every generated solid coordinate it verifies:

1. `surfaceZ` lies inside vertical world bounds;
2. material resolver does not return null;
3. every resolved `LandscapeDefinitionId` exists in the supplied definition catalog.

If any cell fails, nothing has been placed yet.

### Placement

After successful preflight, every solid cell is written through:

```text
LandscapeMutations.placeTerrain(x,y,z,material)
```

A rejected placement becomes an exception because the initialization assumptions were already validated and the target was required to be empty.

The materializer never writes `TerrainStorage` directly. This preserves Landscape-owned indexes, extents, Geometry/traversal revisions and any other mutation semantics centralized behind `LandscapeMutations`.

## Empty-target requirement

Before materialization:

```text
terrainExtents.empty() must be true
```

If runtime Terrain already exists, generated materialization fails.

This is deliberate. “Initialize an empty generated world”, “merge generated terrain into a lived world”, “replace existing terrain” and “load a saved world” are different operations with different ownership semantics. The current materializer implements only the first.

## Semantic versus runtime material identity

A generated world must not depend on runtime registry order.

Therefore generation returns semantic keys:

```text
TerrainMaterialKey("core:soil")
TerrainMaterialKey("core:granite")
...
```

The application/runtime composition owns the mapping:

```text
TerrainMaterialKey
        ↓ TerrainMaterialBindings
LandscapeDefinitionId
```

Only the materialization boundary resolves the key to the runtime-local integer identity.

A specialized composition may use a uniform resolver for an already-registered runtime material, but that helper is not a canonical “all generated ground is material X” rule.

## Surface Shapes

Surface Shape materialization is performed by `GeneratedWorldRuntimeBootstrap` after solid Terrain has been placed.

For each horizontal column:

```text
shape = TerrainShapeField.shapeOverrideAt(x,y)
if shape exists:
    surfaceZ = atlas.elevation().elevationAt(x,y)
    assembly.setShape(x,y,surfaceZ,shape)
```

Only the surface cell gets the generated override. Thereafter the Shape is ordinary runtime Geometry state; the prepared `TerrainShapeField` is not a continuing owner.

## Generated Soil properties

Generated spatial Soil hydraulic profiles are not material identity. During bootstrap they are compiled using the physical cell scale and simulation time scale into runtime Soil properties, then handed to the existing Soil subsystem.

This keeps:

```text
material identity
surface geometry
physical Soil profile
```

as separate facts instead of encoding all of them in one material enum/key.

## Initial Water is a different boundary

`WorldTerrainMaterializer` does **not** place initial Water.

Current generated-world bootstrap separately reads `SurfaceHydrologyField` and calls the ordinary initial-Water setup API. This distinction matters because Terrain and Water have different authoritative owners.

The final world-generation plan moves/refines initial Water as Stage 7 after complete dry-world acceptance.

## Performance characteristics

The current materializer performs:

```text
full solid-volume preflight
        +
full solid-volume placement
```

This is intentionally straightforward and ownership-safe.

The upstream `TerrainMaterialField` is compact: it can derive materials from per-column profile data and geology rather than allocating a full 3D generated material array.

Chunked bulk materialization, parallel writes, packed storage or direct storage bypass should be introduced only after representative profiling demonstrates a need and the same observable semantics can be preserved.

## Invariants

- Generated Terrain is materialized only into empty runtime Terrain.
- The complete solid column from world floor through discrete surface is placed.
- Preflight completes before writes begin.
- Every material resolves to an already-registered runtime definition.
- All writes use Landscape's mutation capability.
- Generated stable keys do not depend on runtime ID ordering.
- After materialization, Landscape is the only mutable Terrain owner.
- Later Terrain mutation does not rewrite the original Atlas/prepared facts.
- Water, Soil properties and surface Shape remain separate facts/owners even when initialized in the same bootstrap phase.

## Current limitations

The current boundary does not yet handle:

- Stage 4 cave/open underground volume;
- partial/chunked world streaming;
- merging generation into an existing lived Terrain;
- save/load reconstruction;
- runtime regeneration;
- final Stage 5/7 material and Water semantics beyond existing compatibility infrastructure.

Caves will require an explicit solid/open generated fact or equivalent owning contract; they must not be represented by pretending that “air” is just another rock material.

## Code and tests

Primary implementation:

```text
world/materialization/WorldTerrainMaterializer.java
world/materialization/TerrainMaterialBindings.java
world/materialization/TerrainMaterialResolver.java
world/bootstrap/GeneratedWorldRuntimeBootstrap.java
```

Integration coverage checks empty-target requirements, bounds/material validation, exact cell counts, stable material binding and generated-world bootstrap ownership.

## Sources

**Internal EvoForge design:** the one-way generated-fact → Landscape initialization bridge is project-specific architecture.

See [Terrain Generation](terrain-generation.md), [Generated World Runtime](generated-world-runtime.md), [World Atlas](world-atlas.md), [Landscape](../environment/landscape.md), [ADR-016](../../decisions/016-atlas-terrain-materialization.md), and [ADR-020](../../decisions/020-terrain-palettes-hide-generated-complexity.md).

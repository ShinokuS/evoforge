# Landscape and Terrain

## In plain language

Landscape is the authoritative owner of the world's **solid Terrain cells and their material identity**.

A coordinate can contain Terrain such as topsoil, sand or granite, or it can have no Terrain at all. Terrain material answers “what solid material is here?”; Geometry answers “what shape does that solid occupy?”; liquids answer “what finite fluid is here?”. EvoForge keeps those facts separate even when they share the same XYZ coordinate.

There is no fake `air` material used to represent ordinary empty space.

## Current status

Terrain state is conceptually:

```text
XYZ -> LandscapeDefinitionId | absence
```

`TerrainSystem` owns presence/material identity. `LandscapeMutations` coordinates semantic Terrain mutations that also have Geometry lifecycle consequences.

Current storage is sparse, but sparse storage is an implementation choice rather than public semantics.

## Terrain ownership

### Owns

- whether solid Terrain exists at an XYZ;
- which `LandscapeDefinitionId` that Terrain uses;
- Terrain-specific mutation invariants;
- derived Terrain extents/revision facts maintained from accepted mutations.

### Does not own

- object identity/position;
- Shape geometry override state;
- Navigation/path routes;
- free liquid;
- retained Soil-liquid composition;
- generated Atlas facts after runtime materialization.

Consumers read through narrow Terrain/lookups rather than mutable storage.

## Empty space is absence

Ordinary open space is represented by no Terrain:

```text
terrainAt(x,y,z) = absent
```

not by:

```text
LandscapeDefinitionId("core:air")
```

Why this matters: `air`, `unloaded`, `unknown` and `solid material` are different concepts. A future streaming world may need a richer loaded/unknown state, but that should be designed explicitly rather than turning every empty coordinate into a material cell today.

## Coordinated Landscape mutation

Terrain and Geometry are different owners, but changing the lifetime of one Terrain anchor has Shape consequences. `LandscapeMutations` is the semantic coordinator above both owners:

```text
external command ─┐
generation/bootstrap├─> LandscapeMutations
future erosion ────┘         │
                       ┌──────┴──────┐
                       ↓             ↓
                  Terrain owner   Geometry owner
```

### Place Terrain

```text
empty coordinate -> present Terrain
clear any stale Shape override
present Terrain therefore resolves default FullShape unless a new override is applied
```

### Replace Terrain material

```text
existing Terrain definition changes
current Shape override is preserved
```

Changing material does not automatically flatten a ramp.

### Remove Terrain

```text
Terrain removed
associated Shape override removed
```

A custom Shape belongs to the lifetime of the anchored Terrain cell. Removing/replacing the entire cell and later reusing the coordinate does not resurrect old geometry.

Terrain itself does not depend backwards on Geometry; the cross-owner lifecycle rule lives above both owners.

## Mutation result semantics

Expected current-world conflicts are structured operation results, for example:

- placing where Terrain already exists;
- replacing/removing absent Terrain.

Invalid definition IDs, null dependencies or violated trusted invariants remain exceptional.

A deterministic internal producer whose model requires a mutation to succeed may assert the accepted result, but that assertion is a caller expectation—not a change to the public domain-result semantics.

## Material identity, Geometry and mechanic properties

One Terrain cell may participate in several independent facts:

```text
LandscapeDefinitionId
    what material/content identity this Terrain uses

Shape
    local solid/free geometry and structural traversal roles

SurfaceTraversalCost
    actor-independent intrinsic traversal contribution

SoilProperties
    pore capacity + reference-liquid permeability

SurfaceRetentionDefinitions
    microtopographic free-liquid reserve before horizontal runoff
```

These are not collapsed into one mutable Terrain record.

Two materials can share the same Shape. One material can be used by both full cells and ramps. A material can be porous without defining a new Geometry class.

## Traversal relationship

```text
Terrain material + Shape
       ↓
GeometryLookup
       ↓
NavigationLookup
  structural edge?

Terrain material + Shape
       ↓
TransitionCostLookup
  intrinsic edge price?

Navigation + mover constraints + Occupancy
       ↓
Movement
```

Terrain material never creates an otherwise absent Navigation edge. Actor-specific preferences do not belong in Landscape traversal price.

Accepted Terrain/Shape changes update traversal revision/change facts so suspended Pathfinding can refuse to combine incompatible snapshots and the hierarchy cache can invalidate derived data.

## Hydrology relationship

Hydrology reads supporting Terrain to resolve material-owned Soil/surface-retention capabilities, but mutable liquid state belongs elsewhere:

```text
Terrain identity
    ↓
SoilPropertiesLookup / SurfaceRetentionLookup

SoilLiquidSystem
    retained constituent composition

LiquidSystem
    free-liquid identity + quantity
```

A Terrain cell does not become “wet terrain state” merely because Water is present. Wetness is derived from separate Water/Soil owners.

Liquid-specific transport properties belong to liquid identity. Effective porous uptake combines Terrain permeability with liquid viscosity at runtime rather than storing a material×liquid table in Landscape.

## Generated-world relationship

Generation produces immutable semantic material/elevation/Shape/Soil facts. Runtime bootstrap resolves/materializes them once into ordinary Landscape/Geometry/Soil owners.

After startup:

```text
Landscape = mutable runtime Terrain truth
Atlas/prepared fields = immutable provenance/preparation facts
```

A later Terrain mutation does not rewrite the original Atlas.

See [World Materialization](../world-generation/world-materialization.md).

## Optional finite world bounds

Finite containment is configured by `SimulationAssembly`, not by placing fake wall Terrain around the map.

`WorldGeometryLookup` behaves as:

```text
inside WorldBounds  -> ordinary Landscape Geometry
outside WorldBounds -> closed FullShape geometry
```

Setup Terrain/liquid placement outside configured bounds is rejected.

The outside coordinates are not inserted into `TerrainSystem`. This is physical containment, not streaming/generated-state policy.

## Landscape is not a WorldObject

Terrain cells do not receive one runtime `WorldObject` identity each.

Objects and Terrain share spatial coordinates but have different lifecycle/identity semantics. Liquids, retained Soil constituents and future environmental fields follow the same specialized-owner principle.

Physical storage may someday be co-located for performance without merging semantic ownership.

## Invariants

- `TerrainSystem` is the only mutable owner of Terrain presence/material identity.
- Ordinary open space is absence, not a fake material definition.
- Cross-owner Terrain/Shape lifecycle uses `LandscapeMutations`.
- Place clears stale Shape; replace preserves current Shape; remove clears Shape.
- Material identity and Shape geometry remain independent facts.
- Mutable free/retained liquid never becomes a Terrain field.
- Traversal revision changes only through accepted Landscape/Shape mutations that affect traversal semantics.
- Runtime Landscape does not synchronize lived changes back into generated Atlas facts.
- Finite bounds use shared Geometry closure rather than fake boundary Terrain.

## Current limitations

Not yet defined:

- chunk/region storage dimensions;
- loaded/unloaded/unknown state;
- streaming generation boundaries;
- persistence/save representation;
- runtime erosion/material transformation models;
- cave/open-volume materialization semantics beyond current solid-column worldgen.

A future loaded-state model must not silently treat `UNLOADED/UNKNOWN` as real empty Terrain, because that would corrupt Geometry, Navigation, Movement, Pathfinding and liquids.

## Code and tests

Primary code lives under:

```text
simulation/.../world/landscape/
```

Generated initial Terrain enters through `world/materialization/` and runtime bootstrap.

Tests cover place/replace/remove results, definition identity, Geometry lifecycle, extents/revisions, Navigation/Movement invalidation, finite-world containment and the separation between material capabilities and liquid state.

## Sources

**Internal EvoForge design.** Landscape ownership and cross-owner mutation coordination are project architecture.

See [Definitions](../foundations/definitions.md), [Geometry](../foundations/geometry.md), [Navigation](../traversal/navigation.md), [Liquids](liquids.md), [Soil Hydraulics](soil-hydraulics.md), and [World Materialization](../world-generation/world-materialization.md).

# Landscape and Terrain

## Purpose

Own base environmental terrain independently from runtime objects, Water/Soil state and Shape geometry.

## Core representation

Terrain state is conceptually:

```text
XYZ -> LandscapeDefinitionId | absence
```

A present cell stores material/content identity. Ordinary absence is not represented by a fake definition such as `core:air` or `core:empty`.

A future streaming world may need to distinguish absent from unloaded/unknown. That would be a richer read-state contract, not a reason to turn absence into a material today.

## Terrain ownership

`TerrainSystem` is the authoritative owner of terrain presence/material identity and terrain-specific mutation invariants. Consumers read through terrain lookup capabilities.

Concrete storage is replaceable. The current sparse representation is an implementation choice; future region/chunk storage must preserve the same semantic reads/mutations.

Expected mutation conflicts are structured results, for example placing into an occupied coordinate or replacing/removing absent terrain. Invalid definitions/programming inputs remain exceptional.

## Coordinated landscape mutation

Terrain and Geometry are separate authoritative owners, but one terrain cell lifetime has Geometry consequences. `LandscapeMutations`, coordinated above both owners, owns that semantic operation.

```text
external command handler ─┐
future generation ────────┤
future erosion/process ───┤
                         ↓
                 LandscapeMutations
                  /              \
           Terrain owner     Geometry owner
```

Current lifecycle:

```text
placeTerrain
    empty -> terrain
    clear any stale geometry override
    present terrain resolves to default FullShape

replaceTerrain
    existing definition changes
    preserve current geometry override

removeTerrain
    terrain removed
    associated geometry override removed
```

A custom Shape therefore belongs to the lifetime of the terrain cell. It does not survive remove/re-place merely because the same XYZ is reused.

Terrain itself does not depend on Geometry. Cross-owner lifecycle semantics are coordinated above both owners rather than creating a reverse dependency from the terrain owner into Geometry.

## Result boundary

Landscape mutation results implement the common operation-result floor (`accepted` + namespaced result code). A caller may treat normal world-state conflicts as expected data.

An internal deterministic producer whose invariant requires success can assert acceptance through the generic operation-result helper; this expresses caller expectation without converting ordinary landscape conflicts into exception-based API semantics.

## Read capabilities and traversal revisions

Normal terrain lookup exposes definition identity for present terrain and absence otherwise.

Derived consumers currently use:

- `TerrainExtentLookup` — exact occupied global min/max Z;
- terrain/surface indexes used by presentation/atmosphere;
- traversal revision/change facts used to stale exact Pathfinding work and invalidate derived hierarchy cache when accepted landscape/Shape mutation can change traversal semantics.

These are read/cache facts. Presentation never drives them.

## Material, geometry and mechanic data stay separate

```text
LandscapeDefinitionId
    material/content identity

Shape
    local physical geometry + structural traversal roles

LandscapeTraversalDefinitions
    actor-independent traversal price for the material

SoilHydrologyDefinitions
    absorbent-terrain moisture capacity/transfer facts

SurfaceWaterStorageDefinitions
    free-Water runoff retention for a supporting material
```

Two materials may use identical Geometry. The same material may receive a non-default Shape override without changing material identity.

Traversal cost is definition data resolved from `LandscapeDefinitionId`; it is not duplicated on every terrain cell. It cannot create a missing Navigation edge and does not encode actor-specific affinity.

Soil hydrology and surface-water storage are separate mechanic-specific definition aspects. Terrain identity does not itself become mutable SoilMoisture or Water quantity.

## Relationship to Navigation, Movement and Hydrology

```text
TerrainLookup
    ↓
GeometryLookup
    ↓
NavigationLookup
        structural edge?

TerrainLookup + GeometryLookup
    ↓
TransitionCostLookup
        price of valid edge

Navigation + dynamic mover constraints + Occupancy
    ↓
Movement
```

Hydrology reads Terrain material/Geometry through narrow lookups to resolve absorbency, surface storage and exposed/supporting surfaces. `SoilMoistureSystem` and `WaterSystem` remain the authoritative quantity owners.

Current Geometry/Navigation reads observe accepted landscape mutation on their next query. A sleeping Movement action may therefore discover removed support during completion-time revalidation. Pathfinding uses traversal revision facts so suspended exact search cannot mix topology/cost snapshots from different accepted landscape revisions.

## Optional finite world bounds

Finite runtime containment is configured by `SimulationAssembly`, not stored as fake boundary Terrain.

Inside configured `WorldBounds`, `WorldGeometryLookup` delegates to ordinary landscape Geometry. Outside, it exposes closed `FullShape` geometry. Setup terrain placement outside the box is rejected.

This boundary does not make outside cells part of `TerrainSystem` and does not answer future generated/unloaded/streamed world-state questions.

## Landscape is not WorldObject

Base terrain does not receive one `WorldObject` identity per cell. Objects and Landscape share XYZ addressing, not lifecycle/identity ownership.

Water and SoilMoisture already demonstrate the same rule for environmental state: they have specialized owners rather than fields on a universal mutable terrain cell. Future temperature, light or contamination should follow the same ownership discipline unless a real consumer proves a different contract is needed.

Physical storage may later be co-located for performance without merging semantic ownership.

## Deferred world-storage questions

Chunk/region dimensions, streaming state, generation boundaries and persistence are intentionally not fixed yet. They must be designed together when real world-generation/scale consumers exist.

A future loaded-state model must not silently treat `UNLOADED/UNKNOWN` as true empty terrain, because doing so could corrupt Geometry, Navigation, Pathfinding and Movement semantics.

## Diagnostics and tests

Tests cover place/replace/remove result semantics, typed definition ids, Geometry lifecycle, extents/revisions, traversal invalidation and integration through Geometry/Navigation/Movement. Supporting-terrain removal during a timed action is covered so stale Movement cannot commit through changed Landscape. World-bound integration separately proves that explicit runtime containment does not require fake boundary Terrain.

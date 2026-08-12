# Landscape and Terrain

## Purpose

Own base environmental terrain independently from runtime objects and independently from Shape geometry.

## Core representation

Terrain state is conceptually:

```text
XYZ → LandscapeDefinitionId | absence
```

A present cell stores material/content identity. Ordinary absence is not represented by a fake definition such as `core:air` or `core:empty`.

A future streaming world may need to distinguish absent from unloaded/unknown. That would be a richer read-state contract, not a reason to turn absence into a material today.

## Terrain ownership

`TerrainSystem` is the authoritative owner of terrain presence/material identity and terrain-specific mutation invariants. Consumers read through terrain lookup capabilities.

Concrete storage is replaceable. The current sparse representation is an implementation choice; future region/chunk storage must preserve the same semantic reads/mutations.

Expected mutation conflicts are structured results, for example placing into an occupied coordinate or replacing/removing absent terrain. Invalid definitions/programming inputs remain exceptional.

## Coordinated landscape mutation

Terrain and Geometry are separate authoritative owners, but one terrain cell lifetime has geometry consequences. `LandscapeMutations`, currently coordinated above both owners, owns that semantic operation.

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
    empty → terrain
    clear any stale geometry override
    present terrain therefore resolves to default FullShape

replaceTerrain
    existing terrain definition changes
    preserve current geometry override

removeTerrain
    terrain removed
    associated geometry override removed
```

A custom Shape therefore belongs to the lifetime of the terrain cell. It does not survive remove/re-place merely because the same XYZ is reused.

Terrain itself does not depend on Geometry. Cross-owner lifecycle semantics are coordinated above both owners rather than creating a reverse dependency from the terrain owner into geometry.

## Result boundary

Landscape mutation results implement the common operation-result floor (`accepted` + namespaced result code). A caller may treat normal world-state conflicts as expected data.

An internal deterministic producer whose own invariant requires success can assert acceptance through the generic operation-result helper; this expresses the caller's expectation without changing the landscape operation into an exception-based API.

## Read capabilities

Normal terrain lookup exposes definition identity for present terrain and absence otherwise.

Additional generic read facts currently exist for derived consumers:

- `TerrainExtentLookup` — exact occupied global min/max Z, with no rendering semantics;
- `TerrainRevisionLookup` — monotonic terrain-state revision for safe derived-cache invalidation.

The visualizer consumes these facts but does not influence them.

## Material, geometry and mechanic data stay separate

```text
LandscapeDefinitionId
    material/content identity

Shape
    local geometry / topology / intrinsic geometry factor

LandscapeTraversalDefinitions
    actor-independent SurfaceTraversalCost for the material
```

Two materials may use identical geometry. The same material may receive a non-default Shape override without changing its material identity.

Traversal cost is definition data resolved from `LandscapeDefinitionId`; it is not duplicated on every terrain cell. It cannot create a missing Navigation edge and does not encode actor-specific affinity.

## Relationship to Navigation and Movement

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

Movement
    ↓
MovementRate + timing carry
    ↓
Scheduler / completion revalidation
```

Current Geometry/Navigation reads observe terrain mutation on their next query rather than through a persistent topology cache. A sleeping Movement action may therefore discover removed support during completion-time Navigation revalidation.

Future caches/events may change recomputation timing but must preserve the same semantic truth.

## Landscape is not WorldObject

Base terrain does not receive one `WorldObject` identity per cell. Doing so would impose object lifetime/identity overhead on environmental content and pollute object spatial indexes.

Objects and landscape share XYZ addressing, not ownership.

Likewise, future water, temperature, soil moisture, light or contamination should normally receive specialized semantic owners rather than fields on a universal terrain cell. Physical storage may later be co-located for performance without merging ownership contracts.

## Deferred world-storage questions

Chunk/region dimensions, streaming state, generation boundaries and persistence are intentionally not fixed yet. They must be designed together when real world-generation/scale consumers exist.

A future loaded-state model must not silently treat `UNLOADED/UNKNOWN` as true empty terrain, because doing so could corrupt Geometry, Navigation and Movement semantics.

## Diagnostics and tests

Tests cover place/replace/remove result semantics, lookup/storage behavior, typed definition ids, geometry lifecycle, extents/revisions and integration through Geometry/Navigation/Movement. Supporting-terrain removal during a timed action is also covered so stale movement cannot commit through changed landscape.

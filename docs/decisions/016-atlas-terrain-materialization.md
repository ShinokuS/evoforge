# Decision 016 — Atlas elevation materializes through a one-way Terrain ownership boundary

**Status:** Accepted

## Problem

`ElevationField` is a durable generated world fact: it describes the authored surface height of every XY column. Runtime geometry, traversal, liquids and later actors, however, consume concrete Terrain owned by `LandscapeSystem`.

The first generated-world runtime needs a bridge between those layers without making `WorldAtlasGenerator` write runtime storage, making `LandscapeSystem` know how a world was generated, or inventing a geology/material taxonomy before there is a real geology model.

A surface-only shell is also insufficient for the current solid-cell geometry model. Present Terrain resolves to solid geometry by default, while absent cells are open. Materializing only the surface cell would therefore create an artificial open volume immediately below generated ground.

## Decision

`WorldTerrainMaterializer` is a one-way initialization bridge:

```text
ElevationField
      ↓ discrete elevationAt(x,y)
solid generated column [WorldBounds.minZ .. surfaceZ]
      ↓ TerrainMaterialResolver(x,y,z)
LandscapeDefinitionId
      ↓ LandscapeMutations.placeTerrain(...)
LandscapeSystem-owned runtime Terrain
```

Every XY column is filled from the finite world floor through the Atlas surface cell, inclusive. Cells above the surface remain absent/open inside the finite world.

The materializer accepts only the Atlas fact it consumes (`ElevationField`), rather than the whole `WorldAtlas`. It does not depend on Drainage or HydroClimate facts and does not grow `WorldAtlasGenerator` into a runtime composition object.

`TerrainMaterialResolver` is a pure deterministic lookup. It chooses material identity per generated solid XYZ coordinate but owns no Terrain state. The initial vertical slice may use `TerrainMaterialResolver.uniform(id)` explicitly. That is a composition choice, not a claim that the world has one geological material. A future geology-derived resolver can replace it without changing materialization mechanics.

The target Terrain must be empty. Materialization is initial world construction, not Atlas/Terrain synchronization, replacement or regeneration. Merge/patch semantics are intentionally absent.

Before mutation, the materializer performs a preflight over the source surface and resolved materials. Surface heights must lie inside the vertical `WorldBounds`, every material id must be non-null, and every material id must exist in the supplied immutable definition catalog. Only after successful preflight are cells placed.

Every runtime mutation goes through `LandscapeMutations`; the materializer never receives or writes `TerrainStorage`.

## Ownership after materialization

Atlas remains the immutable authored/generated fact source. Landscape becomes the authoritative owner of the concrete runtime Terrain cells.

Runtime erosion, construction or another future terrain-changing process must mutate Landscape through its own domain rules. Such runtime mutation does not silently rewrite Atlas history or provenance.

## Consequences

- generated elevation can now become real runtime geometry without an ownership leak;
- the discrete surface contract of `ElevationField.elevationAt` has its first production consumer;
- generated ground is physically solid down to the finite world floor;
- future subsurface material variation has a narrow seam without premature geology types;
- Landscape surface indexes, geometry defaults and traversal revisions continue to be maintained by their existing owner;
- materialization has explicit fail-fast semantics instead of partially merging with pre-existing runtime Terrain;
- no chunk/storage representation is promoted into public generation semantics.

## Rejected directions

Writing directly to `TerrainStorage` was rejected because it bypasses Landscape-owned indexes, geometry cleanup and traversal revisions.

Adding materialization to `WorldAtlasGenerator` was rejected because generation authors world-scale facts; it should not own runtime state construction.

Materializing only one surface Terrain cell per XY column was rejected because absent cells below that shell are open under current geometry semantics and would create an artificial hollow world.

Hard-coding `soil`, `stone`, `sand` or another current definition in production materialization was rejected because material identity should eventually come from causal generated facts, not from an arbitrary bridge default.

Replacing or merging existing Terrain was rejected because initial materialization and runtime world editing have different invariants and failure semantics.

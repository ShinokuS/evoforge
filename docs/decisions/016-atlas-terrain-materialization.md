# ADR-016: Atlas facts materialize through a one-way Terrain ownership boundary

- Status: Accepted
- Scope: Generated Terrain materialization
- Decision: Prepared/generated elevation and semantic material facts are validated and copied once into Landscape-owned solid Terrain through `LandscapeMutations`; materialization is initial construction, not synchronization.

## Context

Atlas elevation describes generated surface height, but runtime Geometry/Navigation/liquids need concrete Terrain owned by Landscape. Writing runtime storage from generators would blur ownership. Materializing only the surface shell would also leave artificial open underground volume because absent Terrain is open under current Geometry semantics.

## Decision

`WorldTerrainMaterializer` consumes the exact generated facts it needs. For every XY column it creates solid Terrain from finite `WorldBounds.minZ` through discrete `surfaceZ` inclusive.

Generated material identity is resolved through stable semantic `TerrainMaterialKey`/bindings to runtime `LandscapeDefinitionId`; runtime registry ordering is not generated-world semantics.

Materialization requires an empty target and performs a complete preflight before mutation: generated heights must be in bounds, each material must resolve, and each runtime definition must exist. Accepted writes go only through `LandscapeMutations.placeTerrain(...)`.

After materialization, Landscape is the mutable runtime owner. The Atlas/prepared fields remain immutable provenance and are not synchronized when lived Terrain later changes.

## Why

The boundary reuses all existing Landscape mutation invariants/index/revision behavior while keeping generators independent from runtime storage and preserving a solid current world model.

## Consequences

- Generated worlds use ordinary runtime Terrain/Geometry after startup.
- Solid ground exists through the full generated column, not as a hollow shell.
- Semantic generated keys are stable across runtime registration order.
- Initial construction fails before partial writes when preflight detects invalid input.
- Future geology/material algorithms can replace prepared material fields without changing the runtime ownership bridge.

## Alternatives considered

Direct `TerrainStorage` writes were rejected because they bypass Landscape invariants. Atlas generator writing runtime state was rejected because it merges generation and simulation. Surface-only materialization was rejected as physically hollow. Hard-coded ground material and merge/replace semantics were rejected as separate problems.

## Current implementation

`WorldTerrainMaterializer` preflights and fills generated columns through `LandscapeMutations`. `GeneratedWorldRuntimeBootstrap` then applies prepared surface Shape overrides and generated Soil properties before runtime start. Stage 4 caves will require an explicit generated solid/open-volume model rather than treating “air” as another material.

## Related documentation

- [World Materialization](../systems/world-generation/world-materialization.md)
- [Generated World Runtime](../systems/world-generation/generated-world-runtime.md)
- [Landscape and Terrain](../systems/environment/landscape.md)
- [Terrain Generation](../systems/world-generation/terrain-generation.md)

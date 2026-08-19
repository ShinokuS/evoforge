# ADR-012: Drainage preserves closed-world basins

- Status: Accepted
- Scope: Generated analytical drainage topology
- Decision: Drainage derives deterministic in-bounds topology from precise elevation and treats finite world edges as closed, preserving internal basins instead of forcing every column to drain off-map.

## Context

Common raster preprocessing often treats DEM edges as external outlets and fills depressions until every cell drains away. EvoForge finite `WorldBounds` instead present closed physical space outside the world. Integer materialized Terrain elevation also creates artificial flats that are not present in the precise Atlas elevation.

## Decision

`DrainageField` is derived from precise `ElevationField.elevationSubunitsAt(...)`.

The current analytical stage:

1. chooses the steepest strictly lower in-bounds neighbor when one exists;
2. routes exact-elevation flat components that have lower outlets toward those outlets deterministically;
3. gives an outlet-less exact flat one deterministic in-bounds terminal representative;
4. leaves a non-flat local minimum terminal;
5. accumulates contributing area over the resulting acyclic topology;
6. records the terminal basin representative reached by each column.

Out-of-bounds neighbors are never candidates. Exact-flat routing is topological bookkeeping for accumulation/watershed reasoning, not a claim that physically level runtime Water has a preferred velocity.

## Why

Closed edges agree with world containment, precise elevation avoids quantization artifacts, and preserved basins are necessary inputs for later real lake/river reasoning.

## Consequences

- Downstream targets are always in bounds or absent at a terminal.
- Internal depressions/basins remain represented instead of being erased.
- Contributing area and basin identity are available without creating Water.
- Changing finite bounds can legitimately change nearby/upstream topology.
- Runtime liquid behavior remains separate from analytical generated drainage.

## Alternatives considered

Implicit edge outlets were rejected as contradictory to finite-world containment. Blind depression filling was rejected because it would erase legitimate basins. Integer Terrain Z was rejected as hydrologic input because materialization quantization would become macro physics.

## Current implementation

The current `DrainageGenerationStage` remains useful deterministic analytical topology and is consumed by Atlas/material/Soil preparation. Stage 0 explicitly classifies it as **provisional for the future carved hydrography model**: Stage 2 may replace/narrow/extend the algorithm behind the typed Drainage/Hydrography seams, but it must preserve explicit finite-world/basin semantics unless new evidence changes this decision.

## Related documentation

- [World Atlas](../systems/world-generation/world-atlas.md)
- [World Generation](../systems/world-generation/overview.md)
- [Terrain Generation](../systems/world-generation/terrain-generation.md)
- [Project Context](../project-context.md)

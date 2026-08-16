# Decision 012 — Drainage preserves closed-world basins

**Status:** Accepted

## Problem

Drainage is the first generated fact that turns elevation into causal hydrology. Common raster preprocessing often assumes the edge of a DEM is an external outlet and may fill depressions until every cell drains to an edge. That assumption conflicts with EvoForge's finite `WorldBounds`: outside geometry is closed, and an edge column must not silently become a hole through which water leaves the world.

Integer terrain Z is also too coarse for macro drainage. It creates artificial flats that do not exist in the precise generated elevation retained by World Atlas.

## Decision

World Atlas owns an immutable `DrainageField` derived from `ElevationField` by the narrow `DrainageGenerator` contract.

The current `DrainageGenerationStage` builds a deterministic D8-style local topology from `elevationSubunitsAt(...)`:

1. each column first chooses the steepest strictly lower in-bounds neighbour;
2. exact-elevation flat components that contain lower outlets are routed internally toward those outlets;
3. an exact flat with no lower outlet receives one deterministic in-bounds terminal representative;
4. a non-flat local minimum remains terminal;
5. contributing area is accumulated over the resulting acyclic graph;
6. every column records the terminal basin representative reached by its drainage path.

Neighbours outside `WorldBounds` are never candidates. The world edge is therefore a wall in drainage topology, not an implicit ocean or external sink.

Exact-flat routing is topological, not a claim that a physically level lake has a preferred runtime flow velocity. It exists so a connected flat basin/outlet has one deterministic drainage structure for accumulation and later watershed reasoning. Runtime Water remains governed by the existing liquid simulation after materialization.

Drainage consumes precise Atlas elevation. The integer `elevationAt(...)` materialization view is not hydrologic input.

## Consequences

- downstream targets are always inside `WorldBounds` or absent for a terminal;
- closed depressions and internal basins are retained rather than forcibly drained off-map;
- contributing area gives a direct first-order river/channel potential without creating Water yet;
- terminal coordinates provide stable basin membership without inventing a separate region/chunk identity;
- exact discrete terrain flats do not become false drainage flats when precise elevation contains a gradient;
- drainage depends on the finite world boundary, so changing `WorldBounds` can legitimately change topology near and upstream of that boundary even when overlapping elevation samples are identical;
- the generated topology is immutable Atlas data, while future free Water and retained liquid remain runtime-domain state.

## Rejected directions

Treating every map edge as an external outlet was rejected because it contradicts closed world containment and would make rivers disappear beyond authoritative geometry.

Blind depression filling was rejected because it would erase legitimate closed basins and pre-author lake/outflow behavior that should remain explicit.

Running drainage over integer terrain Z was rejected because materialization quantization would become accidental hydrologic physics.

A universal hydrology/world-generation context was rejected. Drainage requires `ElevationField` and returns `DrainageField`; additional inputs should be added only when a real algorithm requires them.

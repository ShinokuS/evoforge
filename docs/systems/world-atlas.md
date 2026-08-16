# World Atlas

World Atlas owns durable generated facts that are authored from `WorldGenesis` before detailed world materialization. It is not terrain storage and does not simulate runtime mechanics.

## Current composition

The first Atlas contains:

```text
WorldGenesis
ElevationField
```

`WorldAtlas` validates that every layer describes the same `WorldBounds` as its genesis.

## Elevation

`ElevationField` is an immutable read contract over global XY world columns:

```text
elevationAt(x, y) -> surface z
```

Coordinates outside the field bounds are invalid queries. Elevation values remain inside a reserved central vertical band of the world's Z bounds, leaving representational room below and above for later geology/materialization, Water and open space.

The current package-private implementation stores a dense bounded array. Consumers cannot depend on that representation. Tiling, compression or another representation may replace it later behind `ElevationField` if representative profiling justifies the change.

## Elevation generation v1

`ElevationGenerationStage` is the first causal generation stage. It combines three independent deterministic value-noise bands:

- coarse scale: 32 world cells, weight 4;
- medium scale: 16 world cells, weight 2;
- detail scale: 8 world cells, weight 1.

Each band has its own `GenerationPurposeId` under the common `world:elevation` stage. Lattice samples use `GenerationRandom`, so sample order is irrelevant.

The lattice is anchored in global coordinates rather than rebased to `WorldBounds.minX/minY`. With the same genesis seed/revisions and vertical bounds, overlapping XY areas therefore resolve the same elevation facts even when the requested horizontal crop differs.

Representative sample values are frozen in headless tests. Generated fields are also tested for deterministic equality, bounded vertical output and seed sensitivity.

## Stage composition

`WorldAtlasGenerator` is deliberately thin. It invokes the current stages and assembles their immutable outputs. Algorithms belong in the individual generation stages.

Future causal order is expected to grow from evidence along lines such as:

```text
Elevation
  -> Geology
  -> Climate normals
  -> Drainage / watersheds
  -> Soil / ecology potential
```

The exact later layer contracts are not defined by the current slice.

## Materialization boundary

Atlas elevation is a world fact, not a placed Landscape cell. A later materialization slice will translate Atlas facts into detailed Terrain/Soil/Liquid/Object state through the existing domain-owned mutation boundaries. Runtime systems then continue to evolve that materialized state under their normal laws.

World Atlas therefore does not own Water flow, soil moisture, objects, agents or dynamic weather.

## Deferred representation decisions

No chunk dimensions, region semantics, streaming lifecycle or simulation LOD are introduced here. The first Atlas proves generated-fact causality and determinism on a bounded world before those optimizations are considered.

See [Decision 010 — World Atlas owns durable generated facts](../decisions/010-world-atlas-generated-facts.md) and [World Genesis](world-genesis.md).

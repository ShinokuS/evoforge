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

`ElevationField` is an immutable read contract over global XY world columns with two deliberately different views of one generated fact:

```text
elevationSubunitsAt(x, y) -> precise macro elevation
elevationAt(x, y)         -> discrete surface-cell z
```

One world Z cell equals `1_000_000` elevation subunits. The precise value is the durable Atlas fact used by macro algorithms that need gradients, especially drainage. `elevationAt` is the floor-derived cell coordinate intended for later terrain materialization. Negative values therefore use mathematical floor semantics rather than truncation toward zero.

This distinction prevents discrete terrain representation from destroying information needed by world-scale causality. Two neighbouring columns may materialize at the same integer Z while still having a real ordered elevation gradient in Atlas. Drainage must use the precise value rather than treating such columns as an artificial flat.

Coordinates outside the field bounds are invalid queries. Elevation remains inside a reserved central vertical band of the world's Z bounds, leaving representational room below and above for later geology/materialization, Water and open space.

The current package-private implementation stores a dense bounded `long[]` of elevation subunits. Consumers cannot depend on that representation. Tiling, compression or another representation may replace it later behind `ElevationField` if representative profiling justifies the change.

## Elevation generation revisions

`ElevationGenerator` is the typed semantic algorithm contract that authors an `ElevationField` from `WorldGenesis`. `ElevationGenerationStage` currently executes two compatible authored-world revisions:

- `evoforge:worldgen-v1` is the accepted legacy elevation semantics. It preserves the original integer surface height exactly and exposes precise elevation as that whole-cell value multiplied by the subunit scale.
- `evoforge:worldgen-v2` is the current semantics. It preserves the same discrete V1 surface height for identical inputs but retains the deterministic fractional remainder that V1 discarded when mapping normalized noise into world Z.

V2 therefore changes the durable generated fact without rewriting the already-accepted discrete terrain shape. The revision change is intentional: regenerating a historical V1 recipe remains capable of producing its cell-quantized fact rather than silently adopting newer precision.

Both revisions use the same three deterministic value-noise bands:

- coarse scale: 32 world cells, weight 4;
- medium scale: 16 world cells, weight 2;
- detail scale: 8 world cells, weight 1.

Each band has its own `GenerationPurposeId` under the common `world:elevation` stage. Lattice samples use `GenerationRandom`, so sample order is irrelevant. The RNG revision remains `evoforge:rng-v1`; the V2 change is generated-fact interpretation, not a new random algorithm.

The lattice is anchored in global coordinates rather than rebased to `WorldBounds.minX/minY`. With the same seed, generation/RNG revisions and vertical bounds, overlapping XY areas therefore resolve the same precise elevation facts even when the requested horizontal crop differs.

Headless tests freeze the accepted V1 discrete samples, prove that current V2 keeps those same discrete samples, prove V1 remains exactly cell-quantized, and prove V2 distinguishes neighbouring columns that are equal only after integer materialization.

## Algorithm composition

`WorldAtlasGenerator` is deliberately thin. It depends on `ElevationGenerator`, not on the concrete elevation implementation, and assembles the immutable output into `WorldAtlas`. Its default constructor selects `ElevationGenerationStage`; callers that own composition may inject another implementation through the same typed seam.

The precision extension does not invalidate substitute algorithms. `ElevationField.elevationSubunitsAt(...)` has a compatibility default derived from `elevationAt(...)`, so a substitute that only authors discrete heights remains valid and explicitly has cell-level precision until it chooses to provide more.

Substitution does not remove validation. `WorldAtlasGenerator` rejects missing/broken algorithm output and `WorldAtlas` still validates layer bounds against `WorldGenesis`.

Future stages use their own narrow semantic contracts when their real dependencies are known. They do not share one universal mutable generation context. Likewise, future world evaluators receive typed contracts for the concrete question they evaluate rather than one universal evaluator API.

A concrete algorithm may be replaced without changing downstream consumers because consumers read generated fact contracts such as `ElevationField`. If a replacement intentionally changes authored world facts for otherwise identical declared inputs, generation-version compatibility must change explicitly rather than silently reusing the old `GenerationRevision`.

Future causal order grows from actual dependencies. The next direct consumer of precise elevation is drainage; geology, climate and other fields will be added when their own semantic inputs are concrete rather than because of a fixed type hierarchy.

## Materialization boundary

Atlas elevation is a world fact, not a placed Landscape cell. A later materialization slice will translate the discrete surface view plus other Atlas facts into detailed Terrain/Soil/Liquid/Object state through the existing domain-owned mutation boundaries. Runtime systems then continue to evolve that materialized state under their normal laws.

World Atlas therefore does not own Water flow, soil retained liquid, objects, agents or dynamic weather.

## Deferred representation decisions

No chunk dimensions, region semantics, streaming lifecycle or simulation LOD are introduced here. The Atlas proves generated-fact causality and determinism on a bounded world before those optimizations are considered.

See [Decision 010 — World Atlas owns durable generated facts](../decisions/010-world-atlas-generated-facts.md), [Decision 011 — World generation algorithms compose behind typed contracts](../decisions/011-world-generation-algorithm-contracts.md) and [World Genesis](world-genesis.md).

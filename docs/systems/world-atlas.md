# World Atlas

World Atlas owns durable generated facts that are authored from `WorldGenesis` before detailed world materialization. It is not terrain storage and does not simulate runtime mechanics.

## Current composition

The current Atlas contains:

```text
WorldGenesis
ElevationField
DrainageField
```

`WorldAtlas` validates that every layer describes the same `WorldBounds` as its genesis.

## Elevation

`ElevationField` is an immutable read contract over global XY world columns with two deliberately different views of one generated fact:

```text
elevationSubunitsAt(x, y) -> precise macro elevation
elevationAt(x, y)         -> discrete surface-cell z
```

One world Z cell equals `1_000_000` elevation subunits. The precise value is the durable Atlas fact used by macro algorithms that need gradients, especially drainage. `elevationAt` is the floor-derived cell coordinate intended for later terrain materialization. Negative values therefore use mathematical floor semantics rather than truncation toward zero.

This distinction prevents discrete terrain representation from destroying information needed by world-scale causality. Two neighbouring columns may materialize at the same integer Z while still have an ordered elevation gradient in Atlas. Drainage uses the precise value rather than treating such columns as an artificial flat.

Coordinates outside the field bounds are invalid queries. Elevation remains inside a reserved central vertical band of the world's Z bounds, leaving representational room below and above for later geology/materialization, Water and open space.

The current package-private implementation stores a dense bounded `long[]` of elevation subunits. Consumers cannot depend on that representation. Tiling, compression or another representation may replace it later behind `ElevationField` if representative profiling justifies the change.

## Elevation generation revisions

`ElevationGenerator` is the typed semantic algorithm contract that authors an `ElevationField` from `WorldGenesis`. `ElevationGenerationStage` currently executes two compatible authored-world revisions:

- `evoforge:worldgen-v1` is the accepted legacy elevation semantics. It preserves the original integer surface height exactly and exposes precise elevation as that whole-cell value multiplied by the subunit scale.
- `evoforge:worldgen-v2` is the current semantics. It preserves the same discrete V1 surface height for identical inputs but retains the deterministic fractional remainder that V1 discarded when mapping normalized noise into world Z.

V2 therefore changes the durable generated elevation fact without rewriting the already-accepted discrete terrain shape. The revision change is intentional: regenerating a historical V1 recipe remains capable of producing its cell-quantized elevation semantics rather than silently adopting newer precision.

Both revisions use the same three deterministic value-noise bands:

- coarse scale: 32 world cells, weight 4;
- medium scale: 16 world cells, weight 2;
- detail scale: 8 world cells, weight 1.

Each band has its own `GenerationPurposeId` under the common `world:elevation` stage. Lattice samples use `GenerationRandom`, so sample order is irrelevant. The RNG revision remains `evoforge:rng-v1`; the V2 change is generated-fact interpretation, not a new random algorithm.

The lattice is anchored in global coordinates rather than rebased to `WorldBounds.minX/minY`. With the same seed, generation/RNG revisions and vertical bounds, overlapping XY areas therefore resolve the same precise elevation facts even when the requested horizontal crop differs.

Headless tests freeze the accepted V1 discrete samples, prove that current V2 keeps those same discrete samples, prove V1 remains exactly cell-quantized, and prove V2 distinguishes neighbouring columns that are equal only after integer materialization.

## Drainage

`DrainageField` is the immutable macro topology derived from `ElevationField`. For each world column it exposes:

```text
optional in-bounds downstream column
contributing area in source columns
terminal basin representative
```

`DrainageGenerator` is deliberately narrower than the elevation algorithm contract:

```text
generate(ElevationField) -> DrainageField
```

The current `DrainageGenerationStage` first chooses a steepest strictly lower D8 neighbour using precise elevation. Exact-elevation flat components are then resolved deterministically. A flat that touches one or more lower outlets routes internally toward those outlets; a flat with no lower outlet receives one deterministic internal terminal representative. Ordinary local minima are terminal directly.

No neighbour outside `WorldBounds` is ever considered. World edges are therefore closed hydrologic boundaries, not external sinks. Enclosed depressions survive as real internal basins instead of being filled or forced to drain off-map.

Contributing area is accumulated over the resulting acyclic topology. It is a first-order channel/river-potential fact, not Water quantity. Terminal coordinates provide stable basin membership without inventing region or chunk identity.

Unlike elevation sampling, drainage topology is legitimately boundary-dependent: cropping to different `WorldBounds` creates a different closed hydrologic world and may change paths near or upstream of the new boundary.

## Algorithm composition

`WorldAtlasGenerator` is deliberately thin. It composes typed algorithms in causal order:

```text
ElevationGenerator -> ElevationField
                         ↓
DrainageGenerator  -> DrainageField
```

Its default constructor selects `ElevationGenerationStage` and `DrainageGenerationStage`. Callers may inject either typed algorithm. The existing elevation-only constructor remains valid and pairs the supplied elevation algorithm with the default drainage stage.

The precision extension does not invalidate substitute elevation algorithms. `ElevationField.elevationSubunitsAt(...)` has a compatibility default derived from `elevationAt(...)`, so a substitute that only authors discrete heights remains valid and explicitly has cell-level precision until it chooses to provide more.

Substitution does not remove validation. `WorldAtlasGenerator` rejects missing/broken algorithm output and `WorldAtlas` validates every generated layer against `WorldGenesis` bounds.

Future stages use their own narrow semantic contracts when their real dependencies are known. They do not share one universal mutable generation context. Likewise, future world evaluators receive typed contracts for the concrete question they evaluate rather than one universal evaluator API.

## Materialization boundary

Atlas elevation and drainage are world facts, not placed Landscape cells or free Water. A later materialization slice translates Atlas facts into detailed Terrain/Soil/Liquid/Object state through the existing domain-owned mutation boundaries. Runtime systems then continue to evolve that materialized state under their normal laws.

Drainage can guide initial channels, basins and finite Water placement, but it never becomes a second runtime Water solver. World Atlas therefore does not own free-liquid flow, soil retained liquid, objects, agents or dynamic weather.

## Deferred representation decisions

No chunk dimensions, region semantics, streaming lifecycle or simulation LOD are introduced here. The Atlas proves generated-fact causality and determinism on a bounded world before those optimizations are considered.

See [Decision 010 — World Atlas owns durable generated facts](../decisions/010-world-atlas-generated-facts.md), [Decision 011 — World generation algorithms compose behind typed contracts](../decisions/011-world-generation-algorithm-contracts.md), [Decision 012 — Drainage preserves closed-world basins](../decisions/012-closed-world-drainage-topology.md) and [World Genesis](world-genesis.md).

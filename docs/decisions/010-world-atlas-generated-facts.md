# Decision 010 — World Atlas owns durable generated facts

**Status:** Accepted

## Problem

World generation needs facts such as elevation, geology, climate normals and drainage before detailed terrain, Water or objects are materialized. Putting those facts directly into runtime domain storage would collapse generation, representation and simulation ownership into one step. Treating them as disposable generator intermediates would also make persistence and cross-stage causality unclear.

A further representation problem appears immediately with elevation: integer terrain Z is appropriate for cell materialization, but quantizing the generated surface to that same integer before later macro stages destroys real gradients. Drainage would then see large artificial flats and sinks that exist only because presentation/materialization uses cells.

## Decision

`WorldAtlas` is the immutable composition boundary for durable generated world facts that exist before detailed world materialization.

Atlas layers expose semantic read contracts and hide their representation. The first layer is `ElevationField`, which answers surface elevation for each global XY world column. Its current dense bounded representation is package-private and is not a storage promise.

Elevation owns a precise generated value measured in fixed-point elevation subunits (`1 world Z cell = 1_000_000 subunits`) and a floor-derived integer surface-cell view. Macro generation consumers that depend on gradients use the precise value; terrain materialization may use the discrete cell value. The discrete representation therefore does not become accidental macro-world physics.

Generation is causal and staged. `ElevationGenerationStage` authors elevation from `WorldGenesis`; later stages explicitly consume earlier facts when their algorithms require them. `WorldAtlasGenerator` is only a thin orchestration boundary and must delegate domain algorithms to stages rather than accumulate generation logic itself.

Elevation generation remains deterministic multi-scale value noise addressed by the versioned generation RNG. Its lattice is global rather than rebased to the requested XY bounds, so overlapping worlds with the same seed, generation/RNG revisions and vertical bounds produce the same elevation at the same global XY coordinates.

The accepted `evoforge:worldgen-v1` semantics remain representable as cell-quantized elevation. `evoforge:worldgen-v2` is current and preserves the same discrete V1 surface while retaining the deterministic fractional elevation information that V1 discarded. This is a `GenerationRevision` change because the durable authored fact changed; the RNG remains `evoforge:rng-v1` because its sampling contract did not.

The generated elevation fact is not Landscape terrain. Future materialization consumes Atlas facts and commits detailed terrain through the Landscape-owned mutation boundary.

## Consequences

- generated macro/world facts have an explicit owner before runtime materialization;
- drainage can distinguish a real downhill gradient even when neighbouring columns materialize to the same integer Z;
- later geology, climate and drainage can depend on earlier facts without reading detailed terrain as accidental generation input;
- Atlas consumers depend on semantic layer contracts rather than dense arrays;
- storage can later become tiled, compressed or streamed behind the same fact contract when profiling justifies it;
- generation remains deterministic across iteration order and cropping of world XY bounds;
- historical V1 recipe semantics remain explicit instead of being silently rewritten by V2;
- materialization does not become a second generator or a second owner of Atlas facts.

## Rejected directions

Generating terrain blocks directly from the seed was rejected because it skips the durable causal facts needed by later generation stages and future persistence.

Using integer terrain Z as the only elevation fact was rejected because quantization would create artificial macro flats and sinks and make drainage depend on materialization resolution.

Exposing the current dense elevation array was rejected because it would turn a prototype representation into public world semantics.

Biome-first generation was rejected for this foundation: biome will be derived from causal facts such as elevation, climate and soil rather than acting as the unexplained source of those facts.

Chunk and region identity remain deferred. The Atlas layer does not need either concept to be correct or deterministic.

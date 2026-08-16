# Decision 010 — World Atlas owns durable generated facts

**Status:** Accepted

## Problem

World generation needs facts such as elevation, geology, climate normals and drainage before detailed terrain, Water or objects are materialized. Putting those facts directly into runtime domain storage would collapse generation, representation and simulation ownership into one step. Treating them as disposable generator intermediates would also make persistence and cross-stage causality unclear.

## Decision

`WorldAtlas` is the immutable composition boundary for durable generated world facts that exist before detailed world materialization.

Atlas layers expose semantic read contracts and hide their representation. The first layer is `ElevationField`, which answers surface elevation for each global XY world column. Its current dense bounded representation is package-private and is not a storage promise.

Generation is causal and staged. `ElevationGenerationStage` authors elevation from `WorldGenesis`; later stages will explicitly consume earlier facts when their algorithms require them. `WorldAtlasGenerator` is only a thin orchestration boundary and must delegate domain algorithms to stages rather than accumulate generation logic itself.

Elevation v1 uses deterministic multi-scale value noise addressed by the versioned generation RNG. Its lattice is global rather than rebased to the requested XY bounds, so overlapping worlds with the same seed, generator/RNG revisions and vertical bounds produce the same elevation at the same global XY coordinates.

The generated elevation fact is not Landscape terrain. Future materialization will consume Atlas facts and commit detailed terrain through the Landscape-owned mutation boundary.

## Consequences

- generated macro/world facts have an explicit owner before runtime materialization;
- later geology, climate and drainage can depend on earlier facts without reading detailed terrain as accidental generation input;
- Atlas consumers depend on semantic layer contracts rather than dense arrays;
- storage can later become tiled, compressed or streamed behind the same fact contract when profiling justifies it;
- generation remains deterministic across iteration order and cropping of world XY bounds;
- materialization does not become a second generator or a second owner of Atlas facts.

## Rejected directions

Generating terrain blocks directly from the seed was rejected because it skips the durable causal facts needed by later generation stages and future persistence.

Exposing the current dense elevation array was rejected because it would turn a prototype representation into public world semantics.

Biome-first generation was rejected for this foundation: biome will be derived from causal facts such as elevation, climate and soil rather than acting as the unexplained source of those facts.

Chunk and region identity remain deferred. The first Atlas layer does not need either concept to be correct or deterministic.

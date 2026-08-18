# World Genesis

World Genesis owns immutable provenance for a generated world and the deterministic random contract used while authoring its initial facts. It does not own Terrain, Water, Soil, runtime weather, objects or later runtime mutation.

## Current contract

`WorldSpec` is the requested pre-generation specification. It currently contains:

```text
finite inclusive WorldBounds
ClimateSpec
optional PhysicalSpaceScale
```

`ClimateSpec` is long-term climate intent. Historical revisions V1-V7 use the legacy cell-relative atmospheric-water representation. V8+ use physical water-depth-per-time normals when a complete Atlas is generated. Runtime weather and precipitation remain separate simulation state/processes.

`PhysicalSpaceScale` is optional because not every generation consumer needs a physical cell size. Consumers that require physical dimensions must request them explicitly through `requirePhysicalSpaceScale()` rather than inventing a hidden default.

`WorldGenesis` combines:

```text
WorldSpec
masterSeed
GenerationRevision
RngRevision
WorldGenerationIntent
```

`WorldGenerationIntent` is the high-level macro-generation request. Its current normalized semantic coordinates are:

```text
landCoverage   desired fraction of horizontal columns above global sea level
landmassScale  characteristic spatial coherence / landmass size
fragmentation  fine breakup of the landmass field
```

These values are authored intent, not noise thresholds or physical constants. A generation algorithm is responsible for calibrating its implementation to the requested outcome.

The compatibility constructor without an explicit intent supplies `WorldGenerationIntent.balanced()`. V1-V8 ignore this macro intent, so adding the field does not change historical elevation semantics.

## Generation revisions

`GenerationRevision` versions authored-world semantics. `RngRevision` separately versions the deterministic sampling algorithm.

`WorldGenesis.current(...)` intentionally remains on `evoforge:worldgen-v7` with `evoforge:rng-v1`. Newer revisions are explicit validation targets until their complete contracts are promoted together.

Relevant current boundaries are:

- V1 retains the original cell-quantized elevation semantics;
- V2+ retain precise sub-cell elevation facts;
- V8 introduces the physical climate-water contract for full Atlas generation;
- V9 introduces ocean-first macro elevation driven by `WorldGenerationIntent` while retaining V8 semantics for the other existing Atlas layers.

V9 uses one global macro sea-level datum at `z = 0`. Its elevation generator requires vertical world bounds that contain space both below and above that datum. `landCoverage` is calibrated to the nearest representable horizontal-column count, so requested coverage is preserved to one-column precision instead of being an incidental side effect of a fixed noise threshold.

Historical revisions remain executable. Changing the newest generation algorithm must never silently rewrite the authored facts of an older revision.

## Deterministic random scope

`GenerationRandom` is not a mutable stream. Every sample is addressed independently by:

```text
GenerationStageId
GenerationPurposeId
scopeX, scopeY, scopeZ
ordinal
```

Stage and purpose identifiers use stable namespaced keys such as `world:elevation` and `world:landmass`. Scope coordinates are signed 64-bit values. Direct cell-scoped generation passes global world coordinates unchanged; macro algorithms may use stable lattice coordinates without creating a second authoritative world position or requiring chunk identity.

`ordinal` distinguishes multiple samples for one semantic purpose at one scope. The result depends only on those inputs and the genesis master seed. Calling samples in another order or inserting an unrelated sample does not alter an existing result.

`evoforge:rng-v1` is an EvoForge-owned fixed 64-bit sampling algorithm and is covered by golden vectors. `GenerationRandom.from(...)` accepts only RNG revisions it can execute; unknown revisions fail explicitly.

## Ownership boundary

Genesis metadata is immutable provenance and requested generation input. Generated output belongs to the domain that owns the resulting fact:

- elevation, geology, climate normals, drainage, hydrography and generated initial surface hydrology belong to World Atlas;
- materialized Terrain belongs to Landscape;
- runtime Water and Soil state belong to their existing authoritative systems;
- runtime precipitation/evaporation and WeatherState remain environment behavior;
- objects and agents remain owned by their existing domains.

A generator therefore must not become a permanent second owner of generated state. Generation ends before the started simulation begins evolving the world.

## Persistence rule

The master seed, revisions and intent are provenance, not a substitute for canonical save state. A save must eventually preserve already-generated authoritative facts/state required to continue the existing world. Loading a historical world must not rerun the newest generator and rewrite its past.

The physical save schema is not part of this subsystem contract yet.

## Deliberately deferred

World Genesis does not yet define chunk/region dimensions, loaded/unloaded state, streaming, packed coordinates, plate tectonics, erosion history, mountain chains, biome classification or ecology. Those contracts arrive only when a concrete generation slice needs them.

See [Decision 009 — World genesis provenance and deterministic randomness](../decisions/009-world-genesis-provenance-and-randomness.md), [World Atlas](world-atlas.md), [Generated World Runtime](generated-world-runtime.md) and [Terrain Generation](terrain-generation.md).

# World Genesis

World Genesis owns immutable provenance for a generated world and the deterministic random contract used while authoring its initial facts. It does not own terrain, liquids, climate, objects or later runtime mutation.

## Current contract

`WorldSpec` is the requested pre-generation specification. The current foundation intentionally contains only finite inclusive `WorldBounds`.

`WorldGenesis` combines:

```text
WorldSpec
masterSeed
GenerationRevision
RngRevision
```

`WorldGenesis.current(...)` currently declares `evoforge:worldgen-v2` with `evoforge:rng-v1`.

`evoforge:worldgen-v1` remains a supported historical generation revision for the accepted cell-quantized elevation semantics. V2 preserves the same discrete surface heights while making precise sub-cell elevation a durable Atlas fact. A historical recipe therefore carries enough provenance to request its original authored-fact semantics rather than silently inheriting the newest generator behavior.

The distinction is semantic: `WorldSpec` says what was requested; `WorldGenesis` records how this particular world's initial facts were authored. `GenerationRevision` versions authored-world semantics, while `RngRevision` versions the deterministic sampling algorithm. A generated-fact change does not require a new RNG revision when the random contract itself is unchanged.

## Deterministic random scope

`GenerationRandom` is not a mutable stream. Every sample is addressed independently by:

```text
GenerationStageId
GenerationPurposeId
scopeX, scopeY, scopeZ
ordinal
```

Stage and purpose identifiers use stable namespaced keys such as `world:elevation` and `world:base`. Scope coordinates are signed 64-bit values. Direct cell-scoped generation passes the simulation's global integer XYZ coordinates unchanged; macro algorithms may use stable lattice coordinates without creating a second authoritative world position or requiring chunk/region identity.

`ordinal` distinguishes multiple samples for one semantic purpose at one scope. The result depends only on those inputs and the genesis master seed. Calling samples in another order or inserting an unrelated sample does not alter an existing result.

`evoforge:rng-v1` is an EvoForge-owned fixed 64-bit sampling algorithm and is covered by golden vectors. Existing int-scoped samples are unchanged by the wider scope-coordinate API. `GenerationRandom.from(...)` accepts only RNG revisions it can execute; unknown revisions fail explicitly.

## Ownership boundary

Genesis metadata is immutable provenance. Generated output belongs to the domain that owns the resulting fact:

- elevation/geology/climate normals belong to world-fact/atlas owners;
- materialized terrain belongs to Landscape;
- Water and retained liquid belong to their existing authoritative systems;
- objects and agents remain owned by their existing domains.

A generator therefore must not become a permanent second owner of generated state.

## Persistence rule

The master seed and revisions are provenance, not a substitute for canonical save state. A save must eventually preserve already-generated authoritative facts/state required to continue the existing world. Loading a historical world must not rerun the newest generator and rewrite its past.

The physical save schema is not part of this foundation.

## Deliberately deferred

Genesis does not define region semantics, chunk dimensions, materialization lifecycle, streaming, packed storage, climate parameters or world-calendar semantics. Those contracts arrive only with the slices that need them.

See [Decision 009 — World genesis provenance and deterministic randomness](../decisions/009-world-genesis-provenance-and-randomness.md) and [World Atlas](world-atlas.md).

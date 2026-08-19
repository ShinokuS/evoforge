# ADR-009: World genesis provenance and deterministic randomness

- Status: Accepted
- Scope: World-generation provenance and RNG compatibility
- Decision: World creation records immutable specification/intent/seed/revisions, and generation randomness is an addressable pure function rather than a mutable call-order-dependent stream.

## Context

Procedural worlds must be reproducible without making Java RNG implementation, generator call order, chunk layout or a future save schema hidden generation state. A seed alone is also insufficient once generation semantics evolve.

## Decision

`WorldGenesis` records the immutable world request/provenance: current `WorldSpec`, master seed, `GenerationRevision`, `RngRevision` and `WorldGenerationIntent`.

Generation randomness is addressed by meaning:

```text
master seed
+ GenerationStageId
+ GenerationPurposeId
+ signed 64-bit scope X/Y/Z
+ non-negative ordinal
→ deterministic 64-bit sample
```

Direct cell sampling uses world coordinates; macro algorithms may use stable lattice coordinates as random-scope identity without turning those coordinates into authoritative world positions. Stage/purpose identifiers are stable namespaced semantic keys.

Sample order is not state: inserting an unrelated generation sample cannot shift an existing sample. RNG revision and world-generation revision are separate compatibility dimensions. Unsupported RNG revisions fail explicitly.

Seed/provenance can reproduce initial generation inputs; it does not replace lived authoritative facts in persistence after runtime mutation.

## Why

Addressable randomness localizes change, makes deterministic tests/golden vectors meaningful and decouples generated semantics from incidental control flow.

## Consequences

- Unrelated generator work does not globally reshuffle random output.
- Generator semantics and RNG sampling can version independently.
- Macro algorithms need no premature chunk/region identity merely to address randomness.
- Historical generated-world behavior can remain explicit.
- Persistence must store authoritative lived facts as required, with Genesis retained as provenance rather than “regenerate latest from seed”.

## Alternatives considered

A process-wide mutable `Random` was rejected because call order becomes hidden state. Seed-only persistence was rejected because newer generator semantics could rewrite an existing world's past. Chunk/region keys were not introduced merely for RNG scope.

## Current implementation

`GenerationRandom` V1 uses stable FNV-1a hashing for semantic stage/purpose strings and SplitMix-style `mix64` avalanche constants inside a call-order-independent addressable sampler. `GenerationRevision` currently spans V1–V12; `WorldGenesis.current(...)` deliberately remains a V7 compatibility convenience while current accepted V12 work constructs V12 explicitly. `WorldGenerationIntent` currently carries seven normalized semantic terrain coordinates.

## Related documentation

- [World Genesis](../systems/world-generation/world-genesis.md)
- [World Generation](../systems/world-generation/overview.md)
- [References](../references.md)

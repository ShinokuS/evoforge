# Decision 011 — World generation algorithms compose behind typed contracts

**Status:** Accepted

## Problem

World generation will accumulate multiple algorithms for elevation, drainage, climate, materials and later evaluators. If orchestration stores concrete implementation classes or passes one universal mutable generation context everywhere, replacing one algorithm couples unrelated stages and makes experimentation unsafe.

The opposite extreme is also harmful: a generic plugin framework invented before concrete semantics exist would erase useful type information and predict extension needs we do not yet understand.

## Decision

Each generation layer exposes the narrowest typed algorithm contract required by its semantic input and output. Orchestration depends on that contract, not on a concrete implementation.

The first proven seam is:

```text
ElevationGenerator
    generate(WorldGenesis) -> ElevationField
```

`ElevationGenerationStage` is the current v1 implementation. `WorldAtlasGenerator` composes an `ElevationGenerator`; its default composition selects the current implementation, while alternate implementations may be injected without changing Atlas consumers.

Future layers follow the same rule when their real dependencies are known. A drainage algorithm, for example, should consume the exact upstream facts it requires and return a `DrainageField`; it should not receive a universal mutable `WorldGeneratorContext` merely for convenience.

Evaluators and selectors follow the same discipline: introduce a typed evaluator contract when an evaluator has a concrete semantic question to answer. Do not create one universal evaluator interface or service locator in advance.

Generated fact contracts remain independent from algorithm contracts. Downstream systems read `ElevationField` or future fact interfaces, never the Java class that authored them.

`GenerationRevision` describes authored-world compatibility, not Java implementation identity. Replacing an algorithm with an implementation that intentionally changes generated facts for the same declared inputs requires a new generation revision. Refactors or alternate implementations claiming the same revision must preserve the declared semantics and frozen compatibility expectations.

## Consequences

- generation algorithms can be replaced, decorated, compared or tested in isolation;
- orchestration remains small as the pipeline grows;
- downstream stages depend on semantic facts instead of concrete generators;
- new algorithms do not require central `instanceof`, enum switches or edits to unrelated stages;
- validators can remain outside algorithms and continue protecting layer invariants;
- plugin registries or discovery mechanisms are added only if real runtime/configuration selection requires them;
- persistence stores world provenance and facts, not generator class names.

## Rejected directions

Hard-wiring `ElevationGenerationStage` into `WorldAtlasGenerator` was rejected because it made the first Atlas algorithm non-substitutable.

A universal `WorldGenerationAlgorithm<T>` plus ambient mutable context was rejected because different layers have different causal inputs and outputs, and a generic context would hide those dependencies.

A global algorithm registry is deliberately deferred until there is an actual need to discover or select implementations dynamically.

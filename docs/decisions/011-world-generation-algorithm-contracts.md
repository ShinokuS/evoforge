# ADR-011: World-generation algorithms compose behind typed contracts

- Status: Accepted
- Scope: World-generation algorithm extensibility
- Decision: Every generation/preparation layer exposes the narrowest typed algorithm contract for its real inputs/outputs; orchestration depends on those contracts and generated facts, not concrete implementations or ambient mutable contexts.

## Context

World generation grows through elevation, geology, climate, drainage, hydrography, materials, Soil and later mountain/cave/depositional algorithms. Hard-wiring concrete stages couples experiments/refactors to orchestration. A universal plugin interface plus mutable context would hide causal dependencies and erase useful domain types.

## Decision

Each layer has a typed interface such as `ElevationGenerator`, `DrainageGenerator`, `GeologyGenerator` or a preparation counterpart. Java signatures expose the exact upstream facts required by the layer. Downstream consumers read fact interfaces rather than generator classes.

Complex generation stages may themselves be composed from narrower replaceable contracts when those responsibilities are independently meaningful. Calibration, model recipe and spatial synthesis must not be fused merely because one standard implementation currently uses all three.

`WorldAtlasGenerator` and preparation orchestration compose contracts in causal order. Alternate implementations may be injected without changing downstream fact consumers.

`GenerationRevision` describes authored-world compatibility, not Java class identity: implementations claiming the same revision must preserve declared semantics; intentional changes to durable facts require explicit revision handling.

A global algorithm/service registry or universal evaluator/context is introduced only if a real selection/discovery consumer later proves it necessary.

## Why

Typed contracts make dependencies reviewable, allow isolated deterministic tests/experiments and keep replaceability local without building speculative generic infrastructure.

## Consequences

- Algorithms can be replaced/decorated/compared in isolation.
- Orchestration remains small and domain-neutral.
- Fact contracts can outlive concrete algorithms.
- New algorithms do not require `instanceof`/central enums in unrelated consumers.
- Validators and calibrators remain layer/domain specific.
- A compound stage may expose more than one seam when calibration and synthesis genuinely have independent ownership/tests.

## Alternatives considered

Hard-wiring concrete stages in Atlas orchestration was rejected. A universal `WorldGenerationAlgorithm<T>` with ambient mutable context was rejected because different domains have different causal contracts. A global registry was deferred until dynamic discovery/selection is genuinely needed. Making every private helper a public strategy was also rejected: replaceability is introduced only at real independently meaningful boundaries.

## Current implementation

`WorldGenerationAlgorithms` composes Elevation, Geology, Climate Normals, Drainage, Hydrography and Surface Hydrology generators. `WorldPreparationAlgorithms` similarly composes Surface Morphology, Terrain Shape, Terrain Material and Soil Formation algorithms.

V12 separates `V12LandformCalibrator`, immutable `V12LandformCalibration` / `V12LandformRecipe` and the replaceable spatial elevation implementation behind `ElevationGenerator`.

V13 makes the same rule explicit for dedicated mountains: `V13MountainTerrainGenerator` composes an `ElevationGenerator` base, replaceable `MountainCalibrator`, immutable `MountainCalibration` / `MountainRecipe`, and replaceable `MountainElevationAlgorithm`. The standard `MountainMorphologyAlgorithm` implements that contract directly; orchestration and downstream `ElevationField` consumers do not depend on its concrete class.

## Related documentation

- [World Generation](../systems/world-generation/overview.md)
- [World Atlas](../systems/world-generation/world-atlas.md)
- [Terrain Generation](../systems/world-generation/terrain-generation.md)
- [V13 Mountain Generation](../systems/world-generation/mountain-generation.md)
- [Generated World Runtime](../systems/world-generation/generated-world-runtime.md)

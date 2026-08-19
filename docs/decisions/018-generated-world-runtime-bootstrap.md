# ADR-018: Generated worlds enter the ordinary production runtime

- Status: Superseded
- Scope: Generated-world bootstrap composition
- Decision: The original one-shot `GeneratedWorldBootstrap` composition has been superseded by the explicit preparation/start split of ADR-021; the durable principle remains that generated worlds materialize once into the same ordinary `SimulationRuntime` as hand-authored worlds.

## Context

The first complete generated-world slice needed to connect Genesis/Atlas, Terrain materialization, climate forcing and runtime diagnostics without creating a test-only/generated-only simulation engine or teaching `SimulationAssembly` how to generate worlds.

## Decision

The historical bridge introduced one-shot `GeneratedWorldBootstrap`:

```text
WorldGenesis -> WorldAtlasGenerator -> Atlas
                         ↓
              Terrain/climate setup
                         ↓
              SimulationAssembly.start()
                         ↓
                SimulationRuntime
```

The important accepted rule was that generated worlds enter existing Landscape, Water, Soil, Scheduler and other runtime owners rather than running under a second generated-world ruleset.

Stage 0 later refined the exact ownership boundary into separate preparation and runtime bootstrap phases. `GeneratedWorldBootstrap` remains a compatibility/convenience facade where useful, but is no longer the canonical architectural description.

## Why

Generated facts should initialize existing authoritative owners exactly once. A special generated-world runtime would duplicate rules and make CI/visual/manual behavior diverge.

## Consequences

- Generated and hand-authored worlds share ordinary runtime mechanics after startup.
- Generation does not remain a live runtime owner.
- Content/material selection remains outside a hard-coded generated-world engine.
- The newer explicit preparation phase can grow typed generated/calibrated facts without giving runtime bootstrap generator dependencies.

## Alternatives considered

A second generated-world runtime, generation inside `SimulationAssembly`, hard-coded generated Terrain material, and manual calls to runtime forcing from CI/render code were rejected.

## Current implementation

Canonical composition is now `GeneratedWorldPreparation -> PreparedGeneratedWorld -> GeneratedWorldRuntimeBootstrap -> SimulationRuntime`, as defined by ADR-021. Current bootstrap still contains compatibility initial-Water materialization; final canonical initial Water belongs to world-generation Stage 7 after dry-world acceptance.

## Related documentation

- [Generated World Runtime](../systems/world-generation/generated-world-runtime.md)
- [World Generation](../systems/world-generation/overview.md)
- [ADR-021](021-world-preparation-and-calibration-boundary.md)

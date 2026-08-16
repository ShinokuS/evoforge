# Decision 018 — Generated worlds enter the ordinary production runtime

**Status:** Accepted

## Problem

World Genesis, World Atlas, generated Terrain materialization, hydrologic climate forcing and runtime diagnostics now exist as separate correct slices. Keeping their integration only in headless test loops would create a second, test-specific way to run generated worlds and make desktop/manual behavior diverge from CI.

At the same time, putting world generation logic directly into `SimulationAssembly` would turn the production composition root into a generator/content selector and blur ownership between immutable Atlas facts and lived runtime state.

## Decision

`GeneratedWorldBootstrap` is the one-shot orchestration boundary that turns a `WorldGenesis` into a started production runtime.

```text
WorldGenesis
    ↓
WorldAtlasGenerator
    ↓
WorldAtlas
    ├─ ElevationField ──> WorldTerrainMaterializer ──> Landscape-owned Terrain
    └─ HydroClimateField ────────────────────────────> runtime forcing setup
                                                        ↓
                                                  SimulationAssembly.start()
                                                        ↓
                                                  SimulationRuntime
```

The caller supplies a still-unstarted `SimulationAssembly` that already contains its content/mechanic definitions and a `TerrainMaterialResolver`. Bootstrap does not choose a hard-coded ground material, load a special generated-world content registry, or interpret user-facing world controls.

`SimulationAssembly` exposes two narrow setup capabilities needed by this orchestration:

- `materializeGeneratedTerrain(...)` delegates to the existing `WorldTerrainMaterializer` using Assembly-owned Landscape definitions/mutations;
- `generatedHydroClimate(...)` records one immutable `HydroClimateField` for runtime composition.

Generated HydroClimate and legacy periodic precipitation/evaporation schedules are mutually exclusive in one runtime. This prevents accidental double atmospheric forcing.

During `start()`, generated HydroClimate is realized by `HydroClimateForcingSystem` through a scheduler-bound `HydroClimateForcingProcess`. The process evaluates exactly once for every subsequently advanced simulation tick. It owns cadence only; precipitation, evaporation, Soil, Water and liquid flow keep their existing authoritative owners.

## Consequences

- CI and future desktop generation can use the same production bootstrap path;
- Atlas remains immutable provenance/generated fact data rather than runtime state ownership;
- generated Terrain is still created exclusively through the canonical materialization boundary;
- content selection remains outside bootstrap, so adding ordinary content does not require editing generated-world orchestration;
- generated climate uses the ordinary production scheduler rather than a manual test loop;
- legacy hand-authored periodic atmospheric scenarios remain available but cannot silently stack with generated climate;
- the bootstrap result pairs Atlas provenance, materialization accounting and the started runtime for diagnostics/persistence work.

## Deliberately deferred

This decision does not define warmup duration, viability thresholds, user-facing climate controls, geology/material generation, initial rivers/lakes, persistence format or performance batching for HydroClimate forcing.

The current forcing process evaluates every simulation tick because that is the exact accepted semantic contract. If profiling shows this cadence is expensive, scheduling/analytical batching may optimize it only while preserving the same authoritative results.

## Rejected directions

A second generated-world runtime implementation was rejected because generated worlds must obey the same scheduler and mechanics as ordinary production scenarios.

Making `SimulationAssembly` generate Atlas facts was rejected because the composition root should wire runtime owners, not become a world generator.

Hard-coding `core:soil` or another material in bootstrap was rejected because material identity is content/generated-fact data and future geology must be replaceable behind `TerrainMaterialResolver`.

Calling `HydroClimateForcingSystem.update(...)` manually from CI or rendering code was rejected because it would create an alternate execution lifecycle outside the authoritative simulation scheduler.

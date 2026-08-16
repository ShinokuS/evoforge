# Decision 017 — Generated-world diagnostics are deterministic audit snapshots

**Status:** Accepted

## Problem

Generated-world work is reaching the point where Atlas facts, materialized Terrain and existing runtime hydrology must be exercised together. Manual visual inspection is insufficient for deciding whether a generated world obeys existing rules, and a CI-only test vocabulary would diverge from the information available when a real desktop generation misbehaves.

Logging also cannot become authoritative simulation state. Parsing textual logs as the source of correctness would couple tests to presentation and make observability capable of influencing architecture.

## Decision

Generated-world verification uses an immutable `GeneratedWorldDiagnostics` snapshot produced on demand by `GeneratedWorldDiagnosticsProbe` from:

```text
WorldAtlas + SimulationRuntime read capabilities
```

The probe performs an exact audit of the finite world at an explicit checkpoint. It is not a scheduled simulation process and cannot mutate world state.

The same snapshot is used in two ways:

- headless CI compares/asserts the structured record directly;
- desktop/manual runs may emit the record through `GeneratedWorldDiagnosticsLog` and the existing SLF4J logging pipeline.

The initial audit covers generated/runtime surface agreement, Terrain volume, drainage topology summary, free Water and Soil-retained Water.

Wall-clock performance and presentation state are intentionally absent from this deterministic record.

## Consequences

- CI and real user runs share one diagnostic vocabulary;
- logs remain a representation of observed facts rather than an authority;
- deterministic replay can compare complete snapshots directly;
- Water/Terrain debugging has exact finite-world totals before those systems are expanded;
- adding logging cannot change scheduler order or simulation outcomes;
- expensive full-world scans occur only at deliberate audit checkpoints, not on hot paths.

## Current generated-world baseline

The first CI scenarios use production `WorldAtlasGenerator`, `SimulationAssembly`, scheduler and hydrology mechanics. They verify that an unforced generated world does not create Water spontaneously and that configured precipitation/infiltration on generated porous Terrain is deterministic across replay.

This decision does not claim that the current generated-world bootstrap composition is final. Atlas-driven runtime HydroClimate scheduling, canonical material profiles, warmup policy and later world-generation UI remain separate composition work.

## Rejected directions

Per-tick INFO logging was rejected because it would create hot-path noise and large persistent logs.

A test-only diagnostic model was rejected because CI and desktop failures would then expose different facts.

Parsing log text in tests was rejected because log formatting is observability presentation, not simulation semantics.

Adding balance verdicts such as “healthy world” to the snapshot was rejected because viability/calibration policy has not yet been defined. Diagnostics report facts; evaluators may later interpret them through separate contracts.

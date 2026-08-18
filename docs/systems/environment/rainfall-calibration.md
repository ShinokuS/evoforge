# Rainfall Regime Calibration

## Purpose

Rainfall calibration translates durable physical precipitation normals plus algorithm-independent occurrence statistics into an immutable preparation-time `RainfallRegimeField`.

It does not create Weather, schedule rain, mutate Water/Soil, or choose a runtime algorithm.

```text
ClimateNormalsField
    physical precipitation depth/time
            +
RainfallOccurrenceField
    mean dry-spell duration
    mean wet-spell duration
            ↓
RainfallRegimeCalibrator
            ↓
RainfallRegimeField
            ↓
algorithm-specific compiler
            ↓
runtime weather model
```

## Why precipitation amount is not enough

A long-term precipitation depth alone does not determine rainfall occurrence. The same annual total may be delivered by frequent weak events, rare intense events, or long persistent wet periods. EvoForge therefore does not infer event timing from precipitation amount through hidden constants.

Occurrence and amount remain separate climate statistics. This mirrors established stochastic precipitation modeling practice, where precipitation occurrence/persistence and positive rainfall amount are distinct modeled quantities.

## Current calibrated facts

Each `RainfallRegime` contains:

- exact physical long-term mean precipitation as `WaterDepthRate`;
- `RainfallOccurrenceNormal` with mean dry-spell duration and mean wet-spell duration.

These are preparation data and do not depend on simulation tick duration or cell geometry.

The current `MeanPreservingRainfallRegimeCalibrator` deliberately performs no hidden climatological guess. It requires physical V8+ ClimateNormals and a matching typed occurrence field, then preserves both exactly by world column.

## Current runtime compiler

`AlternatingRainfallPulseCompiler` targets the existing alternating rectangular-pulse weather driver.

For mean dry duration `D`, mean wet duration `W`, and long-term mean precipitation rate `P`, the compiler uses:

```text
wet-time fraction = W / (D + W)
mean wet intensity = P * (D + W) / W
```

Therefore the model's expected long-term precipitation remains `P` rather than changing when event intermittency changes.

Dry/wet spell durations stay unchanged because they are calibrated climatological statistics. The compiled wet intensity is an algorithm-specific parameter and is not world intent.

## Runtime boundary

The current `AlternatingRainfallPulseAtmosphericPlan` is a runtime adapter for one coherent whole-world pulse process. It accepts only a uniform `RainfallRegimeField` and rejects spatial variation rather than flattening it silently.

A future spatial storm/front model should consume spatial rainfall regimes through its own compiler/plan. It must not add type switches to generic atmospheric Water consumers.

## Acceptance scenario

`Generated Rainfall Regime` is a deterministic visual acceptance scene for:

```text
WorldGenesis
  ↓
ClimateNormals
  ↓
RainfallRegime calibration
  ↓
Alternating pulse compilation
  ↓
WeatherState
  ↓
AtmosphericWaterForcing
  ↓
Soil / Water
```

Generated initial surface Water is disabled only for this acceptance scene so runtime rain has an unambiguous visible cause. The tested atmospheric, Soil and Water systems remain production implementations.

This scenario complements the older `Rain Cycle` scenario, which remains useful as an isolated hand-authored Water/Soil acceptance scene.

See [Testing Strategy](../guides/testing.md), [Debug Scenarios](../guides/debug-scenarios.md), and [Decision 020](../decisions/020-world-preparation-and-calibration-boundary.md).

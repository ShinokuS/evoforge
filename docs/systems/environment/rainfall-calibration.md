# Rainfall Regime Calibration

## In plain language

“600 mm of rain per year” tells us **how much** precipitation arrives on average, but not **what the weather feels like**.

The same long-term total could mean:

- frequent light rain;
- rare intense storms;
- long wet spells separated by long dry spells.

EvoForge therefore keeps long-term precipitation amount separate from occurrence/persistence statistics. Preparation combines those independent facts into a `RainfallRegime`; a runtime weather algorithm may then compile that regime into its own operational parameters.

The calibration layer never creates Water or schedules rain itself.

## Current status

Current preparation path:

```text
ClimateNormalsField
  physical long-term precipitation depth/time
              +
RainfallOccurrenceField
  mean dry-spell duration
  mean wet-spell duration
              ↓
MeanPreservingRainfallRegimeCalibrator
              ↓
RainfallRegimeField
              ↓
algorithm-specific compiler/AtmosphericRuntimePlan
              ↓
runtime Weather / AtmosphericWaterForcing
              ↓
ordinary finite Water + Soil mechanics
```

`MeanPreservingRainfallRegimeCalibrator` deliberately makes no hidden climatic guess: the current physical climate normals and occurrence field must both be supplied and use matching world bounds.

## Prepared facts

Each `RainfallRegime` contains:

```text
WaterDepthRate longTermPrecipitation
RainfallOccurrenceNormal occurrence
```

Occurrence currently contains physical mean durations:

```text
meanDrySpellDuration
meanWetSpellDuration
```

These are climate/preparation facts. They do not depend on simulation tick length or cell dimensions.

## Why amount and occurrence are separate

A single average precipitation rate cannot uniquely determine event duration/frequency. Any attempt to derive both from one value would need arbitrary hidden assumptions.

The separation also matches classic stochastic weather-generator practice: daily precipitation occurrence/persistence is commonly modeled as a discrete wet/dry process while positive precipitation amount is modeled separately.

EvoForge does **not** currently implement the full Markov-chain/exponential models in those papers; it preserves the same conceptual separation while using a simpler first runtime compiler.

## Mean-preserving alternating pulse compiler

The current `AlternatingRainfallPulseCompiler` targets a simple whole-world rectangular-pulse weather driver: alternating dry and wet intervals.

Let:

```text
D = mean dry-spell duration
W = mean wet-spell duration
P = long-term mean precipitation rate
```

The wet fraction of time is:

```text
wetFraction = W / (D + W)
```

To keep the long-run average equal to `P`, wet-period intensity must be:

```text
I = P / wetFraction
  = P * (D + W) / W
```

Why this preserves the mean:

```text
long-run mean
  = wetFraction * I
  = W/(D+W) * P*(D+W)/W
  = P
```

So changing intermittency does not accidentally change total climate-normal precipitation.

The dry/wet durations remain the calibrated climatological statistics. `I` is an **algorithm-specific runtime parameter**, not authored world intent.

## Runtime boundary

Current `AlternatingRainfallPulseAtmosphericPlan` represents one coherent whole-world pulse process. It therefore accepts only a uniform `RainfallRegimeField` and rejects spatial variation instead of flattening/averaging it silently.

That failure is important: a future spatial storm/front algorithm should receive the spatial regime through its own compiler/plan. Generic atmospheric Water consumers should not acquire a concrete weather-model switch.

## Relationship to generation intent

Player/content-facing world creation should not expose “wet pulse intensity = X” as a universal control. The stable meaning is climate character/statistics; exact runtime pulse parameters belong to the selected weather algorithm.

The desired layering remains:

```text
human semantic climate intent
        ↓
physical climate/occurrence calibration
        ↓
RainfallRegime
        ↓
weather-algorithm compiler
        ↓
runtime parameters
```

This is the same Definition → calibration → algorithm principle used in Soil and V12 terrain generation.

## Acceptance scene

`Generated Rainfall Regime` is the deterministic visual/integration scene for:

```text
WorldGenesis
  ↓
ClimateNormals
  + occurrence statistics
  ↓
RainfallRegime calibration
  ↓
Alternating pulse compilation
  ↓
WeatherState / AtmosphericWaterForcing
  ↓
Surface Hydrology
  ↓
finite Soil + Water response
```

Generated initial surface Water is intentionally disabled in that focused acceptance fixture so visible wetting has an unambiguous runtime-rain cause. The Water/Soil systems themselves remain production implementations.

The older Rain Cycle remains useful as a hand-authored isolated hydrology acceptance scene.

## Invariants

- Long-term precipitation amount and occurrence timing are independent prepared facts.
- Calibration never invents occurrence statistics from precipitation amount.
- Current mean-preserving calibrator preserves supplied physical facts exactly.
- Runtime compilation preserves the long-term mean mathematically.
- Algorithm-specific pulse intensity is not authored semantic intent.
- A runtime plan that cannot represent spatial variation rejects it rather than flattening it.
- Calibration does not own Weather, Water, Soil or Scheduler state.

## Current limitations

The current alternating-pulse compiler does not model:

- stochastic spell-length variation around the means;
- event-to-event intensity distributions;
- spatial storms/fronts;
- seasonality;
- correlated temperature/wind/humidity;
- convective versus stratiform rainfall;
- extremes/tails beyond the simple pulse model.

Those are future weather-algorithm concerns. The prepared regime contract can evolve only when real consumers prove additional climate statistics are required.

## Code and tests

Primary calibration code:

```text
world/calibration/rainfall/
```

Runtime integration is composed through atmospheric runtime plans/weather drivers rather than calibration mutating hydrology directly.

Tests cover physical-field/bounds requirements, exact preservation of mean/occurrence facts, mean-preserving pulse compilation, rejection of unsupported spatial variation and generated rainfall-regime visual/runtime integration.

## Sources

**Conceptual/algorithm lineage:** Richard W. Katz (1977), “Precipitation as a Chain-Dependent Process”, *Journal of Applied Meteorology* 16(7), 671–676, DOI 10.1175/1520-0450(1977)016<0671:PAACDP>2.0.CO;2, models precipitation occurrence through chain dependence and precipitation amount as a distinct stochastic quantity.

**Conceptual/algorithm lineage:** C. W. Richardson (1981), “Stochastic simulation of daily precipitation, temperature, and solar radiation”, *Water Resources Research* 17(1), 182–190, DOI 10.1029/WR017i001p00182, uses a Markov-chain/exponential daily precipitation model.

**Internal EvoForge design:** the current mean-preserving alternating rectangular-pulse compiler and its exact `I = P(D+W)/W` parameterization are a deliberately simpler project model, not a direct implementation of either stochastic generator.

See [References](../../references.md), [Surface Hydrology](hydrology.md), [Generated World Runtime](../world-generation/generated-world-runtime.md), and [ADR-021](../../decisions/021-world-preparation-and-calibration-boundary.md).

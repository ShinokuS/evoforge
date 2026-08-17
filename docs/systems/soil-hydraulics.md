# Soil Hydraulics

## Purpose

Soil hydraulics turns a small human-authored description of a landscape material into physical soil behavior without exposing simulation-scale numbers in content.

The canonical path is:

```text
landscape JSON
  texture + organicMatter
        ↓
SoilHydraulicDefinitionCompiler
        ↓
SoilSemanticProfile
        ↓
SoilCompositionCompiler
        ↓
SoilCompositionProfile
  sand / silt / clay / organic matter
        ↓
SoilHydraulicCalibrator
        ↓
SoilHydraulicProfileBindings
  porosity
  field capacity
  permanent wilting point
  saturated hydraulic conductivity
        ↓
SoilHydraulicRuntimeBinder
  + PhysicalSpaceScale
  + SimulationTimeScale
        ↓
SoilProperties
        ↓
==================== START ====================
        ↓
SoilLiquidSystem / precipitation / free-liquid flow
```

Generation and calibration end before runtime starts. Runtime never interprets authored texture classes and never invokes a pedotransfer model.

## Authoring boundary

Canonical landscape content describes soil in terms that are meaningful without understanding the solver:

```json
"soil": {
  "texture": "loam",
  "organicMatter": "rich"
}
```

The author does **not** provide:

- runtime pore `capacity`;
- per-tick `permeability`;
- saturated hydraulic conductivity;
- field capacity or wilting point;
- sand/silt/clay percentages required by the current pedotransfer implementation.

Those values are derived. `SoilHydraulicDefinitionCompiler` deliberately rejects unknown low-level fields, so this is a checked architecture boundary rather than a documentation convention.

The current semantic vocabulary is intentionally small: `sand`, `loam`, `clay` texture and `minimal`, `moderate`, `rich` organic-matter character. It can grow when authored world intent needs more distinctions; simulation complexity alone is not a reason to enlarge JSON.

## Semantic to physical composition

`SoilCompositionCompiler` is the replaceable boundary between authored character and physical composition. The current `RepresentativeSoilCompositionCompiler` maps each semantic texture to one model-owned representative point inside that textural region and maps organic-matter character to a representative physical fraction.

These exact fractions are implementation data, not content. A later spatial soil-development compiler can derive composition from parent material, deposition, climate, relief, vegetation and age while keeping the same landscape authoring surface.

USDA-NRCS uses texture classes as the classification of mineral soil from sand, silt and clay proportions. The current representative projection is therefore an internal bridge from a standard human-readable texture name to the physical inputs required by the hydraulic model; it is not a claim that every loam has one exact composition.

## Physical hydraulic facts

`SoilCompositionProfile` is algorithm-independent physical composition. Sand, silt and clay close the mineral texture triangle; organic matter is a separate predictor.

`SoilHydraulicProfile` is immutable calibrated physical data containing:

- porosity / saturated volumetric water content;
- field capacity;
- permanent wilting point;
- saturated hydraulic conductivity as physical water depth per physical time.

`SoilProperties` is **not** authored soil data. It is only the current runtime representation compiled for one physical cell geometry and simulation tick duration.

`SoilLiquidSystem` remains the sole authoritative mutable owner of retained liquid during simulation.

## Replaceable hydraulic calibration

`SoilHydraulicCalibrator` is the narrow physical calibration seam:

```java
SoilHydraulicProfile calibrate(SoilCompositionProfile composition)
```

The current implementation is `SaxtonRawls2006SoilHydraulicCalibrator`, based on Saxton & Rawls (2006), *Soil Science Society of America Journal* 70:1569-1578, DOI 10.2136/sssaj2005.0117.

Its empirical coefficients belong only to that implementation. A measured-property calibrator, another pedotransfer model or a future soil-development model may replace it without changing runtime Soil or the authored landscape schema.

Pedotransfer output is an estimate of statistical soil behavior, not a measured property of every real soil with the same texture.

## Stable-key composition

The generic definition pipeline supplies the stable key of each landscape definition. The soil compiler therefore produces `SoilHydraulicProfileBindings` directly under the same keys used by generated `TerrainMaterialKey` values such as `core:topsoil` and `core:sand`.

There is no second worldgen soil-binding JSON and no material-name switch in code:

```text
core:topsoil.json
      ↓
DefinitionLoader
      ↓
soil aspect compiler
      ↓
"core:topsoil" hydraulic binding
      ↓
TerrainMaterialBindings
      ↓
runtime LandscapeDefinitionId
```

A landscape definition without a `soil` aspect is intentionally non-soil from this hydraulic perspective.

## Runtime compilation

`SoilHydraulicRuntimeBinder` joins calibrated stable-key hydraulic facts to runtime landscape IDs. `SoilHydraulicRuntimeCompiler` then converts physical porosity and saturated conductivity using `PhysicalSpaceScale` and `SimulationTimeScale`.

For the current retained-liquid solver:

```text
available pore storage = compiled porosity - retained liquid

one-step saturated uptake cap
    = compiled saturated conductivity
      adjusted by liquid viscosity
```

Rain enters retained Soil up to that storage/rate limit. Excess remains free Water and may form puddles or run off through the ordinary liquid solver. There is no `if raining -> create puddle` rule.

Field capacity and permanent wilting point remain in the physical profile even though the current runtime does not yet consume them. They are preparation facts needed by future drainage, plant-available water, evaporation and unsaturated-flow models.

## Current exactness boundary

The current runtime still stores permeability as an integer normalized volume per tick. If a physical conductivity, cell height and tick duration would require a fractional normalized unit, `SoilHydraulicRuntimeCompiler` rejects the combination instead of silently rounding it.

This restriction belongs to the present runtime representation, not to authored content or `SoilHydraulicProfile`. A later rational infiltration accumulator can remove it without changing either boundary.

## Deliberately absent

This slice does not yet model:

- unsaturated hydraulic conductivity as a function of water content;
- matric potential / capillary suction;
- Richards-equation or Green-Ampt infiltration;
- vertical redistribution and deep drainage;
- groundwater coupling;
- spatial soil horizons and pedogenesis;
- compaction, gravel, salinity and structural corrections;
- root uptake.

Those processes should consume or enrich physical soil facts. They must not reintroduce material-key conditionals or authored per-tick rates.

## Acceptance

`Water / Hydrology -> Soil Hydraulic Contrast` now starts from two semantic profiles under the same generated rain event:

```text
sand + moderate organic matter ─┐
                                ├→ derived composition
clay + moderate organic matter ─┘
        ↓
Saxton-Rawls hydraulic calibration
        ↓
pre-start runtime compilation
        ↓
identical Weather event
        ↓
different infiltration
        ↓
different retained/free Water response
```

The displayed diagnostics expose the derived physical values for inspection, while the scenario input remains at the semantic authoring level.

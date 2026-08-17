# Soil Hydraulics

## Contract

Soil follows the same ownership law as the rest of generated-world state:

```text
Definition
  normalized semantic character
        ↓
Generated / calibrated properties
  composition + hydraulic profile
        ↓
Runtime representation
        ↓
Runtime state
  retained and free liquid
```

A Definition describes what a material means. It does not contain solver-scale pore capacity,
per-tick permeability, or world-local random variation. Generation/calibration derives physical
facts before startup. Simulation consumes those facts and owns only changing state.

## Authored semantics

Landscape JSON uses continuous normalized coordinates:

```json
"soil": {
  "mineralFineness": 0.4,
  "organicMatter": 0.9
}
```

Both values are `NormalizedValue` in `[0,1]`. JSON decimals are compiled to exact fixed-point
parts-per-million values; runtime does not carry authored floating point.

`mineralFineness` is a monotonic semantic coordinate from coarse to fine mineral character.
`organicMatter` expresses relative organic-matter tendency. They are not physical percentages and
there are no hidden `poor / medium / rich` or `sand / loam / clay` thresholds.

`SoilDefinitionCompiler` parses only this semantic aspect and produces
`SoilSemanticProfileBindings`. It deliberately knows nothing about Saxton-Rawls, conductivity,
porosity, cell size or tick duration.

## Semantic → physical composition

`SoilCompositionCompiler` is a replaceable preparation model. The current
`ContinuousSoilCompositionCompiler` projects mineral fineness continuously through a quadratic
mixture curve and uses an explicit `SoilCompositionCalibration` for the maximum representative
organic fraction.

The result, `SoilCompositionProfile`, contains physical sand/silt/clay and organic-matter fractions.
Those values are generated/model output, not authored content.

A future soil-development model may derive the same profile from parent geology, deposition,
weathering, climate, vegetation, compaction and history without changing runtime Soil.

## Physical hydraulic profile

`SoilHydraulicCalibrator` converts physical composition into an immutable
`SoilHydraulicProfile` containing:

- porosity / saturated volumetric water content;
- field capacity;
- permanent wilting point;
- saturated hydraulic conductivity as physical water depth per physical time.

The current replaceable implementation is `SaxtonRawls2006SoilHydraulicCalibrator`. Its empirical
coefficients belong only to that model. They are not Definition constants or runtime rules.

`SoilHydraulicProfileResolver` is the explicit boundary that combines semantic bindings with a
composition compiler and hydraulic calibrator. Definition loading therefore stops at semantics;
physical resolution is a separate preparation operation.

## Runtime boundary

`SoilHydraulicRuntimeBinder` joins calibrated material-key profiles to runtime landscape IDs.
`SoilHydraulicRuntimeCompiler` converts porosity and conductivity using `PhysicalSpaceScale` and
`SimulationTimeScale` into the current `SoilProperties` representation.

Runtime `SoilLiquidSystem` receives a `SoilPropertiesLookup` and never recalibrates or interprets a
material key. `TerrainSoilPropertiesLookup` now returns the properties bound to terrain; it does not
hash `(seed,x,y,z)` or invent local pore capacity during simulation.

This is intentional: until geology/deposition/history produce a causal spatial property field, a
truthful homogeneous material is preferable to fake coordinate noise.

## Emergent puddles

There is no Puddle definition, generator or `if raining -> create puddle` rule.

```text
rainfall
  ↓
free surface Water
  ↓
local infiltration / pore-capacity limit
  ↓
absorbed amount + remaining free Water
  ↓
ordinary liquid flow / retention
```

A puddle is therefore an observed state of free Water. Future spatially varying generated hydraulic
properties can make apparently identical topsoil respond differently at neighboring cells without
changing the liquid mechanic.

## Exactness

Physical conductivity is preserved up to the runtime compilation boundary. The current runtime
stores permeability as whole normalized volume per tick, so a physical combination requiring a
fractional unit is rejected rather than silently rounded. Acceptance scenarios must use a physical
time/space scale that is representable by the current runtime. A later rational infiltration rate
can remove this representation limit without changing authored semantics or `SoilHydraulicProfile`.

## Acceptance

`Water / Hydrology -> Soil Hydraulic Contrast` compares fineness `0.10` and `0.80` with identical
organic character under the same generated climate and rainfall process. Composition and hydraulics
are derived before startup; the scenario then demonstrates different retained/free Water response
through ordinary Weather, Soil and Water systems.

The older `Rain Cycle` acceptance no longer relies on seeded runtime soil variation. It prepares
explicit low/high hydraulic regions so the test still proves the same causal rule without allowing
Simulation to manufacture world properties.

## Deferred physics

This slice does not yet implement unsaturated conductivity curves, matric suction, vertical
redistribution/deep drainage, groundwater coupling, soil horizons, pedogenesis, compaction/gravel
corrections or root uptake. Those mechanisms should consume or enrich generated physical facts;
they must not reintroduce material-name switches or authored per-tick values.

# Soil Hydraulics

## Contract

Soil follows the same ownership law as the rest of generated-world state:

```text
Definition
  normalized semantic character
        ↓
Generated / calibrated properties
  composition + physical hydraulic profile
        ↓
Generated spatial property field
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

## Generated spatial properties

`SoilHydraulicProfileField` is the immutable spatial preparation contract. Material identity is not
part of that interface: the same `TerrainMaterialKey` may eventually resolve to different hydraulic
profiles at neighboring coordinates because of parent geology, deposition, compaction, drainage,
vegetation or soil-development history.

`MaterialSoilHydraulicProfileField` is only the current deterministic baseline. It projects a
prepared material identity to its calibrated profile and deliberately adds no coordinate noise.
Later causal soil generators can replace that implementation without changing runtime mechanics.

`GeneratedLandscapeProperties` distinguishes two cases that must not be conflated:

- no generated Soil field was prepared: legacy definition-backed runtime Soil remains available;
- a generated Soil field exists: it is authoritative per coordinate, including a local `null`
  meaning that generated Terrain cell is non-porous.

This prevents a spatially resolved non-soil cell from silently falling back to a material-wide Soil
Definition.

## Runtime boundary

`SoilHydraulicRuntimeCompiler` converts one physical hydraulic profile using `PhysicalSpaceScale`
and `SimulationTimeScale` into the current `SoilProperties` representation.

`SoilHydraulicRuntimeFieldCompiler` performs that conversion before runtime starts. It preflights the
solid generated Terrain domain and compiles each distinct physical profile once. The resulting
`SoilPropertiesLookup` is read-only; runtime lookup does not calibrate, perform unit conversion, or
invent world properties.

`GeneratedWorldRuntimeBootstrap` injects that lookup into `SimulationAssembly` before `start()`.
The assembly exposes one pre-start selection seam and freezes it at startup. `SoilLiquidSystem`
continues to depend only on `SoilPropertiesLookup` and therefore knows nothing about world
generation, material keys, pedotransfer models or preparation algorithms.

If a prepared generated Soil field is present, explicit physical space and physical tick duration
are required because physical conductivity cannot be converted honestly without both. If no field
is present, existing definition-backed Soil behavior is preserved for legacy/manual scenarios.

The obsolete `SoilPropertiesVariation` coordinate-hash path has been removed. Spatial physical
differences must now arrive as prepared generated facts.

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

A puddle is therefore an observed state of free Water. Spatially varying generated hydraulic
properties can make apparently identical topsoil respond differently at neighboring cells without
changing the liquid mechanic.

## Exactness

Physical conductivity is preserved up to the runtime compilation boundary. The current runtime
stores permeability as whole normalized volume per tick, so a physical combination requiring a
fractional unit is rejected rather than silently rounded. Acceptance scenarios must use a physical
time/space scale that is representable by the current runtime. A later rational infiltration rate
can remove this representation limit without changing authored semantics, generated fields, or
`SoilHydraulicProfile`.

## Acceptance

`Water / Hydrology -> Soil Hydraulic Contrast` compares fineness `0.10` and `0.80` with identical
organic character under the same generated climate and rainfall process. Composition and hydraulics
are derived before startup; the scenario then demonstrates different retained/free Water response
through ordinary Weather, Soil and Water systems.

Generated-world integration tests additionally prove that one runtime may expose different
`SoilProperties` at different coordinates even when all Terrain cells share the same material
identity, and that an authoritative generated `null` does not fall back to material-wide Soil.

The older `Rain Cycle` and Cow visual fixtures use explicit hydraulic terrain materials. They no
longer rely on seeded runtime soil variation.

## Deferred physics

This slice does not yet implement unsaturated conductivity curves, matric suction, vertical
redistribution/deep drainage, groundwater coupling, soil horizons, pedogenesis, compaction/gravel
corrections or root uptake. Those mechanisms should consume or enrich generated physical facts;
they must not reintroduce material-name switches or authored per-tick values.

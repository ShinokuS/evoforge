# Soil Hydraulics

## Contract

Soil follows the same ownership law as the rest of generated-world state:

```text
Definition
  normalized semantic character
        ↓
Generated local development
  morphology + drainage + material identity
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
porosity, cell size, tick duration or where a material appears in one particular world.

## Causal local Soil formation

`SoilFormationGenerator` is the replaceable boundary that develops authored material archetypes
from generated world facts. The first implementation, `SoilFormationGenerationStage`, consumes:

- `TerrainMaterialField` — which authored archetype exists in a generated solid cell;
- `SurfaceMorphologyField` — local maximum slope and positive concavity derived from precise
  elevation;
- `DrainageField` — contributing-area accumulation across the closed generated world;
- `SoilSemanticProfileBindings` — immutable authored material meaning.

`GeneratedWorldPreparation` derives surface morphology once and passes that same immutable field to
both Terrain material generation and Soil formation. Terrain and Soil therefore cannot quietly use
two different definitions of local slope/concavity.

The first formation model is deliberately narrow. It adjusts only `mineralFineness`:

- topographic exposure/steepness moves the local developed profile toward coarser mineral character;
- concavity moves it toward finer accumulated material;
- drainage accumulation continuously strengthens the concavity response.

All responses are continuous fixed-point transformations. Morphology uses smooth saturating
responses and drainage uses relative contributing area; there are no texture categories, named
terrain switches or gameplay threshold bands. `SoilFormationCalibration` owns the explicit model
response scales and maximum allowed shift away from the authored archetype.

`organicMatter` is intentionally left unchanged by this first geomorphic model. Changing it without
vegetation, climate history and pedogenesis would only replace coordinate noise with another
unsupported guess.

Generated Geology is also not interpreted yet. Current geology Definitions provide stable unit and
material identity but no physical weathering/mineral-release traits. Soil formation therefore does
not infer parent-material behavior from names such as `granite` or `limestone`. A later geology
slice must add explicit physical/semantic parent-material traits before geology can causally alter
soil composition.

## Semantic → physical composition

After local formation, `SoilCompositionCompiler` converts the developed semantic profile into
physical composition. The current `ContinuousSoilCompositionCompiler` projects mineral fineness
continuously through a quadratic mixture curve and uses an explicit `SoilCompositionCalibration`
for the maximum representative organic fraction.

The result, `SoilCompositionProfile`, contains physical sand/silt/clay and organic-matter fractions.
Those values are generated/model output, not authored content.

This boundary remains replaceable: later weathering, sediment provenance, compaction or pedogenesis
models may produce richer composition without changing runtime Soil.

## Physical hydraulic profile

`SoilHydraulicCalibrator` converts physical composition into an immutable
`SoilHydraulicProfile` containing:

- porosity / saturated volumetric water content;
- field capacity;
- permanent wilting point;
- saturated hydraulic conductivity as physical water depth per physical time.

The current replaceable implementation is `SaxtonRawls2006SoilHydraulicCalibrator`. Its empirical
coefficients belong only to that model. They are not Definition constants or runtime rules.

`SoilHydraulicProfileResolver` remains the material-level compatibility boundary for callers that
need one physical profile per authored material. The generated-world path with Soil semantics uses
`SoilFormationGenerationStage` instead and therefore may produce multiple physical profiles for the
same `TerrainMaterialKey` in one world.

## Generated spatial properties

`SoilHydraulicProfileField` is the immutable spatial preparation contract. Material identity is not
part of that interface: the same `TerrainMaterialKey` may resolve to different hydraulic profiles at
neighboring coordinates because generated local development happened before runtime.

`SoilFormationGenerationStage` materializes those local physical profiles during preparation.
`MaterialSoilHydraulicProfileField` remains a deterministic material-level compatibility adapter; it
adds no coordinate noise and is not needed by the causal generated-world path.

`GeneratedWorldPreparation.prepare(genesis, profile, soilSemantics)` produces the authoritative Soil
field and stores it in `GeneratedLandscapeProperties`. The historical two-argument `prepare` remains
available for worlds/scenarios that intentionally have no generated Soil field.

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
local generated infiltration / pore-capacity limit
  ↓
absorbed amount + remaining free Water
  ↓
ordinary liquid flow / retention
```

A puddle is therefore an observed state of free Water. Spatially varying generated hydraulic
properties can make apparently identical topsoil respond differently at neighboring cells without
changing the liquid mechanic.

## Exactness

Authored semantic values and local formation responses use fixed-point integer coordinates. Physical
conductivity is preserved up to the runtime compilation boundary. The current runtime stores
permeability as whole normalized volume per tick, so a physical combination requiring a fractional
unit is rejected rather than silently rounded. A later rational infiltration rate can remove this
representation limit without changing authored semantics, generated fields or
`SoilHydraulicProfile`.

## Acceptance

`SoilFormationGenerationStageTest` fixes an exact causal example in which three cells share one Soil
material archetype but receive exposed, neutral and depositional morphology/drainage contexts. The
resulting developed mineral-fineness coordinates are distinct while authored organic character is
preserved.

`CausalSoilFormationBootstrapIntegrationTest` carries that distinction through the full boundary:
Definition semantics -> generated formation -> physical hydraulic field -> runtime
`SoilPropertiesLookup`. This supplements the lower-level #88 acceptance that already proves a
prepared spatial field overrides material fallback authoritatively.

`Water / Hydrology -> Soil Hydraulic Contrast` still verifies the liquid-side physical consequence
of contrasting hydraulics under the same rainfall process. The older `Rain Cycle` and Cow visual
fixtures use explicit hydraulic terrain materials and no seeded runtime variation.

## Deferred physics

This slice does not yet implement parent-geology weathering traits, sediment provenance/history,
climate-driven pedogenesis, vegetation/organic accumulation, compaction/gravel corrections, soil
horizons, unsaturated conductivity curves, matric suction, vertical redistribution/deep drainage,
groundwater coupling or root uptake. Those mechanisms should consume or enrich generated physical
facts; they must not reintroduce material-name switches, coordinate hashes or authored per-tick
values.

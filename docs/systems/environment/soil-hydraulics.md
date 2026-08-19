# Soil Hydraulics

## In plain language

Soil Hydraulics answers: **how much liquid can this piece of Soil hold, how easily can liquid enter it, and how do local terrain conditions change that behavior before the world starts?**

EvoForge deliberately separates three ideas:

1. a human-authored Soil description such as “coarser/finer mineral character” and “more/less organic tendency”;
2. generated local development caused by the world around that Soil (convex exposed site versus concave accumulation site, drainage accumulation, etc.);
3. physical hydraulic properties such as porosity, field capacity, wilting point and saturated conductivity.

Runtime Soil then owns only changing retained liquid. It does not rerun pedotransfer/calibration every tick.

## Current status

The current generated-world Soil path is:

```text
semantic Soil definition
        ↓
SoilDefinitionCompiler
        ↓
SoilSemanticProfileBindings
        ↓
local generated formation
  material + morphology + drainage
        ↓
continuous physical composition
  sand / silt / clay / organic fraction
        ↓
Saxton-Rawls 2006 hydraulic calibration
        ↓
SoilHydraulicProfileField
        ↓
pre-start runtime-unit compilation
        ↓
SoilPropertiesLookup
        ↓
ordinary runtime SoilLiquidSystem
```

This is a real causal generated-field path. The old coordinate-hash `SoilPropertiesVariation` path is no longer the generated-world source of spatial Soil differences.

## Authored semantic coordinates

Landscape definition data may contain:

```json
"soil": {
  "mineralFineness": 0.4,
  "organicMatter": 0.9
}
```

Both values are exact normalized `0..1` coordinates compiled to integer parts-per-million (`0..1_000_000`).

They are **semantic**, not physical percentages:

- `mineralFineness`: monotonic coarse → fine mineral character;
- `organicMatter`: relative tendency toward more organic material.

There are no authored texture categories such as `sand/loam/clay` and no hidden threshold bands.

## Local generated Soil formation

`SoilFormationGenerationStage` develops an authored material archetype using:

```text
TerrainMaterialField
SurfaceMorphologyField
DrainageField
SoilSemanticProfileBindings
```

The first model changes only `mineralFineness`. `organicMatter` is intentionally preserved because current generation lacks vegetation/climate-history/pedogenesis causes that would justify changing it.

### Morphology inputs

For each surface column the model uses:

```text
maximum local slope
convexity >= 0
concavity >= 0
contributing drainage area
```

Convexity represents a locally exposed/high position; concavity represents a locally accumulating/low position.

Absolute slope is **not** interpreted as erosion direction. A concave basin bottom can have a large neighbor elevation difference while still being an accumulation site.

### Smooth saturating response

For non-negative morphology value `v` and characteristic scale `k`, the response is:

```text
response(v,k) = v / (v + k)
```

represented on the normalized fixed-point scale.

Current representative calibration uses:

```text
convexity characteristic = 1 elevation cell
concavity characteristic = 1 elevation cell
maximum fineness shift   = 0.20
```

So morphology influence saturates smoothly rather than crossing arbitrary classes.

### Drainage response

For contributing area `A` and total horizontal world area `N`:

```text
drainageResponse = (A - 1) / (N - 1)
```

for `N > 1`; a one-column world uses zero response.

`A` must lie in `1..N`.

### Concavity + drainage accumulation

Let:

```text
c = normalized concavity response
 d = normalized drainage response
```

Drainage may strengthen a concave accumulation site only within the remaining normalized headroom:

```text
accumulation = c + c*d*(1-c)
```

This has useful properties:

- if `c = 0`, drainage alone does not manufacture a concavity effect;
- the result is monotonic/bounded;
- no extra arbitrary weighting constant is required.

### Net fineness shift

Let:

```text
e = convex exposure response
a = accumulation response
m = maximum allowed fineness shift
```

Then:

```text
netGeomorphicResponse = a - e
finenessShift = netGeomorphicResponse * m
developedFineness = clamp01(authoredFineness + finenessShift)
```

Therefore:

- convex exposure moves the local profile coarser;
- concave/drained accumulation moves it finer;
- a neutral site stays near its authored archetype.

All these formation calculations use deterministic integer/fixed-point arithmetic.

## Semantic profile -> physical composition

`ContinuousSoilCompositionCompiler` converts developed semantic coordinates to physical fractions.

Let:

```text
f = mineralFineness in [0,1]
c = 1 - f
```

Current quadratic Bernstein-style mineral projection is:

```text
sand = c²
clay = f²
silt = 1 - sand - clay
```

so equivalently:

```text
silt = 2*f*(1-f)
```

with deterministic fixed-point rounding.

Authored organic character is scaled separately:

```text
organicFraction
  = authoredOrganicMatter * maximumRepresentativeOrganicFraction
```

Current representative maximum organic fraction is:

```text
0.05  (50_000 ppm)
```

This composition mapping is an **EvoForge calibration choice**, not part of Saxton-Rawls itself.

## Physical hydraulic calibration: Saxton & Rawls (2006)

`SaxtonRawls2006SoilHydraulicCalibrator` consumes physical sand/clay fractions and organic matter percentage and estimates:

```text
θ1500  permanent wilting-point water content
θ33    field-capacity water content
θS     saturated water content / porosity
Ks     saturated hydraulic conductivity
```

### Inputs

Let:

```text
S  = sand fraction, 0..1
C  = clay fraction, 0..1
OM = organic matter percentage, 0..100
```

### Wilting-point estimate

First intermediate:

```text
θ1500t = -0.024*S
         +0.487*C
         +0.006*OM
         +0.005*S*OM
         -0.013*C*OM
         +0.068*S*C
         +0.031
```

Correction:

```text
θ1500 = θ1500t + (0.14*θ1500t - 0.02)
```

### Field-capacity estimate

```text
θ33t = -0.251*S
       +0.195*C
       +0.011*OM
       +0.006*S*OM
       -0.027*C*OM
       +0.452*S*C
       +0.299
```

Correction:

```text
θ33 = θ33t
      + (1.283*θ33t² - 0.374*θ33t - 0.015)
```

### Saturated water content / porosity

First estimate of the saturation-minus-field-capacity difference:

```text
Δθt = 0.278*S
      +0.034*C
      +0.022*OM
      -0.018*S*OM
      -0.027*C*OM
      -0.584*S*C
      +0.078
```

Correction:

```text
Δθ = Δθt + (0.636*Δθt - 0.107)
```

Then:

```text
θS = θ33 + Δθ - 0.097*S + 0.043
```

The implementation rejects non-physical outputs unless:

```text
0 < θ1500 <= θ33 <= θS <= 1
```

### Saturated hydraulic conductivity

The current code derives:

```text
b = (ln(1500) - ln(33)) / (ln(θ33) - ln(θ1500))
λ = 1 / b

Ks_mm_per_hour = 1930 * (θS - θ33)^(3 - λ)
```

and stores saturated conductivity as physical `WaterDepthRate`, quantized to whole micrometres per hour.

Water contents are quantized to one part per million at the generated physical-profile boundary.

These are empirical pedotransfer estimates, not direct measurements of an individual soil.

## Spatial generated hydraulic field

`SoilHydraulicProfileField` is coordinate-aware. The same `TerrainMaterialKey` can therefore have different physical hydraulic profiles in different columns because local generated formation occurred before runtime.

This is important: material identity does not imply one globally uniform Soil hydraulic state.

A local `null` in an authoritative prepared Soil field means the generated Terrain cell is non-porous. It must not silently fall back to a material-wide legacy Soil definition merely because that would be convenient.

## Runtime unit compilation

Physical saturated conductivity has units of length/time. Runtime infiltration uses normalized cell volume per simulation tick. Therefore generated-world bootstrap needs both:

```text
PhysicalSpaceScale
SimulationTimeScale
```

`SoilHydraulicRuntimeCompiler` / field compiler perform this conversion **before `SimulationAssembly.start()`**.

The current runtime permeability representation is whole normalized volume per tick. If a physical combination cannot be represented exactly under that integer contract, compilation rejects it rather than silently rounding away a meaningful fractional rate.

Runtime `SoilLiquidSystem` then sees only `SoilPropertiesLookup`; it knows nothing about Saxton-Rawls, material keys, terrain generation or semantic authoring.

## Emergent puddles

Spatial Soil differences influence the finite Water cycle causally:

```text
same rain amount
       ↓
locally different generated Soil hydraulics
       ↓
different retained amount / rate
       ↓
different excess free Water
       ↓
ordinary hydraulic flow
```

A puddle is an observed state of finite free Water, not a generated Puddle entity.

## Geology boundary

Current generated geology identity is not yet used to modify Soil formation because current geology definitions do not expose physical weathering/mineral-release traits.

The Soil system deliberately does **not** switch on names such as granite/limestone to guess parent-material effects.

Stage 3 geology must first create explicit causal physical/semantic traits before a later Soil/pedogenesis model can consume them honestly.

## Invariants

- Authored Soil values remain semantic normalized coordinates, not solver constants.
- Local generated formation occurs before runtime.
- Convexity and concavity remain distinct causes; absolute slope alone is not erosion direction.
- Current first formation model changes mineral fineness but preserves authored organic character.
- Physical composition is generated/model output, not author-entered sand/clay percentages.
- Saxton-Rawls coefficients are isolated behind a replaceable calibrator.
- A prepared spatial Soil field is authoritative per coordinate.
- Runtime Soil does not recalibrate or consult generation algorithms.
- Physical-to-runtime conversion requires explicit physical cell/time scales.
- No material-name switches or coordinate-random Soil physics are reintroduced.

## Current limitations

Not yet modeled:

- parent-rock weathering/mineral release;
- sediment provenance/history;
- climate-driven pedogenesis;
- vegetation/organic accumulation;
- compaction/gravel corrections;
- soil horizons;
- unsaturated conductivity/matric suction curves;
- deep redistribution/groundwater;
- root uptake;
- salinity/chemistry.

## Code and tests

Primary implementation:

```text
world/calibration/soil/SoilFormationGenerationStage.java
world/calibration/soil/ContinuousSoilCompositionCompiler.java
world/calibration/soil/SaxtonRawls2006SoilHydraulicCalibrator.java
world/calibration/soil/*Runtime*Compiler.java
```

Tests protect convex/concave morphology distinction, vertical-translation invariance, exact causal formation, physical hydraulic calibration, generated-field authority and complete bootstrap into runtime `SoilPropertiesLookup`. Visual acceptance includes Causal Soil Formation and Soil Hydraulic Contrast scenes.

## Sources

**Direct physical model:** K. E. Saxton & W. J. Rawls (2006), “Soil Water Characteristic Estimates by Texture and Organic Matter for Hydrologic Solutions”, *Soil Science Society of America Journal* 70, 1569–1578, DOI 10.2136/sssaj2005.0117. The production pedotransfer calibrator implements this model family and isolates its empirical coefficients behind `SoilHydraulicCalibrator`.

**Internal EvoForge design:** semantic Soil coordinates, geomorphic fineness development, the quadratic semantic→composition projection and runtime ownership boundaries are project-specific layers around the cited pedotransfer model.

See [References](../../references.md), [Liquids](liquids.md), [Surface Hydrology](hydrology.md), [Terrain Generation](../world-generation/terrain-generation.md), and [ADR-021](../../decisions/021-world-preparation-and-calibration-boundary.md).

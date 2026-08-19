# Terrain Generation

## In plain language

Terrain generation answers several different questions that are easy to confuse:

1. **Where is the ground surface?** — elevation/morphology.
2. **What solid material is inside each ground column?** — topsoil, subsurface material, sediment or geological bedrock.
3. **What discrete shape does the surface cell use at runtime?** — for example a full block or a cardinal ramp.

EvoForge deliberately keeps these questions separate. A mountain is not “granite because it is a mountain”, a shore is not “sand because it is next to Water”, and a slope is not a ramp merely because a renderer would look nicer that way.

The current accepted terrain baseline is V12 **base morphology**. It creates oceans, landmasses, coasts, broad uplift, ordinary hills/depressions, rolling relief and rugged ridges. Mountain systems, carved rivers/lakes, final geology and final causal surface materials are later stages.

## Current status

### Accepted

- deterministic V1–V12 elevation revision compatibility;
- manually accepted V12 base-terrain appearance;
- Stage 0 V12 split into semantic intent → calibration → recipe → spatial algorithm;
- precise elevation plus discrete `elevationAt` projection;
- local surface morphology facts (slope/convexity/concavity);
- replaceable terrain material generation;
- compact material profiles using semantic material keys;
- surface-shape preparation through a shape palette rather than concrete Shape branching;
- deterministic diagnostics and 2D/3D preview.

### Explicitly provisional

- current geology generation;
- current slope/concavity/drainage terrain-material model;
- current threshold hydrography;
- historical generated initial-Water ordering.

These are typed seams that later stages replace/refine; they are not permission to add feature-specific special cases.

## Part I — V12 base elevation

### Architecture

The accepted V12 path is:

```text
WorldGenesis
  └ WorldGenerationIntent (semantic 0..1 coordinates)
              ↓
     V12LandformCalibrator
              ↓
     V12LandformCalibration
     exact values for this world
              +
     V12LandformRecipe
     fixed V12 model choices
              ↓
  V12LandformElevationAlgorithm
              ↓
        ElevationField
```

`V12BaseTerrainGenerator` is a normal replaceable `ElevationGenerator`. `ElevationGenerationStage` routes `GenerationRevision.V12` through the legacy `V12LandformElevationGenerator` facade, which delegates to the same Stage 0 implementation. Tests lock the facade and replaceable generator to bit-identical output.

The spatial algorithm never reads `WorldGenerationIntent` directly. That separation is important: user meaning is calibrated before synthesis.

### Units

`ElevationField` stores precise elevation in integer subunits:

```text
1 terrain cell = ElevationField.SUBUNITS_PER_CELL
```

Most V12 normalized weights use parts per million:

```text
PPM = 1_000_000
0.50 = 500_000 ppm
```

Sea level for ocean-first revisions is exact subunit elevation `0`.

### Step 1 — exact land/ocean membership

V12 creates two smooth deterministic land-potential fields:

- a coherent landmass field;
- a finer fragmented field.

They are blended by semantic fragmentation:

```text
potential = coherent * (1 - fragmentation)
          + fragmented * fragmentation
```

All horizontal cells are then ranked deterministically by potential. Ties are broken by stable cell index. Calibration computes the target land count:

```text
landCount = round(area * landCoverage)
```

The first `landCount` ranked cells become land; the rest become ocean.

This means land coverage is not an accidental noise threshold. It is preserved to the nearest representable horizontal column.

**Important invariant:** later V12 relief controls do not change the chosen land/ocean membership. They shape the land after the mask exists.

### Step 2 — coast interiority

V12 computes each land cell's distance from ocean using a deterministic two-pass cardinal distance transform. Distance is capped at the V12 coast transition length of **12 cells** and mapped through cubic smoothstep:

```text
s(t) = t²(3 - 2t),   0 <= t <= 1
```

The result is an `interiority` coordinate:

```text
0  near the ocean edge
1  fully inland after the transition distance
```

This coordinate controls both baseline land height and how strongly relief is allowed to appear near the coast.

### Step 3 — calibrated spatial scales

V12 feature wavelengths are primarily measured in **terrain cells**, not as a fraction of the entire world. This is a key reason a larger world contains more hills/depressions instead of stretching the same few blobs into giant plateaus.

The balanced recipe uses:

```text
landform spacing:       20 .. 64 cells (from semantic landformScale)
uplift scale:           max(52, spacing * 2)
ridge scale:            max(34, spacing * 3 / 2)
rolling scale:          max(16, spacing / 2)
rolling detail scale:   max(10, spacing / 3)
```

Landmass coherence is calibrated separately from overall world dimensions because landmass scale is a macro shape control, while ordinary landform size is intentionally scale-stable.

### Step 4 — explicit hills and depressions

Ordinary large hills/depressions are not created only by layering more noise. V12 builds a deterministic feature lattice.

For each feature lattice cell:

- spacing = calibrated `20..64` cells;
- center is jittered by at most about `0.26 * spacing` in each axis;
- radius = `0.65..0.92 * spacing`;
- magnitude = `0.55..1.00` on the normalized relief scale;
- signs are balanced in deterministic `2 x 2` lattice blocks so both positive hills and negative depressions occur;
- a surface sample evaluates the surrounding `3 x 3` features (`neighborhoodRadius = 1`).

For a feature with normalized squared radial distance `d²`, values outside the radius contribute zero. Inside:

```text
falloff = smoothstep(1 - d²)
contribution = signedMagnitude * falloff
```

Contributions are summed and clamped to the centered range `[-1, +1]`.

This produces explicit rounded landforms with predictable physical scale instead of cell-to-cell jitter.

### Step 5 — broad uplift

Broad uplift is a centered, domain-warped smooth value-noise field sampled at the calibrated uplift scale.

It contributes to continent/interior vertical variation but does not decide which cells are land.

### Step 6 — rugged ridge belts

Ridges are derived from the absolute difference between two independent warped fields. Where the two fields are similar, the raw crest signal is strong:

```text
difference = |fieldA - fieldB|
rawRidge   = clamp(1 - 2 * normalizedDifference)
```

The balanced recipe ignores the lower half of this signal (`ridgeCrestThreshold = 0.50`), remaps the remainder through smoothstep, then squares it to concentrate the result into narrower crests.

Ridge strength is multiplied by semantic `ruggedness`, so ruggedness affects ridge prominence in addition to the allowed local slope limit.

### Step 7 — rolling local relief

Rolling relief blends two centered smooth value-noise fields:

```text
rolling = 0.76 * primary + 0.24 * detail
```

The overall contribution is then multiplied by the recipe rolling weight `0.24` and semantic `localRelief`.

This is subordinate texture/undulation; it is not supposed to replace explicit landform structure.

### Step 8 — relief mixture

Before semantic relief strength is applied, the macro centered signal is approximately:

```text
macro = 0.22 * uplift
      + 0.34 * explicitLandform
      + 0.30 * ridge * ruggedness

macro *= semanticRelief
```

Local rolling signal is:

```text
local = 0.24 * rolling * semanticLocalRelief
```

Then:

```text
reliefSignal = macro + local
```

Negative relief is compressed by the balanced recipe factor `0.65` so depressions remain visible without consuming as much positive land height range:

```text
if reliefSignal < 0:
    reliefSignal *= 0.65
```

These are V12 model constants, not user-facing controls. They live in `V12LandformRecipe.balanced()` so a future revision can replace the model instead of scattering tuned literals through spatial loops.

### Step 9 — coast gating and base height

Relief is damped near the coast using:

```text
coastGate = 0.25 + interiority * 0.75
reliefSignal *= coastGate
```

Baseline normalized land height is:

```text
baseHeight = 0.07 + interiority * 0.23
```

The final normalized land height is:

```text
height = clamp01(baseHeight + reliefSignal)
```

It is converted into the positive precise elevation range allowed by `WorldBounds.maxZ`.

Ocean cells receive deterministic negative depths from the non-land rank and available depth below sea level.

### Step 10 — bounded slope relaxation

V12 performs four deterministic cardinal relaxation passes. Pass direction alternates forward/reverse to reduce scan-direction bias.

Semantic ruggedness calibrates the maximum readable land-land cardinal step between:

```text
0.18 cell .. 0.60 cell
```

For a neighboring land pair with elevation difference `Δ`:

```text
if |Δ| <= maximumStep:
    no change
else:
    excess = |Δ| - maximumStep
    distribute excess approximately half to each side
```

Heights stay inside `(0, maximum land height]`.

This is a readability/synthesis constraint, not a claim of physical thermal erosion.

## V12 noise building block

V12 uses project-internal deterministic smooth **value noise**, not FastNoiseLite or an external noise library.

For each lattice square it samples four deterministic 16-bit values and performs separable smooth interpolation. The interpolation coordinate uses:

```text
smoothstep(t) = t²(3 - 2t)
```

“Organic” V12 fields apply domain warp first:

```text
warpScale     = max(8, 2 * sourceScale)
warpAmplitude = max(1 cell, sourceScale / 6)
warpedX/Y     = coordinate + centeredWarpSample * warpAmplitude
```

The source field is then sampled at the warped coordinates.

The purpose of the warp is visual organicity and reduced axis-aligned/value-noise regularity; it is not a physical tectonic simulation.

## Why V12 is not the mountain system

V12 supplies ordinary base morphology. Stage 1 will introduce mountain-specific generated structure over this baseline.

A mountain system needs concepts that V12 intentionally does not own, such as sparse coherent mountain provinces/ranges, foothill organization, peak/ridge hierarchy and separate acceptance controls. Adding those as more V12 noise weights would destroy the Stage 0 separation and make future geology/hydrology harder to reason about.

## Part II — local surface morphology

`world.terrain.surface` derives local geometric facts from precise elevation:

- maximum cardinal-neighbor slope in elevation subunits;
- convexity (locally above neighbors);
- concavity (locally below neighbors).

These are **derived Terrain facts**, not another elevation owner. They are consumed by current material/Soil formation models.

A uniform vertical translation preserves these morphology values because the model depends on local differences rather than absolute world Z.

## Part III — current terrain material profile

### In plain language

Once the surface shape exists, the current preparation model decides whether a solid column contains loose surface/subsurface material, deposited sediment, or geological bedrock.

This model is intentionally small and provisional. It demonstrates causal composition; it is not the final Stage 5 material synthesis.

### Authored semantic profile

The current profile system is composed from:

```text
TerrainProfileDefinition
  └ preset keys
TerrainMaterialSetDefinition
  └ semantic roles -> TerrainMaterialKey
        ↓
TerrainProfileCompiler
        ↓
CompiledTerrainProfile
```

Current built-in capabilities include:

```text
NATURAL_GROUND      -> GROUND_PROFILE
surface deposition  -> SURFACE_DEPOSITION
```

Capability conflicts are explicit rather than resolved by array ordering.

The role vocabulary currently includes surface, subsurface, sediment and bedrock. Material keys are stable semantic keys; runtime `LandscapeDefinitionId` values are resolved only at materialization.

### Ground depth

The current `TerrainMaterialGenerationStage` maximum loose-ground profile depth is **4 cells**.

For maximum local slope `s` in elevation subunits:

```text
slopeSteps = 0,                                      if s = 0
           = 1 + floor((s - 1) / 250_000),          otherwise

groundDepth = clamp(4 - slopeSteps, 0, 4)
```

So increasing slope thins the loose ground profile and can expose geology/bedrock.

The `250_000` subunit increment is a current model constant, not authored content.

### Deposition

The first deposition score uses local concavity, normalized drainage influence and slope:

```text
drainageInfluence = contributingArea * SUBUNITS_PER_CELL / horizontalArea
score = 2 * concavity + drainageInfluence - maximumSlope
```

Deposition is disallowed when:

```text
maximumSlope > 550_000 subunits
```

Otherwise:

```text
score < 120_000       -> no deposition
120_000..549_999      -> 1 sediment cell
score >= 550_000      -> 2 sediment cells
```

When a `SurfaceHydrologyField` is supplied and marks a shoreline, the current compatibility model ensures at least one deposition layer there.

That shoreline behavior is explicitly **provisional**. Stage 5 must use the completed dry hydrographic/depositional/geological causes rather than grow a universal `shore = sediment` shortcut.

### Vertical material lookup

For each XY column the implementation stores compact values:

- discrete `surfaceZ`;
- `groundDepth` byte;
- `depositionDepth` byte;
- optional generated geology field.

`materialAt(x,y,z)` derives the material on demand:

1. if depth from surface is inside deposition depth → semantic `SEDIMENT` material;
2. otherwise, if the ground profile exists:
   - depth `0` → `SURFACE`;
   - deeper but still inside ground depth → `SUBSURFACE`;
3. otherwise, use generated geology material when available;
4. fallback compatibility path uses the profile `BEDROCK` role.

This avoids allocating one material object per solid 3D cell.

## Part IV — generated runtime surface shape

Precise elevation is also fitted to the discrete Terrain shapes that current runtime Geometry can represent.

Conceptually:

```text
precise elevation
      ↓
local surface patch
      ↓
shape-template fit
      ↓
TerrainShapeField
      ↓
materialization / pre-start shape override
```

The generic fitting code compares geometry templates rather than branching on `RampShape`, `FullShape` or future concrete shape classes. A palette/adaptor is the only place where available runtime Shapes are bound to represented surface templates.

Poor fits remain ordinary full-cell terrain. Generation does not create ramps merely to repair Navigation connectivity, and generated terrain is not required to be globally traversable.

## Ownership and interactions

```text
WorldGenesis / intent         requested generation meaning
V12 calibration + recipe      exact V12 generation policy
ElevationField                immutable generated surface fact
SurfaceMorphologyField        derived local geometry fact
GeologyField                  immutable generated rock identity fact
TerrainMaterialField          immutable generated material profile
TerrainShapeField             immutable generated discrete shape profile
        ↓
materialization
        ↓
Landscape/Geometry runtime owners
```

After materialization, Landscape owns mutable runtime Terrain. Generated fields remain provenance/preparation facts and are not synchronized back from later Terrain mutation.

## Invariants

- Same Genesis/revision reproduces the same V12 precise elevations.
- `landCoverage` changes land count through ranking, not through a hidden threshold.
- V12 landform feature scales remain cell-based and do not grow in direct proportion to world dimensions.
- Generic consumers do not branch on concrete Shape classes or material names.
- Material composition depends on causal local/generated facts, not arbitrary coordinate noise.
- Vertical translation of the same local morphology does not change slope/concavity-driven material layering.
- Runtime integer definition IDs never become generated semantic identity.

## Current limitations

V12/current terrain preparation does **not** yet implement:

- Stage 1 mountain ranges/provinces;
- erosional valley/channel/lake carving;
- final coherent geology/stratigraphy;
- caves/open underground volumes;
- final causal sediment/soil/exposed-bedrock synthesis;
- erosion as runtime Terrain mutation;
- biome authority;
- connectivity repair.

## Code and tests

Primary V12 implementation:

```text
world/atlas/V12BaseTerrainGenerator.java
world/atlas/V12LandformCalibrator.java
world/atlas/V12LandformCalibration.java
world/atlas/V12LandformRecipe.java
world/atlas/V12LandformElevationAlgorithm.java
world/atlas/V12LandformElevationGenerator.java   compatibility facade
```

Current material/surface preparation:

```text
world/terrain/surface/*
world/terrain/generation/*
world/preparation/*
```

Representative tests cover deterministic generation, exact land/ocean behavior, scale-aware local relief, V12 visual/readability laws, algorithm substitution, morphology invariance, material causality and generated-world audits.

## Sources

**Internal EvoForge design:** the accepted V12 algorithm is a project-specific deterministic synthesis model. It does not claim to implement a published geomorphology solver.

**Conceptual influence for future stages:** hydrology-oriented procedural terrain by Génevaux et al. (2013) and uplift/fluvial-erosion terrain by Cordonnier et al. (2016) inform the direction of later mountain/hydrography work, not the exact V12 equations above.

See [References](../../references.md), [World Generation](overview.md), [World Atlas](world-atlas.md), [World Materialization](world-materialization.md), [ADR-020](../../decisions/020-terrain-palettes-hide-generated-complexity.md), and [ADR-021](../../decisions/021-world-preparation-and-calibration-boundary.md).

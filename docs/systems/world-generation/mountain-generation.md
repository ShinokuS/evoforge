# V13 Mountain Generation

## In plain language

V13 adds **dedicated mountain structures** above the accepted V12 base landscape without turning mountains into a material, a navigation rule or a concrete Terrain shape.

The important separation is:

```text
V12 base morphology
        ↓
V13 mountain elevation
        ↓
precise ElevationField
        ↓
generic surface-shape fitting
```

The mountain generator decides only the surface height contribution. It does not know about `RampShape`, `FullShape`, rock names, paths, Water or whether the resulting mountain is traversable.

The current V13 result has completed deterministic tests/audits and manual 2D/3D visual acceptance.

## Architecture

V13 follows the project generation law directly:

```text
WorldGenerationIntent.mountains
        ↓ semantic authored character
MountainCalibrator
        ↓
MountainCalibration
        exact values for this world
        +
MountainRecipe
        versioned V13 model choices
        ↓
MountainElevationAlgorithm
        ↓
ElevationField
```

`V13MountainTerrainGenerator` is composition only. It receives four replaceable dependencies:

```text
ElevationGenerator          base terrain
MountainCalibrator          semantic -> world-specific values
MountainRecipe              immutable model policy
MountainElevationAlgorithm  spatial synthesis
```

The standard combination is:

```text
V12BaseTerrainGenerator
MountainCalibrator.standard()
MountainRecipe.balanced()
MountainMorphologyAlgorithm
```

A future mountain algorithm or calibrator can therefore be substituted without teaching orchestration or downstream consumers about its concrete class. If a future model changes durable V13 semantics rather than only its implementation, that is a generation-revision compatibility decision rather than a reason to bypass these contracts.

## Authored mountain intent

`MountainIntent` contains semantic normalized coordinates, not algorithm constants:

| Control | Meaning |
|---|---|
| `abundance` | How much of the available V12 land should be occupied by dedicated mountain structures. |
| `height` | Desired vertical prominence, subject to world size, vertical headroom, authored Scale and readable slope constraints. |
| `scale` | Typical transverse size of an individual mountain structure and its source-lattice spacing. |
| `chaininess` | How strongly a structure stretches along its long axis without changing the authored transverse scale. |
| `peakSharpness` | Mountain profile/slope character; it calibrates readable local rise rather than selecting a concrete surface Shape. |
| `plateausEnabled` | Whether a mountain source may use a plateau summit profile. |
| `plateauProbability` | Per-source probability of choosing that plateau profile when plateaus are enabled. |

The balanced tooling defaults are:

```text
abundance          0.35
height             0.52
scale              0.50
chaininess         0.55
peakSharpness      0.60
plateausEnabled    true
plateauProbability 0.18
```

`MountainIntent.none()` sets abundance to zero and guarantees that the dedicated V13 mountain stage leaves the V12 base elevation unchanged.

## What each control owns

### Abundance owns global mountain coverage

Abundance is not a probability attached blindly to every lattice node. Calibration converts it into `targetCoveragePpm`, the expected share of **V12 land** occupied by mountain structures.

The balanced recipe maps the full authored abundance range into at most `0.75` target coverage. Source selection is discrete and deterministic, so small/fragmented worlds approximate the requested share rather than promising an impossible exact percentage.

This ownership is intentional:

```text
Abundance  -> amount of mountain land
Scale      -> size of each structure
Height     -> vertical prominence
Chaininess -> elongation
```

Increasing Scale or Height must not silently turn ordinary abundance into a mountain carpet.

### Scale owns individual structure size

Scale chooses a transverse half-width from a world-relative range:

```text
4% .. 18% of the limiting horizontal world span
```

with absolute balanced bounds of `8 .. 180` cells.

The deterministic source lattice is based on this **authored Scale width**, not on later Height broadening. Changing Height therefore does not move the candidate lattice for the same seed and Scale.

### Height is constrained by both vertical and horizontal capacity

V13 first generates the ordinary base terrain inside the V12 positive ceiling (`+12` cells in the balanced recipe). Mountain generation may use additional positive world headroom above that ceiling.

Desired mountain height is then bounded by:

1. available vertical headroom;
2. horizontal world size;
3. the calibrated maximum readable cardinal rise;
4. the footprint allowed by Scale.

Height may broaden the Scale-authored transverse width only within the recipe's bounded coupling (up to `1.65x` the authored width in the balanced model). If that still cannot support the requested height, **realized height is capped** instead of expanding one mountain across a continent.

This is why a `64x64` world cannot receive a hundred-cell mountain while a `500x500` world can expose substantially more mountain headroom.

### Chaininess stretches one structure

Chaininess maps the long axis from roughly `1.10x` to `2.05x` the calibrated transverse half-width. It does not create a separate ridge system and does not take ownership of global coverage.

Each source independently varies:

- left transverse width;
- right transverse width;
- negative long-axis length;
- positive long-axis length;
- orientation;
- center position within bounded lattice jitter;
- height within bounded variation.

The resulting structure is therefore asymmetric even though its underlying profile remains simple and bounded.

### Peak sharpness is a geometric slope budget

The balanced recipe calibrates maximum cardinal mountain rise between:

```text
0.22 cell per horizontal cell  (soft)
0.38 cell per horizontal cell  (sharp)
```

So even the sharp end spends more than about `2.6` cardinal horizontal cells per vertical level; softer mountains spend roughly `4.5` cells per level. This budget exists so broad discrete Z bands are born from the source profile rather than repaired after generation.

The mountain generator still has no knowledge of the runtime shape that may later represent a suitable transition.

## Spatial source planning

The standard morphology algorithm builds a deterministic ranked candidate set from a Scale-owned lattice.

For each lattice source it deterministically derives:

```text
jittered center
orientation
left/right widths
positive/negative long axes
height variation
plateau choice
```

Candidates whose centers are not on V12 land are rejected. Remaining candidates are ranked by an addressable deterministic random sample.

The desired source count is derived from:

```text
target mountain land cells
        /
nominal visible footprint per structure
```

Well-separated candidates are preferred first. On small or fragmented land where the quota cannot be satisfied by those centers alone, the same deterministic ranking can fill the remaining quota. Mountain contributions compose with `max`, not addition, so overlaps do not create additive vertical spikes.

## One bounded mountain profile

Each source is one asymmetric elongated hill, not a stack of nested repair hills.

For a cell, world-space displacement is projected onto the source's long/across axes and normalized by the independently varied side lengths. The resulting elliptical radius selects a `layeredHill` profile.

The profile has:

- an eased summit;
- a long near-linear middle slope;
- an eased foot;
- an optional plateau core.

Its derivative bound is owned by `MountainRecipe` and shared with calibration:

```text
standard profile bound = 1.30
plateau profile bound  = 1.60
```

Before rasterization, each source's requested height is capped by the narrowest axis, calibrated cardinal-rise budget and the correct derivative bound. Therefore readability is part of **source construction**, not a post-generation terrace repair pass.

There is deliberately no post-generation mountain smoothing/terrace regularizer in the accepted pipeline.

## Coast interaction

Land/ocean membership is inherited from V12 and never changed by the mountain stage.

Near the ocean/world edge, mountain uplift is limited by a second cardinal-Lipschitz height field. The final mountain uplift is conceptually:

```text
min(raw mountain profile, coastal height cap)
```

Because both fields respect the same cardinal-rise budget, their minimum also preserves that budget. This avoids multiplying two sloped fields, which can accidentally add their gradients and create compressed contour bands.

The coastal transition length grows with the mountain rise it must absorb, with a balanced minimum of 12 cells. The base V12 morphology remains free to form ordinary coastal cliffs; dedicated mountain uplift simply cannot create an uncontrolled vertical wall at the shoreline.

## Composition with V12

The V13 pipeline does not stretch V12 ordinary relief into mountain height.

```text
V13 world bounds
      ↓
construct V12 base Genesis with positive ceiling +12
      ↓
V12BaseTerrainGenerator
      ↓
base ElevationField
      +
calibrated mountain uplift on land only
      ↓
V13 ElevationField
```

Important invariants:

- zero mountain abundance is bit-identical to the V12 base produced for V13;
- mountain generation cannot create or delete land/ocean columns;
- dedicated mountain height may use headroom that ordinary V12 relief does not own;
- `Height`, `Scale`, `Chaininess` and `Abundance` retain separate semantic ownership.

## Generic surface-shape fitting

After precise elevation exists, `TerrainShapeGenerationStage` handles runtime geometry independently.

V13 uses a geometry-only sparse coherent transition policy. It first requires a locally readable cardinal slope and a coherent band of at least three cells. It then keeps only irregular short patches selected by a deterministic hash that includes:

```text
x, y
integer Z layer
transition direction
```

Nearby layers therefore do not repeat one fixed mechanical interval. The V13 sampler deliberately chooses only some geometrically suitable transitions; a mountain is **not required to be completely traversable**.

The mountain algorithm never imports or branches on concrete runtime Shape classes. Available Shapes remain a palette/adaptor concern of generic shape fitting.

## Replaceability rules

A new implementation that preserves the current V13 semantic contract can replace either:

```text
MountainCalibrator
MountainElevationAlgorithm
```

independently through `V13MountainTerrainGenerator` composition.

`MountainRecipe` owns model choices shared across calibration and compatible algorithms. Algorithm-private details that have no second consumer stay private instead of being promoted into a speculative universal framework.

A model with genuinely different authored meaning or durable generated semantics should receive explicit generation-revision treatment rather than silently redefining V13.

## Tests and acceptance evidence

The V13 test set covers the contracts at their owning boundaries:

- `MountainArchitectureTest` — semantic calibration, scale/height/chaininess ownership, world-size height cap, slope budget, coast and plateau controls;
- `MountainAbundanceCoverageTest` — Abundance controls real mountain footprint while Scale does not take ownership of total coverage;
- `MountainMorphologyElevationGenerationTest` — deterministic generated behavior, height/plateau effects, coastline preservation, zero-mountain compatibility and local rise invariants;
- `V13MountainTerrainGeneratorCompositionTest` — base generator, calibrator/recipe and mountain algorithm are independently composable;
- `V13SparseShapeGenerationTest` — the generic fitter produces sparse rather than exhaustive V13 transition geometry;
- `Generated World Audit` — representative headless whole-world generation remains valid.

Automated evidence is supplemented by explicit manual 2D/3D preview acceptance because morphology quality cannot be proven from numerical invariants alone.

## Non-goals

V13 mountains do not implement:

- rock/geology identity;
- erosion or river carving;
- snow/altitude climate consequences;
- guaranteed navigation connectivity;
- concrete runtime Shape selection inside the mountain algorithm;
- a universal formation/ridge/mountain framework;
- runtime terrain evolution.

Those responsibilities remain with their own stages/contracts.

## Code

Primary implementation:

```text
simulation/.../world/genesis/MountainIntent.java
simulation/.../world/atlas/MountainRecipe.java
simulation/.../world/atlas/MountainCalibrator.java
simulation/.../world/atlas/MountainCalibration.java
simulation/.../world/atlas/MountainElevationAlgorithm.java
simulation/.../world/atlas/MountainMorphologyAlgorithm.java
simulation/.../world/atlas/V13MountainTerrainGenerator.java
simulation/.../world/terrain/shape/TerrainSurfaceTargetSamplers.java
```

See [Terrain Generation](terrain-generation.md), [World Generation](overview.md), [World Genesis](world-genesis.md), [ADR-011](../../decisions/011-world-generation-algorithm-contracts.md) and [ADR-021](../../decisions/021-world-preparation-and-calibration-boundary.md).

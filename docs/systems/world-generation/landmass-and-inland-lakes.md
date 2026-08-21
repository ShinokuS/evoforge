# Continental Domain and Inland Lakes

## In plain language

This part of world generation answers two different questions:

1. **Where are the continents and islands?**
2. **Which broad inland lowlands become real lakes?**

Those questions deliberately have different owners. `Fragmentation` may split one large land mass into several islands or continents, but it is not a hidden “lake count” control. Lakes appear only where the generated land itself contains enough broad, low, interior space for a useful lake.

A generated lake is not painted blue on top of finished terrain. It is real negative-Z terrain whose water surface is the shared sea-level plane `Z = 0`. Mountains therefore never need a special “do not draw over lake” repair rule: by the time mountains run, the lake is already water in the elevation field.

## Current status

The V14 continental-domain replacement and V15 inland-lake model are **implemented, deterministic and manually accepted**.

The following are deliberately absent from this completed slice:

- drainage directions and watersheds;
- river graphs;
- river/valley carving;
- F4 hydrology diagnostics;
- local lake water surfaces above sea level;
- runtime finite-Water filling.

Those concerns require their own later contracts. This page must not be read as evidence that rivers or drainage topology already exist.

## Mental model

```text
WorldGenerationIntent
        │
        ├── Land / Continent scale / Fragmentation
        │          ↓
        │  Regularized continental-domain algorithm
        │          ↓
        │   ocean / continental support
        │          ↓
        └────── ordinary V12 land relief
                   ↓
          broad inland-lowland analysis
                   ↓
          regularized lake footprint
                   ↓
        selected cells cross below Z = 0
                   ↓
              V13 mountains
                   ↓
           accepted V14 bathymetry
                   ↓
       inland-only lake-bottom refinement
                   ↓
              V15 ElevationField
```

The important direction is **one way**. A later step consumes an earlier fact; it does not silently reopen the earlier owner's responsibility.

## Ownership and boundaries

### Continental-domain owner

`LandmassSilhouetteAlgorithm` owns:

- the external ocean footprint;
- continent/island placement;
- macro connectivity and separation;
- the structural meaning of `Fragmentation`.

It does **not** own:

- ordinary relief;
- mountains;
- lakes;
- lake depth;
- rivers;
- geology or materials.

The accepted implementation is `RegularizedGraphLandmassSilhouetteAlgorithm`.

### Inland-lake footprint owner

`InlandLakeDomainAlgorithm` owns only the selected inland-water membership before mountains. Its standard implementation is `TerrainLowlandInlandLakeDomainAlgorithm`.

It reads real generated continental relief. It may reject a requested amount of water if the available geometry cannot support a sufficiently broad lake. **Shape validity wins over quota.**

### Shore materialization owner

`InlandLakeShoreConditioningAlgorithm` converts only selected lake cells to negative elevation. The standard implementation does not construct a synthetic dry ridge around the lake; non-lake terrain is preserved.

### Lake-bottom owner

`InlandLakeBathymetryAlgorithm` owns depth **inside already accepted inland-water membership**. It cannot expand or shrink the lake.

## Continental-domain algorithm

### Why the old grid-growth approach was rejected

Earlier prototypes grew regions through fixed lattice neighbours. Even when control points were jittered, the hidden lattice remained visible as square/Voronoi-like structure, sharp appendages and artificial connections.

The accepted algorithm instead uses an irregularized local geometric graph and a regularized binary land phase.

### Step 1 — irregular structural controls

Control sites begin near a coarse lattice only so neighbour discovery remains local and cheap. Each site is deterministically jittered in continuous `(x,y)` space.

For a candidate neighbour `j` of site `i`, geometric distance is

```text
d(i,j) = sqrt((xj - xi)^2 + (yj - yi)^2)
```

The nearest local geometric neighbours are retained and weighted approximately by inverse distance:

```text
w(i,j) = (1 / d(i,j)) / Σk(1 / d(i,k))
```

The lattice is therefore a spatial index, not the topology of the generated continent.

### Step 2 — separated geographic nuclei

The first nucleus is chosen deterministically from generation randomness. Later nuclei use farthest-point-style separation: each new seed prefers the site whose distance from the already chosen set is largest, with bounded deterministic jitter to avoid repetitive symmetry.

`Fragmentation = 0` resolves to one structural nucleus. Increasing Fragmentation permits more separated nuclei and a finer structural graph.

### Step 3 — broad geographic forcing

Each nucleus receives a broad axis and bounded elongation. A low-frequency geographic field perturbs the large form. When several nuclei compete, Fragmentation increases broad sea pressure near owner boundaries.

This is deliberately **macro geography**, not coast noise.

### Step 4 — regularized phase

The binary land phase repeatedly diffuses over the geometric graph and is then re-thresholded to preserve its requested structural volume.

Conceptually:

```text
old binary phase
      ↓ diffuse across geometric neighbours
smooth local occupancy
      + broad geographic forcing
      ↓ rank all candidate controls
keep exactly the required number as LAND
```

Diffusion makes excessive perimeter, thin appendages and isolated teeth expensive. Global re-thresholding prevents the regularization from simply shrinking all land away.

The implementation is inspired by threshold-dynamics / phase-regularization ideas, but EvoForge's fixed-volume graph formulation and geographic forcing are project-specific.

### Step 5 — continuous coast field

The accepted graph phase is interpolated to raster cells with a compact-support Wendland-style kernel. A weak broad warp may deform the coast, followed only by bounded near-shore relaxation. The silhouette is the full owner of wet/dry membership; ordinary V12 relief no longer contributes a hidden percentage of coastline decisions.

## What the semantic controls mean

### Land

`Land` means requested dry-land coverage as closely as finite-world support permits. V15 reserves expected lake area in the continental budget so adding inland water does not silently redefine the control.

### Continent scale

Controls the structural scale of major land masses. It is not a “smoothness” slider.

### Fragmentation

Controls macro separation/connectivity of major land masses.

Good causal chain:

```text
Fragmentation
    ↓
continent/island geometry
    ↓
available deep continental interior
    ↓
where lakes can physically fit
```

Bad coupling, intentionally forbidden:

```text
Fragmentation → hard-coded lake count
```

## Inland-lake placement

### Why lakes are generated from terrain lowlands

The accepted visual principle is the same one that made the earlier accidental lakes look natural: the lake must agree with surrounding relief instead of being carved through arbitrary finished terrain.

The algorithm therefore searches existing dry continental terrain for broad low interior support.

A cell can only enter the candidate set when it satisfies hard conditions such as:

- it is currently dry;
- it is sufficiently far from the external coast;
- its source elevation is below the calibrated maximum;
- the surrounding lowland is broad enough.

### Broad lowland field

Raw cell height is too noisy for placement. The algorithm computes a broad local elevation field and selects the lowest eligible support up to a bounded occupancy fraction.

The exact smoothing implementation is an implementation detail; the contract is that lake placement follows regional lowland structure rather than individual low cells.

### Width regularization

A candidate water mask is measured with an 8-direction chamfer distance field.

Cardinal movement has distance `1000`; diagonal movement has distance `1414`, approximating `sqrt(2) × 1000`.

That approximation gives a cheap grid distance much closer to Euclidean geometry than Manhattan distance and is used to answer questions such as:

> “Is this part of the candidate genuinely wide, or is it only a one-cell corridor?”

Thin corridors and appendages are removed. Reconstruction is allowed only around a sufficiently broad core, so the result cannot recover a one-cell tendril merely to satisfy an area target.

### Minimum meaningful lake

A V15 lake is not allowed to exist if it cannot honestly support a deep profile.

Balanced policy requires roughly:

```text
minimum significant radius ≈ 10 cells
minimum significant depth  = 5 Z
```

If a lowland is too small, the correct result is **no lake**, not a 1–2 Z puddle pretending to be a geographic lake.

## Z = 0 shoreline rule

For generated oceans and current generated lakes:

```text
water surface = Z = 0
water terrain < 0
land terrain  > 0
```

`Z0InlandLakeShoreConditioningAlgorithm` pushes selected lake membership only just below zero before downstream bathymetry. It does not lower a one-cell dry ring around the water. This is why accepted lakes no longer create an artificial shoreline ridge.

## Lake-bottom algorithm

The lake bottom follows the same broad principle as accepted ocean bathymetry:

> **distance and available room away from shore determine how much depth is allowed.**

There are no authored circular pits, no random per-cell holes and no special “deep basin object”.

### Distance-to-shore envelope

For every inland-water component the algorithm builds a local padded bounding box and computes an 8-direction distance transform only inside that region.

Let

```text
d(x,y) = approximate distance from lake cell (x,y) to dry shore
```

Balanced policy uses approximately two horizontal cells of inward room per additional full Z level.

A simplified form is:

```text
level(x,y) = 1 + floor(max(0, d_eff(x,y) - 1 cell) / 2 cells)
```

then

```text
depth(x,y) = min(targetDepth, level(x,y))
elevation(x,y) = -max(existingDepth, depth(x,y))
```

The production implementation uses fixed scaled integer distances and clamps against world-floor headroom.

### Broad asymmetry

A pure distance transform tends to produce a perfectly centered funnel. V15 adds one deterministic broad directional bias per lake.

Crucially, the bias may only **reduce effective inward distance** on one broad side:

```text
d_eff(x,y) <= d(x,y)
```

Therefore asymmetry can move/flatten the deep core, but can never invent unsupported extra depth or a hidden pit.

### Depth invariants

- shoreline membership never changes during lake-bottom refinement;
- boundary-connected ocean is preserved by the inland-only refinement;
- significant lakes reach at least `5 Z` depth;
- narrow water is rejected earlier instead of being forced into a trench;
- cardinal neighbouring depth levels differ by at most `1 Z`;
- there is no cell-scale depth noise;
- depth terraces remain broad.

## Land-budget compensation

If a user asks for dry-land coverage `L`, turning some continental cells into lakes must not quietly reduce the meaning of `Land`.

The V15 coordinator therefore reserves expected lake coverage before the expensive continental synthesis.

Normal path:

```text
requested final dry land
      + predicted lake budget
              ↓
one continental-base synthesis
              ↓
select lake footprint
              ↓
if prediction matches: reuse the same base
```

If shape constraints make the real lake footprint materially different, the coordinator falls back to an exact compensation pass. Correct semantics are preferred over speed.

This predictive path is an optimization of orchestration, not a change in lake shape.

## Determinism

All random-looking structural choices use `GenerationRandom` addressed by semantic purposes and stable spatial/ordinal inputs. Generation does not depend on mutable RNG call order.

Changing the order of unrelated consumers must therefore not change an existing V15 world.

## Invariants

Across refactors, preserve these laws:

1. landmass owns external ocean/continent topology; lakes do not rewrite it arbitrarily;
2. `Fragmentation` means land-mass separation/connectivity, not lake abundance or coast noise;
3. lake placement is terrain-derived and may refuse invalid geometry;
4. no generated geographic lake exists unless it can support the significant-depth contract;
5. current generated ocean/lake surface is `Z = 0`;
6. lake-bottom refinement cannot change wet/dry membership;
7. mountains run after lake membership and therefore naturally respect water;
8. accepted V14 ocean bathymetry remains independently owned;
9. no river/drainage implementation is hidden inside these classes;
10. algorithms/calibrators/recipes remain replaceable behind narrow contracts.

## Interactions with other systems

### Mountains

V13 consumes the water-aware base. A lake is already negative terrain, so mountain synthesis does not need lake-specific carving or cleanup.

### V14 bathymetry

The common V14 bathymetry pass creates the accepted submerged baseline. The inland-only refinement then adjusts only lake depth.

### Future drainage and rivers

Future drainage may read final elevation and existing standing-water membership. It must not make lakes exist by repainting this accepted layer.

A later river-network owner should decide route/topology; a separate channel-morphology owner should decide terrain incision. That future design is intentionally **not implemented in this slice**.

## Current limitations / deliberately absent

- all generated standing-water surfaces currently share `Z = 0`;
- no perched/high-altitude lake surfaces;
- no drainage-driven lake filling;
- no river inlets/outlets yet;
- no erosion history is simulated;
- lake placement is a deterministic procedural model, not a geological lake-formation simulator;
- current algorithms are still expected to receive a dedicated large-world performance pass before 10,000×10,000 becomes the normal target.

## Code and tests

Primary implementation entry points:

```text
LandmassSilhouetteAlgorithm
RegularizedGraphLandmassSilhouetteAlgorithm
LandmassSilhouetteCalibrator / LandmassSilhouetteRecipe

InlandLakeDomainAlgorithm
TerrainLowlandInlandLakeDomainAlgorithm
InlandLakeDomainCalibrator / InlandLakeDomainRecipe

InlandLakeShoreConditioningAlgorithm
Z0InlandLakeShoreConditioningAlgorithm

InlandLakeBathymetryAlgorithm
DistanceProfileInlandLakeBathymetryAlgorithm
InlandLakeBathymetryRecipe

V15InlandLakeBaseTerrainGenerator
V15InlandLakeTerrainGenerator
ElevationGenerationStage
```

Representative executable evidence:

```text
RegularizedGraphLandmassSilhouetteAlgorithmTest
OceanicLandmassGenerationTest
TerrainLowlandInlandLakeDomainAlgorithmTest
InlandLakeBathymetryAlgorithmTest
V15InlandLakeTerrainGeneratorTest
Generated World Audit
```

Manual visual acceptance remains part of the contract for geographic shape quality.

## Sources

### Borgefors — chamfer distance transforms

Gunilla Borgefors, **“Distance transformations in digital images.”** *Computer Vision, Graphics, and Image Processing* 34(3), 344–371, 1986. DOI: https://doi.org/10.1016/S0734-189X(86)80047-0

**Algorithm lineage.** EvoForge uses the same family of cheap weighted-grid distance approximations (cardinal/diagonal costs) for shape width and shoreline-distance reasoning. The exact policies around lake selection and depth are EvoForge-specific.

### Merriman–Bence–Osher — threshold dynamics

Barry Merriman, James Bence, Stanley Osher, **“Diffusion Generated Motion by Mean Curvature.”** UCLA CAM Report 92-18, 1992.

**Conceptual influence.** Diffuse-and-threshold perimeter regularization motivated the accepted land-phase strategy. EvoForge uses a deterministic irregular graph, fixed-volume re-thresholding and geographic forcing; it is not a direct implementation of the original continuous model.

### Red Blob Games — polygon/map generation

Amit Patel, **“Polygon Map Generation for Games.”** https://www.redblobgames.com/maps/mapgen2/

Amit Patel, **“Mapgen4.”** https://www.redblobgames.com/maps/mapgen4/

**Conceptual influence.** These references informed the separation between coarse structural geography, irregular spatial support and continuous/raster map shaping. EvoForge's landmass and lake algorithms are project-specific.

### Internal EvoForge design

Lake-domain selection, minimum-meaningful-depth policy, predictive land-budget compensation, broad one-sided lake-floor asymmetry and the exact regularized graph landmass composition are **internal EvoForge designs**. Production code/tests are their primary source.

See also [Documentation Guide](../../guides/documentation.md), [World Generation](overview.md), [Terrain Generation](terrain-generation.md), [V13 Mountain Generation](mountain-generation.md), [V14 Standing-Water Bathymetry](bathymetry-generation.md), [References](../../references.md), [ADR-011](../../decisions/011-world-generation-algorithm-contracts.md), [ADR-022](../../decisions/022-green-checkpoint-development.md) and [ADR-023](../../decisions/023-strict-modular-architecture.md).

# V14 standing-water bathymetry

## Status

**World-generation Stage 2A is complete and manually accepted.**

V14 owns the underwater shape of standing-water regions whose 2D submerged footprint already exists in the accepted V13 terrain. It does not create lakes, seas or oceans and it does not place Water.

The accepted contract is:

```text
accepted V13 land + submerged membership
        ↓
standing-water coastal bathymetry
        ↓
broad deep-water structures where room exists
        ↓
V14 ElevationField
```

For every horizontal column:

- V13 `elevation >= 0` remains land and its elevation is preserved exactly;
- V13 `elevation < 0` remains part of the same standing-water footprint;
- only the negative-Z bottom geometry is re-authored by V14.

The V13 negative amplitude was never treated as accepted bathymetric truth. It supplied standing-water membership only.

## Ownership and composition

The standard V14 composition is:

```text
V13MountainTerrainGenerator
        ↓
accepted land / submerged footprint
        ↓
BathymetryCalibrator
        +
BathymetryRecipe
        ↓
StructuredBathymetryAlgorithm
        ├─ BathymetryMorphologyAlgorithm
        │    accepted coast / shallow-to-deep morphology
        └─ DeepBathymetryStructureAlgorithm
             broad deep-interior basins and highs
        ↓
ElevationField
```

The important replaceable seams are:

- `BathymetryCalibrator` — resolves exact operating limits from world dimensions and negative-Z headroom;
- `BathymetryRecipe` — owns versioned bathymetry model policy;
- `BathymetryElevationAlgorithm` — spatial synthesis contract;
- `StructuredBathymetryAlgorithm` — composition only; it does not own morphology formulas;
- `BathymetryMorphologyAlgorithm` — coastal and ordinary standing-water bottom morphology;
- `DeepBathymetryStructureAlgorithm` — independent deep-interior structural relief.

Deep-interior work was deliberately separated after the coastal morphology was manually accepted. Later structural tuning therefore could not silently rewrite the accepted shore.

## Coastal and littoral morphology

The accepted coast is smooth, readable and causally responsive to adjacent broad land relief.

Important properties:

- shoreline distance supplies the universal shallow-to-deep room/depth envelope;
- broad adjacent land morphology may support a steeper ocean-connected coastal descent;
- coastal influence is evaluated over a world-scaled context rather than from isolated cells;
- competing coast influences blend through connected water rather than switching discrete nearest-land owners;
- land barriers stop that propagation;
- narrow bays and corridors remain shallow when there is insufficient horizontal room for clean depth;
- coastal descent remains monotone offshore where the causal coast model applies;
- the accepted maximum coastal fall remains below half a Z cell per cardinal XY step;
- final elevation is not repaired by a generic downstream smoothing pass.

This avoids the rejected artifacts observed during development: sharp Voronoi-like wedges in bays and visually noisy one-cell Z bands.

## Deep-water structure

A large/deep water body is not required to collapse into one mathematical bowl whose deepest point is determined only by distance from shore.

`DeepBathymetryStructureAlgorithm` activates only when a submerged connected component has sufficient depth and horizontal clearance. Small, shallow or narrow components remain on the accepted base bathymetry.

Eligible deep interiors receive several broad deterministic structures:

- local basins / depressions;
- local highs;
- broad saddles and transitions produced by their interaction.

There is no high-frequency noise or per-cell random perturbation. Structural cores are packed from actual available interior room and kept away from the protected coastal band.

Each structure authors an independent slope-bounded depth surface:

- basin surfaces compose through `max(depth)`;
- high ceilings compose through `min(depth)`.

This is intentionally different from adding a perturbation to the accepted bottom. `max/min` composition lets independently slope-bounded surfaces change the deep morphology without adding a second gradient on top of the existing coastal gradient.

The balanced model uses only part of the global readable cardinal slope budget for those structural surfaces, remains world-floor bounded, and preserves standing-water membership.

## Scale and depth

Bathymetry depth is not a fixed constant such as `lakeDepth = 20`.

The calibrator constrains available depth from:

- horizontal world dimensions;
- negative-Z world headroom;
- readable cardinal fall budget;
- the horizontal room available inside each connected submerged component.

Therefore wider water bodies can naturally support greater depth, while small worlds and narrow water bodies cannot receive absurdly deep bottoms merely because vertical bounds happen to allow it.

No `OCEAN / SEA / LAKE` enum is required for this behavior. The morphology responds to actual scale, depth and interior room. A large deep lake can therefore receive broad interior structure while a cramped sea inlet remains shallow.

## What V14 does not own

Standing-water bathymetry does **not** own:

- Water quantity or runtime liquid behavior;
- river networks or river carving;
- drainage topology policy;
- geology or rock identity;
- sediment/material selection;
- runtime navigation policy;
- concrete Terrain Shape types.

Generic shape fitting still runs later from final precise elevation. Finite initial Water remains a later world-generation milestone.

## Preview presentation

The development preview can hide the fake sea-level Water plane and inspect real negative-Z bottom geometry directly.

`Z contrast` is presentation-only. The accepted behavior is sign-aware:

- positive Z retains the existing terrain lightening/contrast behavior;
- negative Z uses the actual generated submerged depth range and becomes progressively darker with increasing depth;
- `Z contrast = 0` returns the submerged bottom to one neutral blue-gray shade.

This color mapping changes no generated elevation fact.

## Evidence

The owning tests cover, among other things:

- exact land preservation;
- exact submerged-footprint preservation;
- deterministic replay;
- world-floor bounds;
- readable cardinal slope bounds;
- broad coast continuity and barrier awareness;
- narrow-water shallowness;
- absence of the rejected sharp competing-coast wedge behavior;
- broad deep regions both above and below the old equal-clearance bowl;
- several deep-interior structures rather than one forced center;
- shallow/narrow water remaining unchanged by the deep pass;
- independent algorithm composition;
- negative-Z preview contrast.

The final implementation passed repository CI and Generated World Audit before documentation closeout. Manual visual acceptance is recorded in [V14 bathymetry visual acceptance](../../journal/acceptance/v14-bathymetry-visual-acceptance.md).

## Acceptance boundary

Stage 2A closes the shape and bottom morphology of the currently accepted standing-water bodies. Later Stage 2 work must not incidentally regenerate their shoreline footprint or accepted bathymetry.

The next separate concern is **Stage 2B — drainage and basin topology**, followed by river-network generation and river/valley carving. A future need to create a genuinely new standing-water body would require an explicit new contract rather than silently reopening V14.

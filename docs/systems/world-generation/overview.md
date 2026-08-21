# World Generation

## In plain language

World generation decides **what exists when a new world begins**. Runtime simulation decides what happens after that world starts living.

EvoForge deliberately separates a human request such as “large continents, moderate fragmentation, sparse tall mountains” from the mathematical constants used to produce it. Semantic intent is calibrated into world-specific operating values, then consumed by replaceable deterministic algorithms.

## Current status

The accepted elevation chain is now:

```text
V12 ordinary terrain
   ↓
V14 continental-domain owner around the V12 relief model
   ↓
V15 inland-lake footprint at Z = 0
   ↓
V13 mountain morphology over the water-aware terrain
   ↓
V14 standing-water bathymetry
   ↓
V15 inland-only lake-bottom refinement
   ↓
final V15 ElevationField
```

Completed and manually accepted:

- **Stage 0 / V12** — ordinary terrain architecture and scale-stable landforms;
- **Stage 1 / V13** — structural mountains;
- **Stage 2A / V14** — standing-water bathymetry;
- **Stage 2B / V14–V15** — organic continental domain plus terrain-derived inland lakes.

Drainage topology and rivers are **not part of the completed Stage 2B code**. Experimental drainage/F4 scaffolding used during development was removed before Stage 2B was closed.

See [Continental Domain and Inland Lakes](landmass-and-inland-lakes.md) for the complete current algorithms, formulas, invariants and sources.

## Canonical generation architecture

```text
human-authored semantic meaning
            ↓
validate / compile meaning
            ↓
world-specific domain calibration
            ↓
versioned model recipe
            ↓
replaceable generation algorithm
            ↓
immutable typed generated fact
            ↓
preparation / materialization
            ↓
ordinary SimulationRuntime ownership
```

### Intent owns meaning

Examples:

```text
Land             = requested dry-land amount
Continent scale  = macro geographic scale
Fragmentation    = separation/connectivity of major land masses
Mountain height  = desired prominence
```

A semantic control must not secretly become a different control in a later algorithm. In particular, `Fragmentation` does not mean lake count, coastline noise or river density.

### Calibrators own world-specific numbers

A calibrator combines semantic intent with world size/headroom and produces exact operating values. There is no universal GodCalibrator.

### Recipes own model policy

Stable model choices that are neither authored meaning nor world-specific calibration live in immutable recipes. Examples include `V12LandformRecipe`, `LandmassSilhouetteRecipe`, `MountainRecipe`, `BathymetryRecipe`, `InlandLakeDomainRecipe` and `InlandLakeBathymetryRecipe`.

### Algorithms own spatial synthesis

Algorithms receive typed calibrated/model inputs and return typed generated facts. Orchestrators only compose these owners.

## Deterministic provenance

`WorldGenesis` is the immutable generation birth certificate:

```text
WorldSpec
masterSeed
GenerationRevision
RngRevision
WorldGenerationIntent
```

Generation randomness is addressable rather than call-order driven. A sample is derived from semantic stage/purpose identifiers plus stable coordinates/ordinals, so unrelated call reordering does not silently alter a world.

See [World Genesis](world-genesis.md).

## Stage 0 — V12 ordinary terrain **[COMPLETE]**

V12 owns the ordinary land-surface height model: broad uplift, hills/depressions, rolling relief, rugged structure and bounded readable slopes. Its algorithms remain independently replaceable behind the elevation contract.

The current continental-domain owner is composed around this relief model rather than allowing V12 local noise to reopen shoreline membership.

See [Terrain Generation](terrain-generation.md).

## Stage 1 — V13 mountains **[COMPLETE]**

V13 owns dedicated mountain elevation contribution. `Abundance`, `Scale`, `Height`, `Chaininess` and `Peak sharpness` retain separate meanings. Mountains consume the already water-aware terrain and do not own coast/lake membership.

See [V13 Mountain Generation](mountain-generation.md).

## Stage 2A — V14 standing-water bathymetry **[COMPLETE]**

V14 re-authors submerged depth while preserving the accepted wet/dry footprint. Near-shore morphology and deep-water structure have separate replaceable owners. Narrow water remains shallow when there is insufficient room; deep structure remains broad and avoids cell-scale noise.

See [V14 Standing-Water Bathymetry](bathymetry-generation.md).

## Stage 2B — continental domain and inland lakes **[COMPLETE]**

Stage 2B closed two prerequisites that had previously been entangled:

1. **continental domain** — an organic regularized graph phase owns external ocean/continent topology;
2. **inland lakes** — broad real continental lowlands may become negative-Z lake domains before mountains.

Current generated ocean and lake surfaces share `Z = 0`. Significant lakes are rejected unless their geometry can honestly support at least a 5-Z deep profile. Lake-bottom refinement follows shoreline distance with broad deterministic asymmetry and cannot change shoreline membership.

See [Continental Domain and Inland Lakes](landmass-and-inland-lakes.md).

## Ownership matrix

| Concern | Owner | Must not own |
|---|---|---|
| continent/island topology | landmass silhouette | relief, mountains, lakes, rivers |
| ordinary terrestrial height | V12 relief | macro water membership |
| inland lake footprint | inland-lake domain | mountains, river routing, lake depth |
| mountain elevation | V13 mountain owner | coast/lake membership |
| general submerged depth | V14 bathymetry | wet/dry membership |
| inland lake depth refinement | lake bathymetry | shoreline membership, ocean rewrite |
| future drainage | not implemented | terrain mutation |
| future river network | not implemented | lake identity, mountain generation |
| future channel carving | not implemented | river-route ownership |

The dependency direction is forward. A downstream owner may react causally to upstream facts but must not silently rewrite their responsibilities.

## What comes next

The next world-generation semantic work starts only after the separate large-world performance and preview-UI work requested for the accepted Stage 2B baseline.

Future hydrography should be introduced as new independently accepted slices:

```text
final accepted terrain
        ↓
drainage / catchments
        ↓
river network
        ↓
channel / valley morphology
```

Routing and terrain incision should remain separate owners. No unfinished river implementation is retained in the completed Stage 2B production code.

## Development protocol

World-generation work follows [Green Checkpoint Development](../../decisions/022-green-checkpoint-development.md):

```text
one contract
   ↓
one independently meaningful component
   ↓
focused evidence
   ↓
green commit
   ↓
manual visual gate when appearance is semantic
```

Strict replaceable boundaries follow [ADR-023](../../decisions/023-strict-modular-architecture.md).

Documentation follows the repository [Documentation Guide](../../guides/documentation.md): each non-trivial implementation must be explained both precisely and in accessible language, including diagrams, formulas/symbol definitions, invariants, limitations, tests and source classification where relevant.

## Anti-patterns

Do not introduce:

- a god generator/calibrator/context for unrelated domains;
- downstream repair passes that hide an upstream ownership error;
- `Fragmentation` branches for lake count, coast noise or river density;
- random painted lakes/rivers not represented by generated terrain/facts;
- cell-scale noise as a substitute for broad geomorphology;
- one-cell depth/height layers where the accepted model requires readable bands;
- Water/runtime state as a source of dry generation geometry;
- unfinished “future” frameworks in production merely because a later stage may need them.

## Sources

The architecture itself is EvoForge-specific. External algorithm lineage and conceptual influences are documented on the owning pages rather than used to imply physical fidelity that the implementation does not have.

See [References](../../references.md), [Continental Domain and Inland Lakes](landmass-and-inland-lakes.md), [Terrain Generation](terrain-generation.md), [V13 Mountain Generation](mountain-generation.md), [V14 Standing-Water Bathymetry](bathymetry-generation.md), [ADR-011](../../decisions/011-world-generation-algorithm-contracts.md), [ADR-022](../../decisions/022-green-checkpoint-development.md) and [ADR-023](../../decisions/023-strict-modular-architecture.md).

# Project Context

This page is the fastest way to reconstruct **what EvoForge is, what is already true in code, what must not be broken, and what should happen next**. It is written for humans returning after a long break and for future AI-assisted development sessions that do not have access to old chat history.

## EvoForge in one minute

EvoForge is a deterministic simulation of a persistent three-dimensional world. Complex behavior should emerge from explicit physical and behavioral rules instead of being faked in presentation code or hard-coded for particular content.

```text
authored meaning
      ↓
validated / calibrated data
      ↓
authoritative systems and generated facts
      ↓
observable world state
      ↓
visualizer and diagnostics
```

The renderer is an observer. It never decides what is physically true.

## Repository map

```text
simulation/   pure Java authoritative simulation and generation code
core/         libGDX visualizer, scenarios and presentation adapters
lwjgl3/       desktop launcher
assets/       authored definition/presentation data
docs/         canonical explanations, decisions, guides and journal
```

`simulation` must not depend on libGDX/presentation code.

## Central architectural rules

Every mutable fact has one authoritative owner. Systems exchange narrow typed facts/capabilities rather than sharing one giant mutable world object.

Generation follows the same rule:

```text
semantic intent
      ↓
domain calibration
      +
versioned model recipe
      ↓
replaceable deterministic algorithm
      ↓
immutable generated fact
      ↓
preparation / materialization
      ↓
runtime owner
```

Meaning, calibration, model policy, spatial synthesis, generated facts and runtime ownership are separate concerns.

### Strict modularity

Every non-trivial subsystem remains decomposable into small cohesive blocks with clear owners, typed inputs/outputs and one-way dependencies. Independently meaningful algorithms/calibrators/planners/selectors are replaceable through composition. Orchestrators compose; they do not accumulate spatial model policy.

Use abstraction at real semantic boundaries and simple concrete implementation inside a boundary. Do not manufacture interfaces for private helpers without an independent consumer.

See [ADR-023: Strict modular architecture and replaceable boundaries](decisions/023-strict-modular-architecture.md).

### Green checkpoints

World-generation work advances one independently meaningful concern at a time. A green automated checkpoint plus explicit manual visual acceptance is required before a visually semantic layer is called complete.

See [ADR-022: Green checkpoint development](decisions/022-green-checkpoint-development.md).

## Documentation is part of the implementation

The repository [Documentation Guide](guides/documentation.md) is canonical and already encodes the required standard:

- explain the system first in language understandable without programming knowledge;
- show ownership/lifecycle diagrams;
- document the actual algorithm, not only class names;
- define every symbol in formulas;
- record invariants, limitations, interactions, representative tests and code entry points;
- cite primary/reusable sources when an external algorithm/model is actually used;
- explicitly label conceptual influence vs direct implementation vs internal EvoForge design;
- never rely on old chat history to reconstruct current semantics.

A feature is incomplete if code is green but normative documentation cannot explain it independently.

## Current stable capabilities

The integration line contains working foundations for deterministic time/scheduling, Definitions, spatial ownership, Terrain/Geometry/Navigation, occupancy and movement, deterministic 3D pathfinding, autonomous agents, finite resources, finite liquids/Water/Soil hydrology, observer-only visualization and deterministic world-generation provenance.

The current accepted world-generation elevation baseline is **V15**.

## Accepted world-generation baseline

### V12 — ordinary terrain

V12 owns ordinary terrestrial height morphology: broad uplift, ordinary hills/depressions, rolling relief, rugged structures and bounded readable slopes.

### Accepted continental domain around V12 relief

The standard `LandmassSilhouetteAlgorithm` is `RegularizedGraphLandmassSilhouetteAlgorithm`.

It owns:

- external ocean vs continental support;
- continent/island macro geometry;
- macro separation/connectivity controlled by `Fragmentation`.

It does not own relief, mountains, lakes, rivers, geology or materials.

The accepted implementation uses an irregular geometric control graph, separated nuclei, broad geographic forcing, fixed-volume graph phase regularization and continuous coast-field materialization. The old compact frontier-growth fallback has been removed.

### V13 — mountains

V13 owns dedicated mountain elevation contribution. Mountain controls retain independent semantics (`Abundance`, `Scale`, `Height`, `Chaininess`, `Peak sharpness`). Mountains consume already accepted water membership rather than changing it.

See [V13 Mountain Generation](systems/world-generation/mountain-generation.md).

### V14 — standing-water bathymetry

V14 re-authors submerged depth while preserving wet/dry membership. Near-shore bathymetry and deep-interior structure remain independent replaceable responsibilities. Deep structure is broad/deterministic rather than cell-scale noise.

See [V14 Standing-Water Bathymetry](systems/world-generation/bathymetry-generation.md).

### V15 — terrain-derived inland lakes

V15 adds independently owned inland lakes without returning lake ownership to the landmass algorithm.

```text
accepted continental relief
        ↓
broad interior-lowland selection
        ↓
8-direction footprint regularization
        ↓
reject candidates too small for meaningful depth
        ↓
selected cells cross below Z = 0
        ↓
V13 mountains
        ↓
V14 bathymetry
        ↓
inland-only broad asymmetric depth refinement
        ↓
final V15 ElevationField
```

Current generated oceans and lakes share water-surface level `Z = 0`.

Important accepted lake invariants:

- lake placement follows real broad continental lowlands;
- `Fragmentation` does not directly own lake count;
- one-cell tendrils/corridors are rejected;
- there is no synthetic one-block dry shoreline ridge;
- a generated geographic lake must be wide enough for a genuine depth profile of at least `5 Z`;
- lake-bottom refinement cannot change wet/dry membership;
- bottom depth is governed by room away from shore and broad deterministic asymmetry, not authored pits/noise;
- cardinal depth changes remain at most `1 Z` per cell;
- boundary-connected ocean remains separately owned.

See [Continental Domain and Inland Lakes](systems/world-generation/landmass-and-inland-lakes.md).

## What Stage 2B does not contain

The completed Stage 2B branch intentionally contains **no unfinished river/drainage implementation**.

Removed before completion:

- experimental drainage-basin/Priority-Flood production scaffolding;
- provisional standing-water route/spill graphs;
- unfinished river prerequisites presented as if they were current facts;
- F4 hydrology diagnostic renderer;
- separate inland-water 3D meshes for obsolete local spill-level lakes.

Those experiments were useful during design but are not part of the accepted current architecture.

## Immediate next work

Before adding new hydrography semantics, the accepted baseline receives two engineering passes.

### 1. Large-world generation performance

Target practical default generation of at least `10,000 × 10,000`, with larger worlds kept architecturally possible.

The performance pass must study asymptotic/memory behavior across the whole accepted pipeline and may replace algorithms/representations only with measured evidence and no visual quality regression.

### 2. World-generation preview UI

After performance work, align the generation UI with scenario style, group controls, add tooltips, place Generate prominently with `G` hotkey, and move generation off the render thread behind progress/stage reporting.

The UI remains observer/control plumbing; it never becomes a generation owner.

See [Roadmap](roadmap.md).

## Future hydrography direction

Future drainage/rivers restart from the accepted final elevation with new typed contracts rather than reviving deleted scaffolding.

Intended causal chain:

```text
accepted final terrain
        ↓
drainage / catchments
        ↓
river network
        ↓
channel / valley morphology
```

Routing and terrain incision should be separate owners. A river must eventually exist as real generated geometry, not as a visual overlay.

## Rules future work must preserve

1. **Determinism:** same authoritative inputs and compatible generation revision produce the same result.
2. **One owner per fact:** no duplicate truth in visualizer/bootstrap/helpers.
3. **Observer independence:** camera/visibility never changes simulation/generation fidelity.
4. **Typed replaceable algorithms:** orchestration depends on contracts, not concrete implementations.
5. **Semantic authoring:** authors describe meaning; domain calibration resolves exact operating values.
6. **No concrete-content branching in generic code:** no permanent `mountain -> granite`, `river -> sand` shortcuts.
7. **Headless evidence + manual visual acceptance:** tests prove invariants; visual quality is reviewed explicitly.
8. **No premature universal framework:** shared abstractions require real independent consumers.
9. **Green checkpoints:** one independently meaningful concern before the next semantic concern.
10. **Strict modular architecture:** package/file structure visibly mirrors ownership.
11. **Abstraction at boundaries, simplicity inside.**
12. **Accepted-stage protection:** downstream work consumes accepted facts instead of rewriting them to hide downstream defects.
13. **Documentation completeness:** current behavior must be recoverable from normative docs without chat history.

## Fast recovery path

Read in this order:

```text
project-context.md
architecture.md
roadmap.md
systems/world-generation/overview.md
systems/world-generation/landmass-and-inland-lakes.md
```

Then inspect the owning code/tests named by the System page.

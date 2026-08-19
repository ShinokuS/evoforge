# Roadmap

The Roadmap answers two questions: **what is already real in EvoForge, and what is intentionally next?** Detailed mechanic behavior belongs in [Systems](systems/); architectural rules belong in [Architecture](architecture.md).

## Current position

The project has completed its simulation foundations, first autonomous-agent vertical slice, first finite-Water/surface-hydrology vertical slice, and **Stage 0 of the world-generation rebuild**.

The next world-generation implementation work is:

> **Stage 1 — Mountain Systems**

Stage 1 begins only from the accepted V12 base terrain and the Stage 0 generation architecture. It must not be smuggled into V12 as more tuning/noise.

For a fast reconstruction of why, see [Project Context](project-context.md).

## Completed simulation foundations

### Core runtime and data ownership

- Object identity separated from mechanics.
- Immutable authored Definitions separated from mutable runtime state.
- Deterministic discrete simulation time.
- Scheduler + ProcessScheduler execution infrastructure.
- Explicit `SimulationAssembly`, `SimulationRuntime` and read-only `SimulationView` composition boundary.
- Optional inclusive finite `WorldBounds` with one shared closed-geometry rule outside the configured box.

### Space, terrain and traversal

- Authoritative object Spatial ownership and coordinate index.
- Landscape/Terrain ownership and coordinated terrain mutation.
- Geometry contract with `FullShape`, cardinal `RampShape`, free-space facts and transition algebra.
- Structural Navigation derived from Geometry rather than duplicated topology.
- Actor-independent `TransitionCost` with a conservative global lower bound suitable for admissible search.
- Exclusive Occupancy with destination reservations.
- Deterministic timed adjacent Movement, fractional time carry and completion-time revalidation.
- Deterministic resumable 3D A* Pathfinding with traversal revisions and derived hierarchy/reachability preflight.
- Long-range `MoveTo` built over ordinary one-edge Movement, including safe route cancellation.
- Water-aware terrestrial traversal through definition-driven wading limits, advisory route filtering and authoritative Movement revalidation.

### Autonomous agents and living-world slice

- Generic agent definitions/capabilities and deterministic decision traces.
- Physical orientation.
- 3D Vision feeding sensor-neutral Perception.
- Source-neutral opportunities with explicit `InteractionSite`s.
- Declarative interaction reach.
- Coordinate-free unknown-source Search using local visual sweep and relative exploration.
- Finite Consumable Stock separated from physiological effect.
- Definition-driven scheduled Growth/regrowth.
- Generic scheduled Need progression for open `NeedId`s.
- Provider-owned timed opportunity use with completion revalidation.
- Hunger + Thirst comparison on one deterministic fixed-point Utility scale.
- Finite Water drinking through the same generic opportunity/use boundaries rather than Water-specific AI logic.
- Stable committed intents and deterministic recovery from blocked/failed opportunity sites.
- Multi-agent tests proving exclusive actors do not overlap while competing for finite opportunities.
- Living Cow Meadow integration slice: Hunger + Thirst, plants/regrowth, edge lake, rain-created puddles and ordinary MoveTo execution.

### Liquids, Water and surface hydrology

- Generic finite free-liquid representation with one constituent identity per free cell.
- Water as a typed facade over generic liquid ownership.
- Deterministic local liquid redistribution with exact volume conservation, hydraulic-head reasoning, bounded relaxation and dormant active-frontier scheduling.
- Generic material-owned surface-retention reserve that prevents endless shallow same-level thin-film runoff while preserving vertical falling.
- Generic Soil-retained liquid composition sharing finite pore capacity.
- Definition/calibration-driven Soil hydraulic properties.
- Precipitation targeting the shared exposed sky surface.
- Terrain-first infiltration, excess free Water, run-on infiltration and finite evaporation.
- Cyclic/periodic scenario forcing plus generated hydro-climate forcing through the same Water/Soil runtime systems.
- Hydrology diagnostics, Rain Cycle acceptance, Geometry/Ramp stress scenarios and water-aware inspector values.

### Presentation and diagnostics

- Surface-first visualizer with explicit `SURFACE`, `INTERIOR` and `DEBUG_SLICE` views.
- Cell-centric inspection and bounded developer overlays.
- Presentation adapters remain observer-only and do not own simulation mechanics.
- Generated-world diagnostics and explicit warm-up/audit harnesses.
- Performance-sensitive world-generation preview with deterministic 2D/3D rendering and LOD behavior.

## Completed world-generation Stage 0

Stage 0 established the generation architecture and protected the accepted V12 base terrain.

### Deterministic provenance

- `WorldGenesis` records generation inputs and revisions.
- `GenerationRandom` provides call-order-independent deterministic samples addressed by semantic stage/purpose + coordinates + ordinal.
- generated fields are immutable typed facts rather than a shared mutable generation bag.

### Replaceable generation stages

- Atlas generation is composed through typed interfaces such as elevation, geology, drainage and hydrology generators.
- `WorldAtlasGenerator` orchestrates dependencies without owning individual domain algorithms.
- generated-world preparation is similarly compositional through `WorldPreparationAlgorithms`.

### V12 base terrain normalization

The manually accepted V12 appearance was preserved while the implementation was separated into:

```text
semantic WorldGenerationIntent
        ↓
V12LandformCalibrator
        ↓
world-specific V12LandformCalibration
        +
versioned V12LandformRecipe
        ↓
V12LandformElevationAlgorithm
        ↓
ElevationField
```

V12 currently provides ocean/land membership, coherent landmasses/coasts, broad uplift, balanced hills/depressions, rolling relief and rugged ridges. It is **base morphology**, not final mountains, rivers, geology or surface materials.

### Stage 0 documentation closeout

The documentation was reorganized and reconciled as part of Stage 0 acceptance so future work can recover context from the repository itself rather than chat history:

- Systems grouped by conceptual ownership.
- Development Journal separated into entries/design/acceptance/audits.
- ADR numbering/format standardized.
- Project Context added as the explicit recovery page.
- normative pages rewritten to explain both the human model and exact technical model.
- external algorithm/model sources centralized and linked from owning systems.

## Accepted baseline relationships

### Runtime hydrology chain

```text
precipitation
    ↓
Soil retention first
    ↓
excess free Water
    ↓
run-on infiltration + local liquid flow
    ↓
finite evaporation
    ↓
Water-aware MoveTo advice
    ↓
authoritative Movement revalidation
```

Terrain, Geometry, Liquids, Soil and Movement remain separate authoritative owners/contracts even though they interact.

### Autonomous-agent chain

```text
Needs (Hunger + Thirst)
        ↓
perceived opportunities
        ↓
common deterministic Utility
        ↓
committed intent
        ↓
MoveTo + provider-owned use lifecycle
        ↓
finite resource mutation
```

The current agent architecture deliberately has no Water-specific decision system or plant-specific movement system.

## World-generation milestone sequence

The next sequence is locked unless implementation evidence proves it needs revision. Any change must update the canonical [World Generation](systems/world-generation/overview.md) document in the same PR.

### Stage 1 — Mountain Systems **[NEXT]**

Generate sparse coherent mountain provinces/ranges, foothills, ridges and peaks over the accepted base morphology.

Requirements:

- mountain geometry is independent from rock identity;
- zero/low mountain intent preserves ordinary V12 character;
- user controls remain semantic and are calibrated into model values;
- generated mountain facts are observable in a dedicated preview;
- vertical capacity is explicit rather than globally inflating ordinary relief.

### Stage 2 — Dry hydrography and carving

Derive drainage/watersheds/basins and create dry river hierarchy, valleys, channels, lake bowls, outlets and readable shores.

The world remains completely dry. A river must be real geometry, not a blue overlay.

### Stage 3 — Coherent layered geology

Replace placeholder geology with coherent formations/strata and only the deposit bodies required by the real geology model.

Rock identity remains separate from mountain shape.

### Stage 4 — Caves

Generate coherent underground voids using available morphology/geology/hydrological causes. Caves remain a replaceable algorithm and do not become geology ownership.

### Stage 5 — Causal surface layers/material synthesis

Combine final dry morphology, hydrographic/depositional facts, geology and calibrated semantic material/Soil definitions to decide surface/subsurface/sediment/exposed-rock composition.

No universal `river => sand` or `mountain => granite` shortcuts.

### Stage 6 — Complete dry-world acceptance

Before any initial Water is generated, visually and deterministically accept:

- land/ocean morphology;
- mountains;
- river valleys/channels and lake bowls;
- geology/strata/deposits;
- caves;
- surface/subsurface material structure.

### Stage 7 — Finite initial Water fill

Fill already-created oceans/lakes/channels with finite Water. Generation owns initial placement only; ordinary runtime liquid/hydrology systems own Water afterwards.

### Stage 8 — Runtime handoff audit

Verify generated facts materialize exactly once and no generator/bootstrap remains a second owner after tick 0. This closes the world-generation milestone.

## Provisional code that must not become accidental final design

The repository currently contains useful typed infrastructure with provisional algorithms:

- current drainage/hydrography is analytical topology, not final erosion/carving;
- current `GeologyGenerationStage` is placeholder geology;
- current generated initial-Water ordering is historical compatibility infrastructure and moves behind complete dry-world acceptance;
- current terrain material slope/deposition model is an early slice, not final surface synthesis.

Future stages should replace/narrow these behind their contracts, not extend them with increasingly specific special cases.

## Separate future research milestones

These are deliberately **not** part of the current world-generation sequence:

- persistent Belief / Memory and landmark/topological navigation;
- richer senses such as hearing and smell;
- richer fluid physics: pressure/inertia/viscosity detail, derived bodies, erosion and more complex boundary behavior;
- tectonic/depositional history beyond what Stage 3 genuinely needs;
- biome/ecology potential derived from the completed physical world;
- coherent vegetation communities/populations and richer plant lifecycles;
- resources beyond deposits required by geology;
- settlements, societies, economy and population generation.

A roadmap name is not permission to build dormant infrastructure. The first real consumer defines the needed contract.

## Deferred presentation work

- richer X-ray/build tools beyond explicit Debug Slice;
- advanced roof/structure/large-object occlusion volumes;
- partial optical transmission such as glass/smoke/foliage;
- lighting as its own mechanic;
- richer material-transition art and larger object/creature/building assets;
- wider worldgen preview layers only when a real generated fact requires inspection;
- broader chunk/dirty presentation caches only after profiling.

## Deferred traversal work

- mid-edge cancellation/reactive wake-up;
- falling, climbing, jumping, swimming and flying;
- actor-specific locomotion affinities beyond current Water wading;
- automatic waiting/replanning inside MoveTo;
- persistent route caches;
- more advanced incremental/JPS/space-time/multi-agent planners;
- background pathfinding threads;
- yielding, displacement, pushing and explicit deadlock-resolution policies;
- group/flow-field navigation.

## Deferred storage/runtime infrastructure

- chunk/region dimensions and loaded/unloaded/generated state;
- streaming/generated-world bound policy beyond explicit finite `WorldBounds`;
- packed coordinate representations;
- persistence/network boundaries;
- authoritative multithreaded mutation.

A future streaming model must distinguish **unknown/unloaded** from **empty/open**. The current finite world box is containment, not streaming.

## Activation rule

A deferred idea becomes active only when at least one concrete reason exists:

- a production consumer cannot proceed without it;
- an invariant/correctness test proves the current contract insufficient;
- a representative workload measures a real performance problem;
- persistence/network/tooling requires stable external representation;
- a vertical slice exposes ownership ambiguity.

“Could be useful later” is not enough.

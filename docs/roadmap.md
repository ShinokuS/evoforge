# Roadmap

The Roadmap answers two questions: **what is already real in EvoForge, and what is intentionally next?** Exact mechanic behavior belongs in [Systems](systems/); global rules belong in [Architecture](architecture.md).

## Current position

Completed major slices include:

- deterministic simulation/runtime foundations;
- autonomous-agent living-world slice;
- finite Water / Soil / surface-hydrology runtime slice;
- world-generation **Stage 0 — architecture stabilization + accepted V12 base terrain**;
- world-generation **Stage 1 — accepted V13 structural mountains**;
- world-generation **Stage 2A — accepted V14 standing-water bathymetry**.

World-generation Stage 2 is now **in progress**. The next implementation work is:

> **Stage 2B — Drainage and basin topology**

Stage 2A already closed the shape/bottom morphology of the standing-water bodies inherited from accepted V13 terrain. Stage 2B starts from accepted V14 elevation and derives real dry drainage/watershed/basin topology before river-network generation and carving. Finite initial Water remains later.

For fast context recovery see [Project Context](project-context.md), [World Generation](systems/world-generation/overview.md), [V13 Mountain Generation](systems/world-generation/mountain-generation.md) and [V14 Standing-Water Bathymetry](systems/world-generation/bathymetry-generation.md).

## Completed simulation foundations

### Runtime and ownership

- object identity separated from mechanics;
- immutable authored Definitions separated from mutable runtime state;
- deterministic discrete simulation time and scheduled processes;
- explicit `SimulationAssembly`, `SimulationRuntime` and read-only `SimulationView` boundaries;
- optional inclusive finite `WorldBounds` with one shared closed-geometry outside rule.

### Space, Terrain and traversal

- authoritative Spatial ownership/index;
- Landscape/Terrain ownership and coordinated terrain mutation;
- Geometry with full cells, cardinal ramps, free-space facts and transition algebra;
- Navigation derived from Geometry;
- transition costs, occupancy/reservations and deterministic timed Movement;
- resumable deterministic 3D A* / `MoveTo`;
- Water-aware terrestrial traversal with advisory planning and authoritative completion revalidation.

### Autonomous agents

- generic definitions/capabilities and deterministic decision traces;
- orientation, Vision/Perception and source-neutral opportunities;
- finite consumables/regrowth and generic Needs;
- common deterministic Utility and committed intents;
- generic provider-owned timed use lifecycle;
- living Cow Meadow vertical slice with Hunger + Thirst, plants, Water and movement.

### Liquids, Water and Soil

- generic finite free liquids;
- Water facade over generic liquid ownership;
- deterministic local redistribution/conservation;
- surface-retention reserve;
- Soil-retained liquid composition and hydraulic calibration;
- precipitation, infiltration, run-on and evaporation;
- cyclic/generated hydro-climate forcing through the same runtime systems.

### Presentation and diagnostics

- observer-only visualizer with surface/interior/debug views;
- deterministic scenario/audit tooling;
- generated-world 2D/3D preview with LOD;
- explicit visual/performance acceptance gates where automated tests cannot prove quality.

## Completed world-generation Stage 0 — V12 baseline

Stage 0 established the canonical generation architecture:

```text
semantic intent
    ↓
world-specific calibration
    +
versioned recipe
    ↓
replaceable spatial algorithm
    ↓
immutable generated fact
```

For V12 this is:

```text
WorldGenerationIntent
        ↓
V12LandformCalibrator
        ↓
V12LandformCalibration + V12LandformRecipe
        ↓
V12LandformElevationAlgorithm
        ↓
ElevationField
```

V12 owns ocean/land membership, coherent landmasses/coasts, broad uplift, ordinary hills/depressions, rolling relief and rugged ridges. It remains the accepted **ordinary base morphology**.

Stage 0 also normalized typed generation/preparation seams, deterministic provenance and documentation/recovery structure.

## Completed world-generation Stage 1 — V13 Mountain Systems

V13 introduces dedicated mountain semantics without pushing mountain policy back into V12:

```text
WorldGenerationIntent.mountains
        ↓
MountainCalibrator
        ↓
MountainCalibration + MountainRecipe
        ↓
MountainElevationAlgorithm
        ↓
ElevationField
```

Accepted Stage 1 properties:

- `Abundance` owns expected mountain coverage on V12 land;
- `Scale` owns individual structure size and deterministic source spacing;
- `Height` owns prominence but is bounded by world size, vertical headroom, Scale and readable slope;
- `Chaininess` owns long-axis elongation;
- structures are deterministic and asymmetric;
- broad Z bands are created by the source profile rather than repaired after generation;
- mountain overlaps use `max` composition rather than additive spikes;
- V12 coastline membership is preserved;
- coast interaction uses a slope-compatible cap;
- mountain generation knows nothing about rock identity or concrete runtime Shapes;
- V13 generic shape fitting keeps sparse irregular coherent transitions and does not guarantee traversal connectivity;
- calibration and spatial algorithms are independently replaceable;
- deterministic tests, Generated World Audit and manual 2D/3D acceptance are complete.

See [V13 Mountain Generation](systems/world-generation/mountain-generation.md).

## Completed world-generation Stage 2A — V14 Standing-Water Bathymetry

V14 preserves accepted V13 land and standing-water membership while re-authoring only negative-Z bottom geometry.

Accepted Stage 2A properties:

- no lake/sea/ocean body is created or deleted;
- every V13 land elevation remains exact;
- the standing-water footprint/shoreline membership remains exact;
- shallow coastal morphology is smooth and readable;
- broad adjacent coastal relief may causally influence ocean-connected descent;
- competing coasts blend broadly instead of producing nearest-owner wedges;
- narrow water remains shallow when there is insufficient horizontal room;
- large/deep water can contain several broad basins, highs and saddles rather than one forced center bowl;
- deep structure is deterministic and uses no cell-scale noise;
- bathymetry remains world-floor and readable-slope bounded;
- negative-Z preview contrast darkens progressively with depth but is presentation-only;
- Water, river generation, geology, materials, navigation and concrete runtime Shapes remain outside bathymetry ownership;
- deterministic tests, Generated World Audit and manual visual acceptance are complete.

See [V14 Standing-Water Bathymetry](systems/world-generation/bathymetry-generation.md).

## World-generation milestone sequence

### Stage 0 — Architecture stabilization / V12 normalization **[COMPLETE]**

Protected the ordinary landscape baseline and established typed semantic/calibration/algorithm ownership.

### Stage 1 — V13 Mountain Systems **[COMPLETE]**

Added accepted dedicated mountain morphology over V12 through independent semantic/calibration/spatial boundaries.

### Stage 2 — Dry hydrography and carving **[IN PROGRESS]**

Stage 2 is deliberately split into independently accepted concerns.

#### Stage 2A — Standing-water bathymetry **[COMPLETE]**

Accepted V14 standing-water geometry now owns the bottom morphology of the existing V13 submerged footprint. The lake/sea/ocean footprint and bathymetry are protected input for later Stage 2 work.

A future genuinely new standing-water body requires a new explicit contract; later drainage/river work must not silently regenerate the accepted V14 shoreline or bottom.

#### Stage 2B — Drainage and basin topology **[NEXT]**

Starting from accepted V14 elevation:

- derive/reconcile drainage directions and terminals;
- derive watersheds/catchments and closed basins;
- establish deterministic basin/outlet topology required by the river network;
- remain completely dry;
- do not reopen accepted standing-water bathymetry.

#### Stage 2C — River network

- derive real river hierarchy from drainage/catchment facts;
- establish tributaries, confluences and outlets;
- keep network semantics separate from spatial carving where independently meaningful.

#### Stage 2D — River / valley carving

- carve readable dry valleys and channels from the accepted topology/network;
- preserve protected V14 standing-water shore/bottom behavior except at explicitly modelled river mouths/outlets whose contract requires interaction;
- remain completely dry.

A river must exist as generated geometry, not as a blue overlay. Topology/morphology tests and manual dry-geometry acceptance are required before Stage 2 closes.

### Stage 3 — Coherent layered geology

Replace placeholder geology with coherent formations/strata and only the deposit bodies genuinely required by the real geology model. Rock identity remains separate from mountain shape.

### Stage 4 — Caves

Generate coherent underground voids behind a replaceable algorithm using available morphology/geology/hydrological causes.

### Stage 5 — Causal surface/subsurface material synthesis

Combine final dry morphology, hydrographic/depositional facts, geology and calibrated semantic material/Soil definitions. No permanent `river -> sand` or `mountain -> granite` shortcuts.

### Stage 6 — Complete dry-world acceptance

Accept:

```text
land/ocean base
mountains
standing-water bathymetry
dry drainage / rivers / valleys
geology/deposits
caves
surface/subsurface materials
```

with deterministic audits, visual acceptance and representative profiling.

### Stage 7 — Finite initial Water fill

Fill already-created oceans/lakes/channels with finite Water. Generation owns initial placement only; ordinary runtime liquid/hydrology systems own Water afterwards.

### Stage 8 — Runtime handoff audit

Verify generated facts materialize exactly once and no generator/preparation/bootstrap object remains a second runtime owner.

## Provisional code that must not become accidental final design

- current drainage/hydrography is analytical/provisional rather than final Stage 2B/2C topology;
- current `GeologyGenerationStage` is placeholder geology;
- current generated initial-Water ordering is compatibility infrastructure and remains later than complete dry-world acceptance in the canonical plan;
- current terrain material slope/deposition model is an early slice, not final Stage 5 synthesis.

Later stages replace/narrow these behind typed contracts instead of extending them with increasingly specific feature-name branches.

## Separate future research milestones

Deliberately outside the current world-generation sequence:

- persistent Belief/Memory and landmark/topological navigation;
- richer senses;
- richer fluid physics and runtime erosion;
- tectonic/depositional history beyond what Stage 3 genuinely needs;
- biome/ecology potential from the completed physical world;
- coherent vegetation communities/populations;
- settlements, societies, economy and population generation;
- persistence/network/multithreaded authoritative mutation.

A roadmap label is not permission to build dormant infrastructure. The first real consumer defines the contract.

## Deferred infrastructure/presentation

Examples include richer X-ray/build tools, advanced occlusion/lighting, broader presentation caches, streaming/chunk state, packed coordinates, persistence and network boundaries. Activate them only from concrete correctness/consumer/performance evidence.

## Activation rule

A deferred idea becomes active only when at least one concrete reason exists:

- a production consumer cannot proceed without it;
- an invariant/correctness test proves the current contract insufficient;
- a representative workload measures a real performance problem;
- persistence/network/tooling requires a stable external representation;
- a vertical slice exposes ownership ambiguity.

“Could be useful later” is not enough.

## Development rule

Every roadmap item is implemented through [Green Checkpoint Development](decisions/022-green-checkpoint-development.md): one stated contract, one independently meaningful component, focused evidence, a green checkpoint, then the next block. Visible generation stages additionally require manual acceptance before merge.

# Roadmap

The Roadmap answers two questions: **what is currently accepted, and what is the next work that is allowed to begin?** Exact mechanics belong in `systems/`; global laws belong in `architecture.md`.

## Current architecture checkpoint

PR #132 established ADR-026 semantic-capability architecture and PR #133 completed the final semantic cleanup. Current repository laws remain unchanged:

- `:simulation`, `:core` and `:lwjgl3` are the only code/Gradle modules;
- world truth belongs to independent semantic concepts rather than presentation, stages or first consumers;
- pages/chunks/tiles/caches are representation only;
- one mutable fact has one authoritative owner;
- camera/visibility never changes simulation truth;
- deterministic results do not depend on query order, cache history, rendering, thread scheduling or whether a location is being observed;
- performance optimization must preserve semantics rather than replace distant simulation with a different world model;
- **every finite EvoForge world is surrounded by ocean: land and later terrain uplift may never reach or be clipped by the logical coordinate boundary.**

## World-generation checkpoint

Continuum Stages 0–5 are accepted. **Stage 5 — Macro Ocean + Geophysical Skeleton was manually accepted in PR #135.** `world/geophysics` owns the macro-geophysical cause; Continuum remains neutral addressing/materialization/cache infrastructure.

PR #136 attempted Stage 6 as a noise/refinement-driven continuous heightfield plus tile-level LOD repairs. Manual inspection rejected that implementation. It is closed and must not be revived by tuning amplitudes, coastline noise, color bands or tile thresholds.

The replacement direction is recorded by ADR-027 and the canonical Continuum Development Plan.

### Stage 5 preparation before Stage 6

The accepted Stage 5 visual macro-elevation remains the intrinsic macro model, but its original public contract exposed future stages only to one scalar `elevationAt(x,y)`. That is insufficient for structure-first Stage 6: a later terrain algorithm would otherwise have to invent continental margins and regional structural causes independently.

A **separate Stage 5 follow-up PR** therefore expands `world/geophysics` with a bounded deterministic structural read capability and restores the finite-world ocean-boundary invariant that existed in the useful legacy generation line.

That preparation exposes consumer-neutral geophysical facts such as:

- broad continental support versus deep ocean support;
- macro-margin influence;
- stable macro structural-region identity;
- local shared-boundary orientation;
- convergent/divergent/transform-like boundary regime and strength;
- finite-world boundary-ocean influence.

The finite-world model has a **non-zero hard outer ocean belt on all four sides plus a broader smooth inward transition**. This is not a cosmetic render mask and not an authored preset. The hard belt is geophysical truth and every later Genesis terrain stage must preserve it below the shared sea datum. Continents, islands, plateaus and mountain systems therefore exist *inside* the surrounding ocean and can never terminate because the map ended.

The Stage 5 preparation must **not** generate mountains, rivers, lakes, detailed terrain or runtime physics. Those remain later owners/stages.

### Replacement Stage 6 — structural terrain + proven local morphology

Stage 6 remains the next terrain checkpoint. Its final surface is built in two distinct roles:

```text
Stage 5 geophysical causes
        ↓
large/regional geomorphic structure
        ↓
mountain belts / plateaus / basins / regional relief
        ↓
local surface synthesis
        ↓
continuous Terrain Z
```

The large-scale structure is new. It determines **where** mountain systems, plateaus, basins, uplands and plains belong and how they are oriented.

The local surface synthesis deliberately reuses the successful ideas from the retired V12 terrain line without restoring its dense architecture or version lineage:

- explicit balanced hills and depressions rather than uniform noise everywhere;
- rolling local relief as a subordinate layer;
- physical feature scales expressed in terrain-cell/world units, so a larger world contains more landforms instead of stretching the same form;
- bounded slope/curvature policy preventing one-block Z chatter;
- calm plains are a valid terrain state;
- local morphology is conditioned by its owning regional structure.

V13's useful elongated/asymmetric mountain profiles may inform the profile of individual ridge children, but isolated V13-style mountain spots are not the global mountain model. Major mountains are explicit connected belts/ridge families first.

Stage 6 has one non-negotiable boundary condition inherited from Stage 5: where `boundaryOceanInfluence == 1`, the resulting Terrain surface remains below sea datum. Natural coast refinement may happen inward of that belt, but no Stage 6 feature may create land at the logical world edge.

Stage 6 acceptance requires simultaneous quality at three scales:

1. country/continental view — coherent landmasses, broad plains/plateaus/basins and readable mountain systems, visibly surrounded by ocean rather than clipped by map edges;
2. regional view — several Songs-of-Syx-scale landscape regions with believable mountain/coast/plain relationships;
3. cell-near view — balanced local terrain comparable to the accepted strengths of V12, with no one-block Z noise.

Later rivers, forests or attractive materials must not be used to hide weak Stage 6 morphology.

### Stage 7 — Genesis drainage and depression topology only

Stage 7 **analyzes** the Stage 6 surface. It does not run real runtime erosion or water physics.

It determines deterministic Genesis topology such as:

- drainage direction/graph;
- watershed hierarchy;
- accumulated upstream support;
- depressions and nested depression hierarchy;
- saddles/spill points;
- connectivity toward the surrounding ocean/outflow.

The guaranteed outer ocean supplies a stable terminal receiving boundary for drainage. The output is topology and causal information for later river/lake generation.

### Stage 8 — coupled Genesis rivers, lakes and surface adjustment

Stage 8 converts Stage 7 topology into initial-world geometry:

- river-channel centerlines and channel hierarchy;
- lake/depression basins and spill relationships;
- connection of river systems to the ocean;
- bounded valley/channel/lake-basin adjustment of the generated surface.

A solver may use erosion-like, stream-power-like or relaxation mathematics **only as a Genesis construction algorithm**. It does not represent simulated historical years and does not introduce runtime erosion physics.

Real later-time erosion, landslides, water-driven terrain mutation and direct digging/construction are separate Runtime mechanics. When they eventually exist, current Terrain truth is conceptually:

```text
immutable/reconstructable Genesis terrain
        +
authoritative sparse persistent Terrain changes
```

Storage cost therefore follows the amount of changed world rather than total potential world area. The exact persistence representation belongs to the later runtime/persistence stages.

### Stages 9–10

Stage 9 proves hierarchical regional refinement, overlapping-request identity and seam-free lazy realization. A requested area may be rebuilt after cache eviction and must reproduce exactly the same natural structures.

Stage 10 materializes exact integer XYZ Terrain from the accepted continuous surface. This is where the visualizer stops showing only a projected future grid and can inspect authoritative exact cells. Exact XYZ materialization must preserve the hard Stage 5 outer-ocean belt as ocean-side terrain rather than rounding isolated boundary cells back into land.

## Simulation-scale law for later ecology/agents

Large object counts must not be optimized by changing reality outside the camera.

If an animal/object exists as an individual simulation entity, it remains that entity and follows the same laws anywhere in the world. Observation is irrelevant. Future performance work may use:

- data-oriented storage;
- exact elapsed-time advancement where mathematically valid;
- event/wake scheduling;
- batched algorithms;
- sparse indexes and persistence;

but not a camera-driven replacement of distant living individuals by a statistically different simulation.

Concepts that are genuinely fields/aggregates by ontology (for example a grass-cover biomass field rather than every blade as an object) may of course use field/aggregate authorities. That decision is semantic, not visibility-based.

## Visualization is part of world-generation development

The long-term F2 Inspector is a first-class acceptance instrument, not a decorative final viewer.

It must eventually support one continuous inspection path from world scale to cells and expose generation causes/layers explicitly:

```text
macro geophysics
boundary-ocean constraint
structural regions/boundaries
coast/landmass support
mountain belts / plateaus / basins
ridge children / local landforms
continuous Terrain Z
later: drainage / watersheds / river channels / lake basins
later: exact XYZ
later: runtime Terrain changes
```

Presentation requirements:

- whole-world view must visibly prove ocean on every logical edge for representative seeds/profiles;
- ordinary pan cannot change simulation truth or semantic detail;
- no visible incomplete checkerboard/fallback state;
- expensive generation never blocks the render thread;
- nearby detail must already be prefetched/available enough that normal pan/zoom feels continuous;
- zoom reveals real additional world structure, not a stretched raster;
- close inspection can expose projected cell heights before Stage 10 and exact cells after Stage 10;
- diagnostics must explain which generator/structure contributed to suspicious terrain.

The exact rendering implementation remains presentation infrastructure and may use clipmaps, bounded pages, overscan and asynchronous preparation as appropriate.

## Continuum guardrails

Do not reintroduce:

- giant full-world authoritative rasters;
- camera-driven authoritative fidelity;
- chunks/pages/tiles as natural geography;
- independent feature painters that can contradict one another;
- V16/V17/V18 whole-generator lineages;
- arbitrary solver constants as authored Definitions;
- universal mutable `WorldCell` / `WorldFact` stores;
- real runtime erosion/water history merely to construct Genesis rivers;
- distant-living-object semantic downgrades driven by observation;
- land, islands or terrain uplift that touch or are clipped by a finite logical world boundary.

## Development rule

Every stage remains a green checkpoint:

```text
owner + invariant
      ↓
small coherent implementation
      ↓
focused correctness / determinism / seam evidence
      ↓
architecture checks
      ↓
representative scale/performance evidence
      ↓
world-to-cell visualization where applicable
      ↓
documentation reconciliation
      ↓
explicit manual acceptance
```

A stage is not complete if its semantics, ownership, performance model or current status still require chat history to reconstruct.

See [Project Context](project-context.md), [Architecture](architecture.md), [ADR-026](decisions/026-semantic-capability-architecture.md), [ADR-027](decisions/027-hierarchical-geomorphic-geography.md), [Stage 5](systems/world-generation/stage5-macro-geophysics.md) and the [Continuum Development Plan](systems/world-generation/continuum-development-plan.md).

# Roadmap

This page tracks milestone state and deliberately deferred work. Detailed current semantics live in [`systems/`](systems/runtime.md); historical design exploration lives in [`notes/`](notes/).

## Completed foundations

- Object identity and immutable definitions.
- Discrete simulation time, Scheduler and ProcessScheduler binding.
- Object Spatial ownership and cell index.
- Landscape/Terrain ownership and coordinated landscape mutations.
- Geometry contract, `FullShape`, cardinal `RampShape`, neutral physical free-space facts and transition algebra.
- Structural Navigation over the shared Geometry contract.
- Actor-independent `TransitionCost` and a conservative global lower bound for admissible search.
- Generic Control/Command backbone with open namespaced result codes.
- Production `SimulationAssembly` / `SimulationRuntime` / `SimulationView` composition boundary.
- Exclusive object Occupancy with immediate execution destination reservations.
- Deterministic timed adjacent Movement with fractional carry and completion-time revalidation.
- Deterministic 3D Pathfinding foundation: exact resumable A*, traversal revisions and derived hierarchy/reachability preflight.
- Long-range `MoveTo` lifecycle over the existing one-edge Movement primitive, including route-level cancellation that stops after at most the currently scheduled atomic edge.
- Movement visualizer scenarios and authoritative route diagnostics.
- Generic autonomous-agent foundation: open Needs/capabilities, physical orientation, deterministic traces and mechanic-owned opportunities.
- 3D Vision feeding sensor-neutral Perception with visible objects and visible cells rather than source-specific sensing APIs.
- Source-neutral opportunity targets with explicit `InteractionSite`s, so source identity and the place an actor must stand are independent.
- Declarative physical interaction reach with same-level cardinal access and one-level-lower cardinal access subject to free-space/clearance rules.
- Coordinate-free unknown-source Search with local visual sweep, relative exploration and production MoveTo execution.
- Finite authoritative Consumable Stock separated from physiological benefit.
- Definition-driven scheduled Growth with narrow stock replenishment and an external effective-rate resolver.
- Generic scheduled Need progression for open `NeedId`s with narrow deficit mutation and an external effective-rate resolver.
- Provider-owned timed opportunity-use lifecycle with authoritative completion/revalidation.
- Finite liquid drinking as an autonomous opportunity over perceived cells, with exact authoritative liquid removal and proportional Need relief for partial drinks.
- One common fixed-point Utility scale for cross-provider/cross-motivation comparison, now exercised by Hunger + Thirst.
- Continuing autonomous intents that remain stable while executing and recover deterministically from failed/blocked opportunity sites.
- Multi-agent acceptance coverage proving exclusive Cows do not overlap while contending for finite opportunities.
- Integrated Living Cow Meadow slice: Hunger + Thirst competition, search/decision, MoveTo, finite plants/regrowth, finite Water, edge-lake drinking and rain-created puddle drinking.
- Finite authoritative Water quantity with sparse storage and Shape-derived cell capacity.
- Deterministic local Water redistribution with hydraulic head, exact conservation, bounded relaxation and dormant active-frontier processing.
- Material `SurfaceWaterStorage`: shallow free Water can remain conserved without becoming perpetual horizontal thin-film runoff; vertical falling remains independent of that horizontal reserve.
- Surface Hydrology: cyclic precipitation, finite SoilMoisture, deterministic coordinate-local soil capacity variation, run-on Water -> Soil exchange and finite exposed-surface evaporation.
- Water-aware terrestrial traversal: definition-driven wading depth is composed into MoveTo advice and revalidated at authoritative Movement start/commit.
- Hydrology visual acceptance: Rain Cycle, stacked Z flow, Geometry/Ramp stress, actual-flow diagnostics, calm-water fixed point checks and hydrology-aware inspector values.
- Surface-first visualizer presentation with explicit `SURFACE`, `INTERIOR` and `DEBUG_SLICE` perspectives, cell-centric interaction and bounded debug overlays.
- Optional explicit finite `WorldBounds` through shared `WorldGeometryLookup`; outside a configured runtime box is physically closed without per-domain map-edge rules.
- Deterministic World Genesis provenance, stable RNG sampling, typed `WorldAtlas` layers, replaceable generation algorithms and explicit generated-world preparation/runtime bootstrap boundaries.
- Physical climate-water generation contract, weather-state runtime boundary, rainfall-regime calibration and semantic Soil hydraulics preparation.
- Accepted V12 ocean-first balanced terrain: calibrated land coverage, coherent landmasses/coasts, broad uplift, explicit hills/depressions, rolling relief and rugged ridges, with deterministic 2D/3D preview and tunable presentation performance.
- Documentation v2: English-only canonical Markdown + VitePress + Development Journal.

## Accepted baseline

The current stable baseline closes the first Water / Surface-Hydrology milestone. Its important integration chain is now:

```text
precipitation
    ↓
SoilMoisture first
    ↓
excess authoritative Water
    ↓
run-on infiltration + local hydraulic flow
    ↓
finite evaporation
    ↓
water-aware MoveTo advice
    ↓
authoritative Movement revalidation
    ↓
Surface / Interior developer observation
```

Water, SoilMoisture, Geometry, Navigation and Movement keep separate authoritative contracts/owners. Presentation is observer-only: the visualizer reads those facts and does not create an alternate surface-world simulation.

The repository treats accepted `main` states as release/milestone baselines and performs ongoing integration on `develop`. See [Development Workflow](guides/development-workflow.md).

## Current living-world sequence

The first real multi-motivation vertical slice is implemented on the integration line:

```text
Hunger + Thirst
    ↓
object-source + liquid-source opportunities
    ↓
common deterministic Utility
    ↓
committed intent + failure recovery
    ↓
plants / edge lake / rain-created puddles
```

Thirst/Drink consumes the existing finite Water and generic opportunity/use boundaries rather than adding a Water-specific AI path. A Cow can use adjacent Water on the same standing level or one level below through the shared interaction-access model; perception remains ordinary 3D Vision rather than privileged liquid discovery.

Representative scale profiling remains mandatory before introducing speculative scheduling, indexing, memory-layout or concurrency work. Scheduling frequency, perception indexes, candidate allocation, source lookup and specialized representations must be justified by measured workloads rather than anticipation.

A richer interruption/preemption policy is deliberately not automatic next work. The current committed intent remains stable while valid; if representative gameplay demonstrates that a newly urgent motivation must interrupt a still-valid action, that concrete case will define the interruption contract.

The internal design of a future milestone is introduced by its first real consumer. A name on this roadmap does not justify dormant infrastructure.

## Current world-generation milestone

The accepted V12 surface appearance is now the protected base for a disciplined world-generation rebuild. The canonical contract and detailed stage acceptance criteria live in [World Generation](systems/world-generation.md). This roadmap intentionally does not duplicate those details.

The current stage is:

```text
Stage 0 — architecture stabilization
semantic intent
    ↓
calibration
    ↓
versioned algorithm recipe
    ↓
replaceable spatial algorithm
    ↓
typed generated facts
```

The locked milestone sequence is:

1. **Stage 0 — architecture stabilization and V12 normalization** (active);
2. **Stage 1 — mountain systems** over the accepted base morphology;
3. **Stage 2 — dry hydrography and carving**: watersheds, basins, river hierarchy, channels, valleys and lake bowls, still without Water;
4. **Stage 3 — coherent geology**: authored/calibrated rock definitions, formations, strata and required deposits;
5. **Stage 4 — caves** as coherent underground volumes;
6. **Stage 5 — surface layers/material synthesis** from morphology + hydrographic/depositional facts + geology + calibrated definitions;
7. **Stage 6 — dry-world visual/test acceptance**;
8. **Stage 7 — finite initial Water fill** into already prepared oceans/lakes/channels;
9. **Stage 8 — runtime handoff audit and world-generation milestone close**.

One world-generation stage uses one PR. If implementation changes this sequence, the canonical World Generation document and the stage report change in the same PR before work continues.

The current generated-water/hydrography and provisional geology code remains useful compatibility/typed infrastructure, but its historical ordering does not override the canonical dry-world-first pipeline.

## Separate research / large future milestones

The following remain outside the mandatory current world-generation milestone and require their own design work when a real consumer appears:

- persistent Belief / Memory and landmark/topological navigation;
- richer sensory mechanics such as hearing and smell;
- richer fluid mechanics beyond the baseline deterministic local solver, including richer boundary profiles, derived water-body identity, detailed pressure/inertia/viscosity and erosion;
- tectonic/depositional history beyond the first coherent geology model when accepted formations prove it necessary;
- ecology/biome potential derived from the completed physical world;
- coherent vegetation communities and plant populations;
- broader plant lifecycle semantics such as age, reproduction, withering and death;
- natural resources/sites beyond the deposits required by the geology milestone;
- settlements, societies, economy and other world-population generation.

## Deferred presentation work

- adjacent-layer X-ray/build tooling beyond the current explicit Debug Slice;
- richer roof/structure/large-object visibility-volume semantics beyond current terrain-driven Surface projection and explicit presentation portals;
- partial optical transmission for glass/smoke/foliage;
- lighting as a separate authoritative mechanic;
- additional terrain materials and material transitions;
- larger creature/tree/building/equipment art beyond active vertical slices;
- richer shadows/compositing;
- broader world-generation preview layers only when a concrete generated fact needs visual inspection;
- generated-atlas/export tooling if asset authoring demonstrates the need;
- broader visual chunk/dirty caches only when profiling requires them.

## Deferred movement/navigation work

- mid-edge cancellation/reactive wake-up; current MoveTo cancellation safely lets an already scheduled atomic edge finish and starts no later edge;
- falling, climbing, jumping, swimming and flying;
- actor-specific terrain affinity/locomotion beyond the current Water-wading constraint;
- automatic waiting/replanning inside `MoveTo`;
- persistent route cache;
- portal/multi-level hierarchy refinement beyond current exact reachability preflight;
- incremental/replanning pathfinder strategies such as D*/LPA*;
- JPS/JPS-3D specializations where traversal properties permit correct pruning;
- background pathfinding threads;
- path-wide/space-time planning reservations;
- temporal/SIPP-like path planning;
- yielding, swap/displacement, pushing and multi-agent deadlock resolution;
- bounded multi-agent planners such as WHCA*/CBS when local conflict cases require them;
- flow-field/group navigation;
- coordinated following/group movement and early source release.

## Deferred world/storage work

- chunk/region dimensions and loaded/unloaded/generated state;
- generated or streamed world-bound policy beyond the current explicit finite runtime box;
- packed coordinate representation;
- persistence/network boundaries;
- authoritative multithreaded mutation.

A future loaded-state model must not silently treat `UNLOADED/UNKNOWN` as ordinary empty geometry. The current explicit `WorldBounds` is a finite runtime containment mechanism, not a streaming design.

## Activation rule

A deferred idea becomes active design when at least one is true:

- a production consumer cannot proceed without it;
- an invariant/correctness test proves the current contract insufficient;
- a representative workload measures a real performance problem;
- persistence/network/tooling requires stable external representation;
- a vertical slice exposes an ownership ambiguity.

“Could be useful later” is not sufficient.

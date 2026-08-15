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
- Generic autonomous-agent foundation: open Needs/capabilities, directed Vision, physical orientation, deterministic decision traces and mechanic-owned opportunities.
- Coordinate-free unknown-source Search with local visual sweep, relative exploration and production MoveTo execution.
- Finite authoritative Consumable Stock separated from physiological benefit.
- Definition-driven scheduled Growth with narrow stock replenishment and an external effective-rate resolver.
- Generic scheduled Need progression for open `NeedId`s with narrow deficit mutation and an external effective-rate resolver.
- Provider-owned timed opportunity-use lifecycle with authoritative completion/revalidation.
- Integrated Living Cow visual slice: Hunger progression, search/decision, MoveTo, finite plant depletion/regrowth and timed grazing.
- Finite authoritative Water quantity with sparse storage and Shape-derived cell capacity.
- Deterministic local Water redistribution with hydraulic head, exact conservation, bounded relaxation and dormant active-frontier processing.
- Material `SurfaceWaterStorage`: shallow free Water can remain conserved without becoming perpetual horizontal thin-film runoff; vertical falling remains independent of that horizontal reserve.
- Surface Hydrology: cyclic precipitation, finite SoilMoisture, deterministic coordinate-local soil capacity variation, run-on Water -> Soil exchange and finite exposed-surface evaporation.
- Water-aware terrestrial traversal: definition-driven wading depth is composed into MoveTo advice and revalidated at authoritative Movement start/commit.
- Hydrology visual acceptance: Rain Cycle, stacked Z flow, Geometry/Ramp stress, actual-flow diagnostics, calm-water fixed point checks and hydrology-aware inspector values.
- Surface-first visualizer presentation with explicit `SURFACE`, `INTERIOR` and `DEBUG_SLICE` perspectives, cell-centric interaction and bounded debug overlays.
- Optional explicit finite `WorldBounds` through shared `WorldGeometryLookup`; outside a configured runtime box is physically closed without per-domain map-edge rules.
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

Water quantity, SoilMoisture, Geometry, Navigation, Movement and presentation remain separate owners. The visualizer observes these facts; it does not create an alternate surface-world simulation.

The repository now treats accepted `main` states as release/milestone baselines and performs ongoing integration on `develop`. See [Development Workflow](guides/development-workflow.md).

## Current living-world sequence

The next direct living-world consumers remain:

```text
Thirst + Drink
    ↓
real Utility competition between motivations
    ↓
Intent persistence / interruption
    ↓
representative scale profiling
```

Thirst/Drink should consume the existing finite Water and generic opportunity/use boundaries rather than adding a Water-specific AI path. Real Hunger + Thirst competition is the first reason to replace the provisional single-motivation scoring with a richer utility model.

Representative scale profiling remains mandatory before AI/world hot-path optimization. Scheduling, perception indexes, memory layout and other specialized representations must be justified by measured workloads rather than anticipation.

The internal design of a future milestone is introduced by its first real consumer. A name on this roadmap does not justify dormant infrastructure.

## Separate research / large future milestones

The following remain outside the mandatory immediate sequence and require their own design work when a real consumer appears:

- persistent Belief / Memory and landmark/topological navigation;
- richer sensory mechanics such as hearing and smell;
- richer fluid mechanics beyond the baseline deterministic local solver, including richer boundary profiles, derived water-body identity, detailed pressure/inertia/viscosity and erosion;
- deterministic World Generation and authoritative RNG-stream policy;
- broader plant lifecycle semantics such as age, reproduction, withering and death.

## Deferred presentation work

- adjacent-layer X-ray/build tooling beyond the current explicit Debug Slice;
- richer roof/structure/large-object visibility-volume semantics beyond current terrain-driven Surface projection and explicit presentation portals;
- partial optical transmission for glass/smoke/foliage;
- lighting as a separate authoritative mechanic;
- additional terrain materials and material transitions;
- larger creature/tree/building/equipment art beyond active vertical slices;
- richer shadows/compositing;
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

- generation algorithm and authoritative RNG stream policy;
- chunk/region dimensions and loaded/unloaded/generated state;
- generated or streamed world-bound policy beyond the current optional explicit finite runtime box;
- packed coordinate representation;
- persistence/network boundaries;
- authoritative multithreaded mutation.

A future loaded-state model must not silently treat `UNLOADED/UNKNOWN` as ordinary empty geometry. The current explicit `WorldBounds` is a finite runtime containment mechanism, not a world-generation or streaming design.

## Activation rule

A deferred idea becomes active design when at least one is true:

- a production consumer cannot proceed without it;
- an invariant/correctness test proves the current contract insufficient;
- a representative workload measures a real performance problem;
- persistence/network/tooling requires stable external representation;
- a vertical slice exposes an ownership ambiguity.

“Could be useful later” is not sufficient.

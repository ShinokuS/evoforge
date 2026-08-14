# Roadmap

This page tracks only milestone state and deliberately deferred work. Detailed current semantics live in [`systems/`](systems/runtime.md).

## Completed foundations

- Object identity and immutable definitions
- Discrete simulation time, Scheduler and ProcessScheduler binding
- Object Spatial ownership and cell index
- Landscape/Terrain ownership and coordinated landscape mutations
- Geometry contract, `FullShape`, cardinal `RampShape` and transition algebra
- Structural Navigation
- Actor-independent TransitionCost
- Generic Control/Command backbone
- Production `SimulationAssembly` / `SimulationRuntime` / `SimulationView`
- Timed adjacent Movement
- Scenario test harness
- Live multi-Z procedural visualizer with geometric cutaway, diagnostics and performance telemetry
- Presentation ownership cleanup and typed Shape presentation bindings
- Documentation v2: English-only canonical Markdown + VitePress + Development Journal
- Exclusive object Occupancy with immediate execution destination reservations
- Deterministic 3D Pathfinding foundation: exact resumable A*, traversal revisions, derived hierarchy/reachability and focused diagnostics
- Observable one-edge Movement completion and long-range `MoveTo` lifecycle over the existing edge primitive
- Movement visualizer scenarios: multi-level waypoint patrol and interactive visible-surface LMB-select / RMB-MoveTo with route diagnostics
- Generic autonomous-agent foundation: open Needs/capabilities, directed Vision, physical orientation, deterministic decision traces and mechanic-owned opportunities
- Coordinate-free unknown-source Search with local visual sweep, correlated multi-cell exploration and generic active-route diagnostics
- Finite authoritative Consumable Stock separated from physiological benefit
- Definition-driven scheduled Growth with narrow stock replenishment and an external effective-rate resolver
- Generic scheduled Need progression for open NeedIds with narrow deficit mutation and an external effective-rate resolver

## Current living-world sequence

```text
Integrated living Cow slice + full developer visualization
    ↓
Water + Thirst
    ↓
real Utility competition
    ↓
Intent persistence / interruption
    ↓
representative scale profiling
```

The integrated Cow slice is the first mandatory visual acceptance point for the current sequence. It should expose authoritative state, cell/object selection, Vision, routes, Needs, Need progression, resources, Growth and timed interactions with state-dependent presentation/animation.

The slice should visibly demonstrate the closed loop:

```text
plant regrows finite biomass
    ↓
Hunger progresses over time
    ↓
Cow perceives/searches/selects food
    ↓
MoveTo route
    ↓
timed consumption interaction
    ↓
Hunger decreases + biomass decreases
    ↓
cycle continues
```

Representative scale profiling is mandatory before AI/world hot-path optimization. Scheduling, perception indexes, memory layout and other specialized representations must be justified by measured workloads rather than anticipation.

The internal design of a future milestone is introduced by its first real consumer. A name on this roadmap does not justify dormant infrastructure.

## Separate research / large future milestones

The following are intentionally outside the mandatory living-Cow path and require their own design work when a real consumer appears:

- persistent Belief / Memory and landmark/topological navigation;
- richer sensory mechanics such as hearing and smell;
- multi-cell fluid evolution, redistribution and derived water-body identity;
- deterministic World Generation and authoritative RNG-stream policy;
- broader plant lifecycle semantics such as age, reproduction, withering and death.

## Deferred presentation work

- explicit adjacent-layer X-ray/build mode
- real roofs/structures/large objects contributing to presentation visibility volume
- partial optical transmission for glass/water/smoke/foliage
- lighting as a separate authoritative mechanic
- additional terrain materials and material transitions
- larger creature/tree/building/equipment art beyond the active vertical slice
- richer shadows/compositing
- generated-atlas export tooling
- visual chunk/dirty caches until profiling requires them

## Deferred movement/navigation work

- early movement cancellation and reactive wake-up
- falling, climbing, jumping, swimming and flying
- actor-specific terrain affinity/locomotion
- automatic waiting/replanning inside `MoveTo`
- persistent route cache
- portal/multi-level hierarchy refinement beyond the current exact reachability preflight
- incremental/replanning pathfinder strategies such as D*/LPA*
- JPS/JPS-3D specializations where traversal properties permit correct pruning
- background pathfinding threads
- path-wide/space-time planning reservations
- temporal/SIPP-like path planning
- yielding, swap/displacement, pushing and multi-agent deadlock resolution
- bounded multi-agent planners such as WHCA*/CBS when local conflict cases require them
- flow-field/group navigation
- coordinated following/group movement and early source release

## Deferred world/storage work

- generation algorithm and authoritative RNG stream policy
- chunk/region dimensions
- unloaded vs absent vs not-generated state
- world bounds and packed coordinate representation
- persistence/network boundaries
- authoritative multithreaded mutation

## Activation rule

A deferred idea becomes active design when at least one is true:

- a production consumer cannot proceed without it;
- an invariant/correctness test proves the current contract insufficient;
- a representative workload measures a real performance problem;
- persistence/network/tooling requires stable external representation;
- a vertical slice exposes an ownership ambiguity.

“Could be useful later” is not sufficient.

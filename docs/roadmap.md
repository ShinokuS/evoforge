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
- Movement visualizer scenarios: closed multi-level Z0-Z4 waypoint patrol and interactive LMB-select / RMB-MoveTo across the same 3D course with route diagnostics

## Next simulation sequence

```text
first agent / Cow vertical slice
    ↓
deterministic World Generation
    ↓
representative scale profiling
```

The internal design of a future milestone is introduced by its first real consumer. A name on this roadmap does not justify dormant infrastructure.

## Deferred presentation work

- explicit adjacent-layer X-ray/build mode
- real roofs/structures/large objects contributing to presentation visibility volume
- partial optical transmission for glass/water/smoke/foliage
- actor line-of-sight and lighting as separate authoritative mechanics
- additional terrain materials and material transitions
- larger creature/tree/building/equipment art
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

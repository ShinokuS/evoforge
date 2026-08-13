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

## Next simulation sequence

```text
Pathfinder
    ↓
observable Movement/Action outcome
    ↓
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
- multi-step `MoveTo` lifecycle
- path cache, hierarchical pathfinding and background pathfinding
- persistent Navigation cache and invalidation lifecycle
- path-wide/space-time planning reservations
- yielding, swap/displacement, pushing and multi-agent deadlock resolution
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

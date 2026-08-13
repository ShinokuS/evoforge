# Debug Scenarios

Use a visualizer scenario when a mechanic or cross-system interaction is useful to **see and understand as a human**, not merely because a new test case exists.

## Role

A debug scenario is a deterministic presentation-side composition of the real production simulation.

It complements, but never replaces:

- headless invariant/correctness tests;
- subsystem documentation;
- representative performance benchmarks.

A scenario must not introduce fake versions of simulation mechanics just to make a picture easier to produce.

## Adding a scenario

1. Add a small Java class under `visualizer/scenario/` implementing `VisualizerScenario`.
2. Build the world through production `SimulationAssembly`.
3. Keep the scene focused on one understandable behavior or interaction.
4. Return a fresh `ScenarioSession` containing the new `SimulationRuntime` and a presentation-only `ScenarioView`.
5. Register the scenario in `ScenarioCatalog.standard()`.
6. Add headless tests for the meaningful scenario setup/invariants.
7. Run the desktop visualizer and manually verify the scene, relevant overlays and restart behavior.

A normal scenario should read approximately like a compact example of the mechanic. If understanding it requires unrelated mountains, actors, structures and diagnostics, split it.

## Determinism and restart

`VisualizerScenario.create()` must be safe to call repeatedly and must create a fresh runtime every time.

`R` is defined as reconstruction, not mutation:

```text
old runtime discarded
        ↓
same scenario.create()
        ↓
fresh runtime at the same deterministic initial state
```

Do not add debug-only `reset()`/`clearEverything()` methods to simulation systems to support visualizer restart.

If a scenario later uses procedural generation, ordinary restart repeats the same fixed seed. A different seed is a separate explicit tool action.

## Presentation boundary

Scenario metadata and `ScenarioView` may choose where the camera initially looks and which standing Z opens first. They must not change authoritative rules according to visibility, camera position or player proximity.

The generic `ZLevelVisualizer` remains shared by every scenario. Do not create subsystem-specific visualizer implementations such as `MovementVisualizer` or `PathfinderVisualizer`.

## Keep scenarios small

Good reasons to add a scenario:

- a new mechanic has important spatial or temporal behavior that is easier to validate visually;
- two established systems now interact in a way worth observing;
- a debugging workflow benefits from a stable reproducible world;
- a future regression would be much easier to recognize visually in a focused scene.

Poor reasons:

- every individual unit-test edge case;
- avoiding a headless test;
- showcasing unrelated features in one world;
- creating a generic scenario DSL before repeated authoring pain demonstrates a need.

Extract shared scenario helpers only after actual repetition appears. Helpers may reduce setup boilerplate; they must not hide the production mechanics being demonstrated.

## Performance scenarios

Small explanatory scenarios and scale benchmarks serve different purposes.

A future `Pathfinder — Basic` scenario may contain one small route and obstacles for understanding. A separate representative workload may later contain large terrain and many agents for profiling. Do not inflate the basic scenario merely to make it double as a benchmark.

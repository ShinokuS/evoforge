# Debug Scenarios

Use a visualizer scenario when a mechanic or cross-system interaction is useful to **see and understand as a human**, not merely because a new test case exists.

## Role

A debug scenario is a deterministic presentation-side composition of the real production simulation.

It complements, but never replaces:

- headless invariant/correctness tests;
- subsystem documentation;
- representative performance benchmarks.

A scenario must not introduce fake versions of simulation mechanics just to make a picture easier to produce.

## Organization

Scenario implementations are grouped beside the domain/composition they demonstrate:

```text
visualizer/scenario/
  shared catalog/session/diagnostic contracts
  agent/
  environment/
  geometry/
  movement/
  occupancy/
  pathfinding/
  water/
```

Tests mirror the same domain packages. Keep shared presentation contracts/helpers at the scenario root; keep mechanic-specific fixtures, controllers and helpers beside the scenarios that use them.

`ScenarioCatalog.standard()` exposes explicit browser groups that currently include:

```text
Geometry & Navigation
Movement
Occupancy
Water / Hydrology
Agents
Pathfinding
```

Browser grouping is metadata owned by the catalog, not inferred from Java package names or title prefixes. A cross-system scenario may therefore live beside the composition it owns while being presented under the most useful browser group; `RainHydrologyScenario` is the current example (`environment/` package, `Water / Hydrology` group).

## Adding a scenario

1. Add a small Java class under the most appropriate `visualizer/scenario/<domain>/` package implementing `VisualizerScenario`.
2. Build the world through production `SimulationAssembly`.
3. Keep the scene focused on one understandable behavior or interaction.
4. Return a fresh `ScenarioSession` containing the new `SimulationRuntime` and presentation-only initial view/bindings needed by that scene.
5. Register the scenario in the appropriate explicit `ScenarioGroup` in `ScenarioCatalog.standard()`.
6. Add headless tests in the matching scenario test package for meaningful setup/invariants.
7. Run the desktop visualizer and manually verify the scene, relevant overlays, search/group placement, interaction and restart behavior.

A normal scenario should read approximately like a compact example of the mechanic. If understanding it requires unrelated mountains, actors, structures and diagnostics, split it.

## Browser workflow

The selector is a searchable grouped browser rather than a growing flat list.

- `Up/Down`: move through visible group/scenario rows;
- `Left/Right`: collapse/expand the selected group;
- `Enter`: toggle a group or open a scenario;
- mouse click: toggle a group or open a scenario;
- mouse wheel: scroll the scenario list;
- typing: search by group title/id and scenario title/id/description;
- `Backspace`: edit the search query;
- `Esc`: clear the current search query.

Search temporarily exposes matching groups even when they are normally collapsed. Clearing search restores the previous expanded/collapsed state instead of mutating it.

## Determinism and restart

`VisualizerScenario.create()` must be safe to call repeatedly and create a fresh runtime every time.

`R` is reconstruction, not mutation:

```text
old runtime discarded
        ↓
same scenario.create()
        ↓
fresh runtime at the same deterministic initial state
```

Do not add debug-only `reset()`/`clearEverything()` methods to simulation systems to support visualizer restart.

If a scenario later uses procedural generation, ordinary restart should repeat the same explicit seed unless a separate tool action intentionally requests a new seed.

## Presentation boundary

Scenario metadata and `ScenarioView` may choose where the camera initially looks and which presentation perspective opens first. Portals/weather/object bindings may configure presentation metadata. None of these may change authoritative simulation rules according to visibility, camera position or player proximity.

The generic `ZLevelVisualizer` remains shared by every scenario. Do not create subsystem-specific visualizer implementations such as `MovementVisualizer` or `PathfinderVisualizer`.

## Surface / Interior interaction

The current visualizer defaults to Surface projection. A scenario that demonstrates covered local space may provide explicit presentation portals/Interior bounds, but the portal itself must not create physical connectivity or teleport an object.

If a scenario exposes Move actions, command submission must go through the ordinary production command/runtime boundary. Hover route preview remains advisory and may not become a scenario-owned movement implementation.

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

Small explanatory scenarios and scale benchmarks serve different purposes. Do not inflate a basic semantic scenario merely to make it double as a benchmark.

When a milestone has desktop/manual acceptance criteria, record them in its working PR/Development Journal while they are active. Once the milestone is merged, canonical system pages should describe the resulting semantics; historical acceptance notes remain non-normative.

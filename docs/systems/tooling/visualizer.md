# Visualizer and Developer Inspection Tools

## In plain language

The Visualizer is EvoForge's **window into the simulation**. It may display Terrain, Water, agents, routes and diagnostics, but it never decides what is physically true.

Camera position, zoom, hidden layers and debug overlays are presentation only. They cannot change simulation or generation fidelity.

## Current status

The current visualizer is a runtime inspection tool built around ordinary production capabilities. The retired V12–V15 world-generation preview was removed with the old dense generator.

A new Continuum preview is planned as part of Phase 0. It does not exist yet.

## Runtime observer boundary

`ZLevelVisualizer` is built from read-oriented/runtime capabilities such as:

```text
SimulationView
SimulationTime
SimulationStepper
presentation bindings
```

When interaction requests a real action, presentation uses an explicit command adapter/sink rather than mutating domain systems directly.

```text
user input
    ↓
presentation command sink
    ↓
production command/domain path
    ↓
authoritative owner
    ↓
SimulationView
    ↓
visualizer redraws observed truth
```

## Scenario model

Focused deterministic scenarios make mechanics understandable and visually inspectable. Each scenario creates its own ordinary runtime/session and uses production systems.

Scenarios complement headless tests; they do not replace deterministic or invariant tests.

## Presentation perspectives

### Surface

Shows the highest relevant Terrain/Water/object surface by visible XY column.

### Interior

Uses presentation-local cutaway/interior context without changing actor coordinates or Navigation truth.

### Debug Slice

Shows an explicit developer Z slice/cutaway for internal layers.

Changing perspective never changes authoritative XYZ state.

## Cell-centric interaction

World interaction is cell-first. Selection reads authoritative facts at the chosen coordinate. Movement preview may perform disposable path search, but actual execution goes through the production command path and remains subject to Movement, Occupancy, Geometry and Water constraints.

## Water presentation

Water visuals read authoritative quantity and geometry. Optical depth, opacity, animation phase and tiny-flux suppression are presentation choices only; they cannot suppress hydraulic simulation work.

## Continuum inspection direction

Phase 0 will introduce a replacement world-generation inspection surface built around the production Continuum contracts.

It must support at minimum:

```text
pan / zoom across large logical coordinates
requested-window visualization
page/cache boundary overlay
resident/evicted page diagnostics
cache hit/miss/load/eviction metrics
seed + coordinate inspection
```

Critical law:

```text
presentation detail may change
world truth may not
```

The preview must never materialize the whole logical world merely to draw an overview. It asks for bounded working windows and visualizes the returned facts.

Later generation stages add their own overlays to the same inspection approach rather than creating independent debug generators.

## Manual visual acceptance versus tests

Every visually meaningful generation stage requires both:

- automated deterministic/invariant tests;
- manual inspection of the production result through the visualizer.

Performance metrics are also part of acceptance. A visually correct stage is not complete if inspection reveals unbounded allocation or avoidable frame stalls.

## Invariants

- Visualizer never owns authoritative simulation state.
- Camera/view/zoom/LOD never changes simulation or generation semantics.
- Runtime interactions go through production command/domain paths.
- A future Continuum preview must consume production Continuum contracts.
- Debug overlays never become hidden Navigation, Water, AI or generation truth.
- Visual acceptance complements, but never replaces, headless tests.

## Current limitations

There is currently no production Continuum world-generation preview. Phase 0 owns that work together with bounded page/cache materialization and metrics.

## Code and tests

Runtime presentation lives primarily under:

```text
core/.../visualizer/
```

Continuum generation currently lives under:

```text
simulation/.../world/continuum/
```

## Sources

**Internal EvoForge tooling/presentation design.** The observer boundary and visual acceptance workflow are project infrastructure.

See [Debug Scenarios Guide](../../guides/debug-scenarios.md), [World Generation](../world-generation/overview.md), [Continuum Development Plan](../world-generation/continuum-development-plan.md), [ADR-004](../../decisions/004-typed-presentation-bindings.md), and [ADR-024](../../decisions/024-continuum-large-world-architecture.md).

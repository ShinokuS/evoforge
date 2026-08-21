# Visualizer and Developer Inspection Tools

## In plain language

The Visualizer is EvoForge's **window into the simulation and generated facts**. It may display Terrain, Water, agents, routes, Continuum technical pages and diagnostics, but it never decides what is physically true.

Camera position, zoom, hidden layers and debug overlays are presentation only. They cannot change simulation or generation fidelity.

## Current status

The current visualizer has two deliberately separate inspection paths:

- runtime scenarios built around ordinary production `SimulationRuntime` capabilities;
- a Phase 0 Continuum technical-page inspector built directly on the production Continuum page/cache contracts.

The retired V12–V15 world-generation preview remains removed. The new Continuum inspector is not a replacement generator and does not bootstrap Continuum into runtime.

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

## Continuum Phase 0 inspector

From the scenario menu, `F2` opens the Continuum technical-page inspector.

It currently shows a 1,000,000 × 1,000,000 logical domain while materializing only a bounded local 3×3 requested neighborhood. The cache may retain at most twelve 256×256 diagnostic scalar pages in this visual workload. Those dimensions are inspection defaults, not final world laws.

The overlay distinguishes:

```text
all visible technical page boundaries
requested 3×3 neighborhood
currently resident cache pages
pages evicted by the most recent move
focused page
```

It also displays:

- logical/world coordinates;
- seed;
- resident page count and scalar payload budget;
- hits, misses, loads and evictions;
- zoom in pixels per technical page.

The shaded scalar is explicitly marked **diagnostic only — not geography**. It exists so page materialization is visible before continents/relief exist.

Controls:

```text
Arrows / WASD      move one technical page
Shift + move       move eight pages
+ / - / wheel      zoom presentation only
Home               return to logical center
Esc                return to scenario menu
```

Critical law:

```text
presentation detail may change
requested/resident representation may change
coordinate-addressed world truth may not
```

Panning causes bounded page requests and therefore real cache hit/miss/eviction behavior. Zoom changes only how many page boundaries fit on screen; it does not request higher/lower-fidelity world truth.

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

## Manual visual acceptance versus tests

Every visually meaningful generation stage requires both:

- automated deterministic/invariant tests;
- manual inspection of the production result through the visualizer.

The Continuum inspector model is headlessly tested for bounded 3×3 requests, overlap reuse, eviction and stable rematerialized values. The screen itself is the manual visual inspection surface.

Performance metrics are also part of acceptance. A visually correct stage is not complete if inspection reveals unbounded allocation or avoidable stalls.

## Invariants

- Visualizer never owns authoritative simulation state.
- Camera/view/zoom/LOD never changes simulation or generation semantics.
- Runtime interactions go through production command/domain paths.
- Continuum inspector consumes production `ContinuumPageLayout` / `ContinuumScalarPageCache` contracts.
- Continuum page/cache state remains technical representation only.
- Debug overlays never become hidden Navigation, Water, AI or generation truth.
- Visual acceptance complements, but never replaces, headless tests.

## Current limitations

The Continuum inspector currently visualizes only the Phase 0 diagnostic scalar and technical page/cache state. Geography overlays, seed controls, arbitrary coordinate entry and later generation layers will be added as those production facts exist.

## Code and tests

Runtime presentation lives primarily under:

```text
core/.../visualizer/
```

Continuum inspection:

```text
core/.../visualizer/continuum/ContinuumInspectorModel.java
core/.../visualizer/screen/ContinuumInspectorScreen.java
core/.../visualizer/continuum/ContinuumInspectorModelTest.java
```

Continuum generation/materialization lives under:

```text
simulation/.../world/continuum/
```

## Sources

**Internal EvoForge tooling/presentation design.** The observer boundary and visual acceptance workflow are project infrastructure.

See [Debug Scenarios Guide](../../guides/debug-scenarios.md), [World Generation](../world-generation/overview.md), [Continuum Technical Pages and Cache](../world-generation/continuum-pages.md), [Continuum Development Plan](../world-generation/continuum-development-plan.md), [ADR-004](../../decisions/004-typed-presentation-bindings.md), and [ADR-024](../../decisions/024-continuum-large-world-architecture.md).

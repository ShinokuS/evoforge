# Visualizer

## Purpose

Observe authoritative simulation state during development. The visualizer is not a second world model and never owns simulation truth.

The debug application is scenario-driven: one generic visualizer is reused across small deterministic worlds that each demonstrate one understandable mechanic or interaction. Scenarios improve human debugging and explanation; they do not replace headless correctness tests.

## Runtime boundary

`ZLevelVisualizer` receives only:

```text
SimulationView
SimulationTime
SimulationStepper
```

An executable boundary test protects this constructor contract.

`SimulationView` exposes read-only capabilities needed by presentation diagnostics, including Object/Spatial, Orientation, Vision, Occupancy, MoveTo, Need, autonomous Decision and Search state. Presentation never receives mutable domain owners.

`ZLevelVisualizer` exposes its presentation input processor to the application shell, but it does not own global `Gdx.input` routing. Scenario-level controls therefore compose with camera/time/debug controls without teaching simulation code about scenario lifecycle.

## Scenario lifecycle

The application starts at a searchable grouped scenario browser.

```text
Main
  ↓
ScenarioCatalog
  ├─ Geometry & Navigation
  ├─ Movement
  ├─ Occupancy
  ├─ Agents
  └─ Pathfinding
  ↓
VisualizerScenario
  ↓
ScenarioSession
  ├─ fresh SimulationRuntime
  ├─ initial ScenarioView
  └─ optional ScenarioController
  ↓
ScenarioScreen
  ↓
ZLevelVisualizer
```

A `VisualizerScenario` is presentation tooling that builds a fresh production simulation through `SimulationAssembly`. Simulation modules do not depend on scenario types.

`ScenarioView` contains presentation-only initial focus:

```text
selected standing Z
camera X/Y
zoom
```

`ScenarioController` is optional visualizer-only tooling driven by authoritative simulation ticks. It may update diagnostics or submit ordinary production commands to the scenario runtime; it is not simulation state.

Current groups include:

- **Geometry & Navigation** — Z-Level / Cutaway, Ramp Navigation;
- **Movement** — Timed Movement, Movement Patrol, Click To Move;
- **Occupancy** — Occupancy Contention;
- **Agents** — Cow Foraging, Cow Visual Search;
- **Pathfinding** — straight, detour, 3D, hierarchy, invalidation and related scenarios.

The root `visualizer.scenario` package contains shared scenario contracts/catalog/diagnostic primitives; mechanic-specific scenarios live in domain subpackages.

## Presentation ownership

```text
Main                         application screen switching
ScenarioMenuScreen           grouped/searchable scenario browser rendering + input
ScenarioMenuModel            filter/expansion/selection state
ScenarioCatalog              ordered groups and scenarios
ScenarioScreen               active scenario lifecycle
VisualizerScenario           builds one fresh deterministic world
ScenarioController           optional presentation-only scenario tooling
ZLevelVisualizer             generic render orchestration
VisualizerState              selected Z / overlay / selection state
VisualizerCamera             pan / zoom / viewport conversion
VisualizerInputController    physical input -> presentation/time controls
LandscapeRenderer            terrain/cutaway rendering
VisualizerOverlayRenderer    grid/navigation/occupancy/object overlays
VisionDiagnosticRenderer     authoritative selected-object Vision overlay
MoveToRouteDiagnosticRenderer authoritative selected-object active-route overlay
VisualizerHudRenderer        status and inspector UI
ShapePresentationRegistry    typed Shape presentation dispatch
```

No component above becomes an authoritative simulation owner.

## Vision diagnostics

Selecting an object with Vision automatically visualizes the simulation's authoritative `VisionSnapshot`.

The renderer does **not** reconstruct a decorative FOV cone from presentation-side math.

Current diagnostics:

```text
soft cell highlight   cells Vision says are visible now
object frame          objects Vision says are visible now
facing arrow          authoritative physical orientation
HUD Vision data       facing / range / FOV / visible counts
```

This is intentionally cell-based because it answers the development question directly:

> Which cells and objects can this agent actually perceive right now?

Occlusion, FOV and range changes therefore appear automatically because presentation reads the same result used by autonomous decision making.

## Active route diagnostics

Selecting any object with an active production `MoveTo` automatically shows its current planned route.

`MoveToRouteDiagnosticRenderer` reads `MoveToLookup.activeRoute(...)`; it does not invoke Pathfinder or reproduce route logic in presentation.

Current route presentation:

```text
route cell frame      each cell in the active PathRoute on selected Z
goal frame            route goal
```

The overlay is reason-agnostic. The same presentation works for:

```text
search exploration
movement toward food/water
future autonomous intents
external MoveTo consumers
```

This keeps route observability attached to the movement contract rather than to Cow-specific scenarios.

## Agent diagnostics

`Cow Foraging` reads authoritative Need and Decision state. It shows competing perceived sources, the selected winner and the current hunger/score context.

`Cow Visual Search` exposes the information-seeking lifecycle. Its summary reads `AgentSearchTrace`, physical facing and authoritative Vision state. It can show states such as:

```text
SWEEPING
EXPLORING
RELOCATION_BLOCKED
```

The scenario now uses a larger world and Vision radius. Grass starts well outside the initial field of view. After a local sweep the Cow chooses a multi-cell relative exploration leg inside its current visual horizon; the generic MoveTo route overlay shows that leg while it is being executed.

Search presentation never knows hidden Grass coordinates on behalf of the Cow. A target marker appears only after ordinary autonomous decision has selected a concrete source.

The generic Vision and route overlays remain active during these scenarios, so clicking the Cow shows what it currently sees, where it is currently moving, and why Search/Decision selected that process.

## Typed Shape presentation

Generic presentation does not branch on concrete Shape classes. `ShapePresentationRegistry` dispatches by exact Java type to specialized bindings registered in the presentation composition root.

Current bindings include full cells and ramps. A binding supplies only presentation facts needed by renderers; procedural art does not become simulation semantics.

## Horizontal Z cut

Visualizer Z is the standing/navigation plane. For each XY:

```text
terrain at selected Z       -> SOLID_BODY
terrain at selected Z - 1   -> CURRENT_SURFACE
nearest visible terrain down through open volume -> LOWER_SURFACE
otherwise                   -> EMPTY
```

Visibility is derived from geometry, not semantic labels such as "cave".

## Cutaway and procedural landscape

Current-surface terrain keeps its natural palette; deeper/covered surfaces darken according to depth/exposure. Ramps keep one visual identity across slice context.

The canonical development landscape is generated procedurally in memory at 16x16 native pixels per logical cell. Surface topology is deterministic from XYZ and local geometry.

Presentation sampling changes with zoom to reduce shimmer without changing authoritative geometry.

## Controls

Application/session controls:

- `R`: recreate the active scenario from a fresh runtime;
- `Esc`: return to the scenario browser.

Generic visualizer controls:

- `Space`: run/pause simulation time;
- `N`: single simulation step while paused;
- `WASD`: pan;
- mouse wheel: zoom;
- `PgUp/PgDn`: change standing Z;
- `G`: grid mode;
- `F2`: Navigation transition overlay;
- `F3`: Shape direction diagnostics;
- `F4`: lower-surface visibility depth;
- `F5`: Occupancy overlay;
- click: select cell/object at current standing Z.

The HUD shows tick, Z, FPS, zoom/sampling and selected-object context.

## Restart semantics

Restart discards the current scenario session and creates a new runtime from the same deterministic scenario definition.

```text
R
 ↓
dispose current session
 ↓
VisualizerScenario.create()
 ↓
new SimulationRuntime
```

Simulation systems therefore need no debug-only reset APIs.

## Performance

Visualizer performance telemetry distinguishes observed frame interval from CPU work inside renderer stages. Landscape analysis/caches and diagnostic scans are optimized only when profiling identifies them as hot.

Focused correctness scenarios and representative performance scenarios remain separate concerns.

## Testing boundary

Headless simulation tests own semantic correctness for Geometry, Navigation, Occupancy, Movement, Pathfinding, Vision, Needs, Decision and Search.

Visualizer scenario tests verify meaningful setup and that presentation exposes the same authoritative diagnostic state. The Cow visual-search scenario additionally verifies that a multi-cell exploration `PathRoute` becomes observable through `MoveToLookup` before a concrete food target is known. Final appearance/readability remains a manual desktop check.

See [Debug Scenarios Guide](../guides/debug-scenarios.md) when adding a new human-observable development scenario.

# Visualizer

## Purpose

Observe authoritative simulation state during development. The visualizer is not a second world model and never owns simulation truth.

The debug application is scenario-driven: one generic visualizer is reused across small deterministic worlds that each demonstrate one understandable mechanic or interaction. Scenarios improve human debugging and explanation; they do not replace headless correctness tests.

## Runtime boundary

`ZLevelVisualizer` receives:

```text
SimulationView
SimulationTime
SimulationStepper
optional presentation-only ObjectPresentationBindings
```

The bindings contain display metadata only. Simulation modules never depend on visualizer presentation types.

`SimulationView` exposes read-only capabilities needed by presentation diagnostics, including Object/Spatial, Orientation, Vision, Occupancy, MoveTo, Need/NeedProgression, ConsumableStock, Growth, autonomous Decision and Search state. Presentation never receives mutable domain owners.

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
  ├─ optional ScenarioController
  └─ optional ObjectPresentationBindings
  ↓
ScenarioScreen
  ↓
ZLevelVisualizer
```

A `VisualizerScenario` is presentation tooling that builds a fresh production simulation through `SimulationAssembly`. Simulation modules do not depend on scenario types.

`ScenarioController` is optional visualizer-only tooling driven by authoritative simulation ticks. It may update diagnostics or submit ordinary production commands to the scenario runtime; it is not simulation state.

Current groups include:

- **Geometry & Navigation** — Z-Level / Cutaway, Ramp Navigation;
- **Movement** — Timed Movement, Movement Patrol, Click To Move;
- **Occupancy** — Occupancy Contention;
- **Agents** — Living Cow Cycle, Cow Foraging, Cow Visual Search;
- **Pathfinding** — straight, detour, 3D, hierarchy, invalidation and related scenarios.

## Presentation ownership

```text
Main                          application screen switching
ScenarioMenuScreen            grouped/searchable scenario browser rendering + input
ScenarioMenuModel             filter/expansion/selection state
ScenarioCatalog               ordered groups and scenarios
ScenarioScreen                active scenario lifecycle
VisualizerScenario            builds one fresh deterministic world
ScenarioController            optional presentation-only scenario tooling
ObjectPresentationBindings    definition -> presentation metadata only
ZLevelVisualizer              generic render orchestration
VisualizerState               selected Z / overlay / selection state
VisualizerCamera              pan / zoom / viewport conversion
VisualizerInputController     physical input -> presentation/time controls
LandscapeRenderer             terrain/cutaway rendering
ObjectPresentationRenderer    all object art, including generic fallback
VisionDiagnosticRenderer      authoritative selected-object Vision overlay
MoveToRouteDiagnosticRenderer authoritative selected-object active-route overlay
VisualizerOverlayRenderer     grid/navigation/occupancy/selection diagnostics only
VisualizerHudRenderer         status + living-world inspector
ShapePresentationRegistry     typed Shape presentation dispatch
```

World presentation is rendered before developer diagnostics:

```text
landscape
  ↓
object art
  ↓
Vision / route / debug overlays
  ↓
HUD
```

No component above becomes an authoritative simulation owner.

## Object presentation bindings

Object art is selected outside simulation:

```text
ObjectDefinitionId
    ↓ presentation-only binding
ObjectPresentation
    ├─ displayName
    ├─ description
    ├─ visual family
    └─ deterministic variant
```

Current visual families are presentation renderer families, not domain object categories:

```text
GENERIC
CREATURE
VEGETATION
```

They do not control AI, physics, capabilities or interaction eligibility.

`ObjectPresentationRenderer` is the only object-art owner. Definitions without a special binding retain the old deterministic generic marker as a fallback; specialized objects do not receive a second hidden generic marker underneath their art.

The integrated Cow scenario binds Cow to the creature renderer and Grass/Clover/Dandelion to one shared vegetation renderer with different variants. There is no simulation-side `if definition == cow/grass` branch.

Vegetation density reads authoritative `ConsumableStock.quantity/capacity`, so depletion and regrowth become visible automatically. Creature facing reads `OrientationLookup`. The grazing pose activates only while `AgentIntentTrace.phase == USING_OPPORTUNITY`; its timing therefore follows simulation, not wall-clock animation state.

## Living-world inspector

Selecting a cell shows authoritative structural context and the number of objects currently stacked in that cell. Repeated LMB on the same cell cycles through the stack, which allows inspecting both an actor and a co-located interaction source.

For the selected object the HUD can expose, when present:

```text
presentation name / description
ObjectId + definition + XYZ + facing
all open Needs and current/max levels
next Need-progression evaluation
finite stock quantity/capacity
next Growth evaluation + last applied Growth
Vision range/FOV/visible-object count
current autonomous intent phase
authoritative timed-use progress/completion tick
search state
last decision, winner and top candidates
current target
```

The inspector does not infer these facts from sprites. It reads the same `SimulationView` capabilities used by production systems.

### Presentation cache boundary

Manual Living Cow acceptance exposed a presentation-only performance problem: the inspector and Vision overlay were rebuilding allocation-heavy authoritative snapshots on every render frame even though simulation state changes only on simulation ticks.

The visualizer now caches selected-object inspector data and `VisionSnapshot` results by:

```text
selected object / selected cell
simulation tick
relevant presentation selection state
```

This cache does **not** become simulation truth. A new simulation tick or changed selection causes the presentation to read authoritative state again. The purpose is only to avoid reconstructing identical read-only diagnostics dozens of times between two simulation ticks.

This distinction is important: optimization occurs at the observer boundary and does not change the frequency or fidelity of authoritative simulation updates.

## Vision diagnostics

Selecting an object with Vision automatically visualizes the simulation's authoritative `VisionSnapshot`.

The renderer does **not** reconstruct a decorative FOV cone from presentation-side math.

Current diagnostics:

```text
soft cell highlight   cells Vision says are visible now
object frame          objects Vision says are visible now
facing arrow          authoritative physical orientation
HUD Vision data       range / FOV / visible counts
```

Occlusion, FOV and range changes therefore appear automatically because presentation reads the same result used by autonomous decision making.

Between simulation ticks the selected-object snapshot may be reused by presentation because authoritative state has not advanced. This avoids per-frame allocation without changing Vision semantics.

## Active route diagnostics

Selecting any object with an active production `MoveTo` automatically shows its current planned route.

`MoveToRouteDiagnosticRenderer` reads `MoveToLookup.activeRoute(...)`; it does not invoke Pathfinder or reproduce route logic in presentation.

The overlay is reason-agnostic. The same presentation works for search exploration, movement toward food/water, future autonomous intents and external MoveTo consumers.

## Agent diagnostics

### Living Cow Cycle

This is the first integrated living-world visual acceptance scenario. It starts with a satisfied Cow on a substantially larger sparse meadow.

Every food source begins outside the Cow's initial Vision. Hunger must first cross the configured autonomous motivation threshold; only then can the Cow search. The acceptance flow is therefore:

```text
Hunger progression
    ↓
meaningful motivation threshold
    ↓
local Vision sweep
    ↓
unguided EXPLORING + physical relocation
    ↓
food actually enters Vision
    ↓
Decision winner
    ↓
MoveTo
    ↓
provider-owned timed grazing
    ↓
Hunger down + biomass down
    ↓
Growth restores biomass
    ↓
cycle repeats
```

The meadow is 37x29 cells and contains sparse Grass, Clover and Dandelion patches at different directions/distances. Initial camera framing intentionally does not zoom out far enough to make the entire world a tiny overview; normal pan/zoom remains available.

Food definitions have deliberately small finite initial stock. A patch can be depleted by feeding, which forces the next motivated cycle to seek another available source while depleted plants regrow independently.

While grazing, Need and stock remain unchanged until the authoritative provider completion tick. If the same source is still desirable and available after a completed use, Agent can continue another provider-owned use without dropping through an artificial idle frame. The procedural Cow therefore keeps the chewing pose continuously across such consecutive uses because `USING_OPPORTUNITY` itself remains continuous.

The inspector progress percentage is derived from `AgentIntentTrace.startedTick/expectedCompletionTick`; no presentation timer invents interaction duration.

### Focused agent scenarios

`Cow Foraging` remains the focused candidate-selection proof.

`Cow Visual Search` remains the focused information-seeking proof. It exposes local sweep, unguided exploration and active search MoveTo without giving Search hidden Grass coordinates.

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
- `LMB`: select cell/object; repeated click cycles co-located object stack.

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

The first Living Cow manual acceptance provided a concrete profile:

- authoritative simulation `update` was normally a small fraction of a millisecond;
- selected-object HUD/Vision diagnostics became the sustained per-frame hot path because identical snapshots were rebuilt between simulation ticks;
- large zoom/pan changes could independently trigger expensive one-off landscape analysis cache misses.

The first issue is addressed with presentation-tick caches. Landscape cache-miss behavior remains a separate profiled concern; it should not be confused with simulation cost or prematurely solved by changing world semantics.

Focused correctness scenarios and representative performance scenarios remain separate concerns.

## Testing boundary

Headless simulation tests own semantic correctness. In particular timed opportunity-use coverage pins that Need/stock do not mutate before the authoritative completion tick, motivation thresholds prevent trivial-deficit action, and still-desired repeated uses remain continuously committed.

The Living Cow scenario test additionally pins that no food is initially visible, `EXPLORING` is observed, the Cow physically expands search away from its start, and only later reaches a real timed plant use.

Visualizer scenario/catalog tests verify meaningful setup/order and that presentation exposes the same authoritative diagnostic state. Final appearance/readability and real desktop performance of the Living Cow Cycle remain mandatory manual acceptance checks before merging the milestone.

See [Debug Scenarios Guide](../guides/debug-scenarios.md) when adding a new human-observable development scenario.

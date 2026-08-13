# Visualizer

## Purpose

Observe the real authoritative simulation during development. The visualizer is not a second world model and never owns simulation truth.

The debug application is scenario-driven: one generic visualizer is reused across small deterministic worlds that each demonstrate one understandable mechanic or interaction. Scenarios improve human debugging and explanation; they do not replace headless correctness tests.

## Runtime boundary

`ZLevelVisualizer` receives only:

```text
SimulationView
SimulationTime
SimulationStepper
```

An executable boundary test protects this constructor contract.

`SimulationView` also exposes read-only Occupancy state, so presentation can diagnose dynamic space conflicts without receiving the mutable Occupancy owner.

`ZLevelVisualizer` exposes its presentation input processor to the application shell, but it does not own global `Gdx.input` routing. This allows scenario-level controls such as restart/back to compose with the existing camera/time/debug controls without teaching the simulation visualizer about scenario lifecycle.

## Scenario lifecycle

The application starts at a small scenario selector rather than constructing one permanent demo world.

```text
Main
  ↓
ScenarioCatalog
  ↓
VisualizerScenario
  ↓
ScenarioSession
  ├─ fresh SimulationRuntime
  └─ initial ScenarioView
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

It cannot alter simulation mechanics.

Current focused scenarios are:

- `Z-Level / Cutaway` — caves, roofs, lower surfaces and a deep shaft without movers;
- `Ramp Navigation` — four directional ramps and a successive vertical ramp chain for F2/F3 inspection;
- `Timed Movement` — slow and fast movers performing the same one-cell move on simple flat terrain;
- `Occupancy Contention` — two exclusive movers competing for one immediate destination for F5 inspection;
- `Pathfinding / Straight` — minimal flat exact route;
- `Pathfinding / Structural Detour` — structural topology forces a detour;
- `Pathfinding / Weighted Detour` — lower intrinsic cost wins over fewer cells;
- `Pathfinding / 3D Ramps` — minimal two-level vertical route;
- `Pathfinding / Multi-Level Climb` — four successive ramps climb from standing Z0 to Z4;
- `Pathfinding / Z Switchback` — a multi-level route turns through +X, +Y and -X ramp orientations;
- `Pathfinding / Vertical Overpass` — start and goal share Z0, but the only route climbs to Z2, crosses and descends;
- `Pathfinding / Unreachable` — structured `NO_PATH`;
- `Pathfinding / Hierarchy` — long route crosses derived hierarchy cluster boundaries;
- `Pathfinding / Dynamic Invalidation` — a search starts on an open corridor, terrain changes on that corridor, and the suspended search becomes `STALE` rather than mixing traversal revisions.

A scenario should be added when a behavior is useful to understand or inspect visually. There is no requirement to mirror every unit test with a scenario.

### Restart

`R` restarts the active scenario by discarding the current presentation/session and calling the same scenario's `create()` again.

```text
R
 ↓
dispose current ScenarioScreen / ZLevelVisualizer
 ↓
VisualizerScenario.create()
 ↓
new SimulationRuntime
 ↓
new ZLevelVisualizer
```

Restart does **not** clear or mutate the existing world in place. Simulation systems therefore need no debug-only `reset()` APIs, and a restart naturally recreates Clock, Scheduler state, object identities, movement actions, reservations, terrain and geometry from the scenario definition.

Scenarios are deterministic. Restart repeats the same scenario definition and seed; a future “new seed” action, if needed, is a separate operation.

`Esc` leaves the active scenario and returns to the selector.

## Presentation ownership

The visualizer is split by responsibility:

```text
Main                         application screen switching
ScenarioMenuScreen           scenario selection
ScenarioScreen               active scenario lifecycle + R/Esc controls
ScenarioCatalog              ordered available scenarios
VisualizerScenario           creates one fresh deterministic simulation world
ZLevelVisualizer             generic render-order orchestration
VisualizerState              selected Z, overlay modes, selection
VisualizerCamera             pan, zoom, viewport and coordinate conversion
VisualizerInputController    physical input → presentation/time controls
LandscapeRenderer            terrain/cutaway rendering + analysis cache
VisualizerOverlayRenderer    grid, Z perimeter, F2/F3/F5, object/selection overlays
VisualizerHudRenderer        status and inspector screen-space UI
ShapePresentationRegistry    exact-type Shape presentation dispatch
```

No component above becomes an authoritative simulation owner.

## Typed Shape presentation

Generic presentation does not branch on concrete Shape classes. `ShapePresentationRegistry` dispatches by exact Java type to specialized bindings registered in the presentation composition root.

Current bindings:

- `FullShapePresentation`
- `RampShapePresentation`

A binding supplies the currently needed Shape-specific presentation facts: terrain region, optional direction diagnostic and inspector label. Procedural art itself no longer imports `RampShape`; the ramp binding translates `riseX/riseY` into art parameters.

This prevents `instanceof`/concrete-class chains from spreading through renderer, F3 diagnostics and inspector while keeping presentation concerns out of simulation `Shape`.

## Horizontal Z cut

Visualizer Z is the standing/navigation plane. For each XY:

```text
terrain at selected Z       → SOLID_BODY
terrain at selected Z - 1   → CURRENT_SURFACE
nearest visible terrain down through open volume → LOWER_SURFACE
otherwise                   → EMPTY
```

Visibility is derived from geometry, not labels such as “cave” or absolute Z.

Resolved context includes body depth, drop depth, ceiling distance, cover depth and shortest open-volume exposure distance to sky-connected exterior air.

## Cutaway presentation

- current surface keeps the natural terrain palette;
- lower surfaces fade nonlinearly with drop depth;
- cover/body depth darkens more strongly;
- sufficiently deep space approaches background darkness and loses visual detail;
- ramps keep one visual identity across slice context;
- only the outer cardinal boundary of `CURRENT_SURFACE` receives the active-Z perimeter, so there is no per-tile active grid.

## Procedural landscape art

The canonical development landscape is generated in memory at 16×16 native pixels per logical cell. Surface topology uses normalized 8-neighbor masks with diagonal corner gating. Visual variants are deterministic from XYZ.

Ramp geometry rotates while lighting remains fixed in world-space, preventing rotated-light artifacts. Atlas padding prevents texture bleeding.

Near zoom uses `Nearest`; far zoom switches procedural textures to `Linear` to reduce sampling shimmer while camera motion stays continuous.

## Diagnostics and controls

Application/session controls:

- scenario selector: `Up/Down`, `Enter` or click;
- `R`: recreate the active scenario from a fresh runtime;
- `Esc`: return to the scenario selector.

Generic visualizer controls inside a scenario:

- `Space`: run/pause simulation time;
- `N`: single simulation step while paused;
- `WASD`: pan;
- mouse wheel: zoom;
- `PgUp/PgDn`: change standing Z;
- `G`: grid off/subtle/debug;
- `F2`: authoritative Navigation transition overlay;
- `F3`: Shape direction diagnostics supplied through typed presentation bindings;
- `F4`: lower-surface visibility depth `0 / 1 / 4 / 8`;
- `F5`: Occupancy overlay at the selected standing Z;
- click: selected cell/object at the current standing Z;
- HUD: tick, Z, FPS, zoom/sampling and inspector context.

The active scenario title, description and `R`/`Esc` reminder remain visible at the bottom of the scenario screen. Pathfinding scenarios additionally use generic presentation-only cell markers for start, goal, route and scenario warnings; those markers do not become simulation state.

The F5 overlay reads `OccupancyLookup` only when enabled:

```text
OCCUPIED  bright outer cell frame
RESERVED  inset amber frame + X marker
FREE      no marker
```

The selected-cell inspector also reports the exact `FREE / OCCUPIED / RESERVED` state. This makes the distinction between physical presence and an in-flight destination claim directly observable while debugging Movement conflicts.

## Performance

Two complementary telemetry streams are logged once per second:

```text
VisualizerPerf
    visible cells
    landscape CPU avg/max
    exposure-analysis CPU avg/max
    analysis cache hit/miss
    padding / cache occupancy / cached Z radius

VisualizerFramePerf
    observed frame interval avg/max
    total CPU work inside ZLevelVisualizer.render avg/max
    update / landscape / overlay / HUD CPU avg/max
    Java heap min/max/current
    native heap
```

The observed frame interval includes pacing/vsync effects, while the CPU stage timings cover work inside the visualizer render method. A large frame-time spike with low CPU-stage maxima therefore points outside those measured CPU stages; a matching landscape/analysis, overlay or HUD spike identifies the responsible presentation path directly.

Exposure uses a primitive dense distance field. Its BFS queue is a reusable resolver-owned scratch buffer, so a camera-local analysis rebuild allocates the new cached distance field but does not also allocate another same-sized temporary queue. Analysis windows are padded relative to viewport size, cached by authoritative visibility revision and built for a nearby standing-Z band so normal camera motion and Z switching reuse work.

The selected-cell inspector caches its expensive slice/exposure result by selected XYZ, F4 lower depth and visibility revision. Dynamic facts such as Occupancy are still read live every frame, while an unchanged inspected cell no longer rebuilds a private exposure field every render.

Hot sparse read paths avoid temporary coordinate-key allocation. This includes Terrain, the Spatial cell index and Occupancy reservation lookup; immutable coordinate keys are still used for stored authoritative entries.

Occupancy scanning is diagnostic-only and is performed over the visible selected-Z cells only while F5 is enabled. No Occupancy render cache is introduced without profiling evidence.

Focused correctness scenarios and future representative performance scenarios are separate concerns. A scenario intended to explain one mechanic should not become a large benchmark world; representative scale profiling gets its own explicit workload when needed.

## Testing boundary

Headless tests cover cut priority, current-surface query, depth/cover/exposure geometry, sealed/open caverns, future non-terrain occlusion, cache retarget correctness, deterministic topology and presentation dependency boundaries. Occupancy correctness itself is tested in the headless simulation layer; the F5 visual styling remains manually inspected.

Scenario tests independently verify the meaningful world setup behind cutaway geometry, ramp topology, timed movement, occupancy contention and catalog freshness. Pathfinding scenario tests verify expected terminal outcomes and also require the dedicated vertical scenarios to expose route markers across multiple standing-Z slices. UI selection/restart aesthetics and interaction are manually inspected in the desktop visualizer.

See [Debug Scenarios Guide](../guides/debug-scenarios.md) when adding a new human-observable development scenario.

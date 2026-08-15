# Visualizer

## Purpose

Observe and interact with authoritative simulation state during development without creating a second world model.

The visualizer is scenario-driven: one generic renderer/interaction shell is reused across small deterministic worlds that demonstrate focused mechanics or cross-system behavior. Scenarios complement headless tests; they do not replace them.

## Runtime boundary

`ZLevelVisualizer` is constructed from read-oriented runtime capabilities:

```text
SimulationView
SimulationTime
SimulationStepper
optional ObjectPresentationBindings
```

Command-capable interaction is configured explicitly after construction through presentation-side `VisualizerCommandSink` bindings. Presentation never receives mutable domain systems simply because a button needs to request a command.

`SimulationView` exposes read-only Object/Spatial, Geometry/Landscape, Water/Soil, Navigation, Occupancy, MoveTo, agent and other diagnostic capabilities. The visualizer reads those contracts rather than reconstructing domain truth.

## Scenario lifecycle

The application starts in a grouped/searchable scenario browser:

```text
ScenarioCatalog
  ├─ Geometry & Navigation
  ├─ Movement
  ├─ Occupancy
  ├─ Water / Hydrology
  ├─ Agents
  └─ Pathfinding
        ↓
VisualizerScenario.create()
        ↓
ScenarioSession
  ├─ fresh SimulationRuntime
  ├─ initial ScenarioView
  ├─ optional ScenarioController
  ├─ optional presentation bindings / portals / weather
  └─ command adapter
        ↓
ScenarioScreen
        ↓
ZLevelVisualizer
```

Scenario packages currently include `geometry`, `movement`, `occupancy`, `pathfinding`, `water`, `environment` and `agent`. Package ownership and browser grouping need not be one-to-one: `RainHydrologyScenario`, for example, is environment composition shown under **Water / Hydrology**.

`R` recreates the scenario by discarding the old session and calling `create()` again. Simulation systems therefore need no debug-only global reset API.

## Presentation perspectives

The visualizer has three explicit presentation perspectives. None changes simulation coordinates, Navigation or Movement topology.

### SURFACE

Default open-world view.

`SurfaceProjectionResolver` resolves the highest authoritative terrain surface per visible XY column. Terrain, Water and objects are presented from that surface projection; covered lower-Z content is not drawn through higher terrain.

Surface view is presentation only. There is no flattened authoritative surface world.

`SurfaceLandscapeRenderer`, `SurfaceCliffRenderer` and `WaterRenderer.drawSurface(...)` operate on camera-visible XY cells. Optional Height contours are a separate diagnostic overlay.

### INTERIOR

An explicit presentation-local covered-space view entered through `ViewPortal` metadata.

Portal metadata changes camera/presentation context only. It does not teleport objects, create Navigation edges or bypass physical walls/doorways. Ordinary `MoveTo` still uses the real world topology.

Interior bounds constrain what the presentation exposes; they are not authoritative simulation bounds.

### DEBUG_SLICE

Development-only standing-Z/cutaway perspective, toggled with `F7` when not inside an Interior.

`PgUp/PgDn` change standing Z only in Interior/Debug Slice. Surface view remains surface-projected rather than pretending its selected Z is a global world slice.

## Rendering order

The current orchestration is approximately:

```text
surface or slice landscape
    ↓
Water
    ↓
Surface relief / grid
    ↓
portal hints
    ↓
object presentation
    ↓
debug overlays + MoveTo/Vision diagnostics
    ↓
interaction feedback
    ↓
screen-space rain
    ↓
status / selected-cell inspector / view HUD / F1 panel / context menu
```

Every layer is presentation state only.

## Cell-centric interaction

LMB belongs to the **world cell**, not to individual glyph hitboxes.

Every left click first updates selected-cell/object inspection. Object and portal actions are then composed for that cell. Repeated clicks on a multi-object cell cycle through the authoritative object stack.

Current object actions include:

```text
Move
Cancel move   when MoveTo is active
```

Current portal actions include:

```text
Enter
Return surface
Move here     while targeting inside an Interior
```

If an object and portal share a cell, one context menu combines both sets of actions.

RMB closes the context menu or cancels an unfinished Move-target draft.

### Move targeting and preview

Starting `Move` keeps the selected mover even while destination cells are inspected/hovered.

Hover preview is mover-aware and advisory. `VisualizerInteractionController` advances a disposable `PathSearch` with a bounded 512-expansion budget per render frame while targeting. The preview never reserves cells and never becomes execution truth.

Submitting a destination goes through `VisualizerCommandSink.moveTo(...)`. Real Movement/Occupancy/Water constraints remain authoritative.

`Cancel move` goes through the production cancellation command. The visualizer does not delete Movement action state directly.

### Esc/back chain

`Esc` backs out one presentation interaction layer at a time:

```text
context menu
    ↓
unfinished Move target
    ↓
Interior
    ↓
Debug Slice
    ↓
hosting ScenarioScreen/browser
```

These states are independent rather than one overloaded mode flag.

## Debug overlays

`F1` toggles the checkbox panel. Current independent options are:

- Grid;
- Height contours;
- Move route;
- Transitions;
- Shape directions;
- Occupancy;
- Vision;
- Technical inspector.

The panel is immediate-mode and shares one top-right layout flow with the selected-cell inspector so the two panels do not overlap.

Legacy/fast keyboard shortcuts remain:

```text
G   cycle grid mode
F2  Transitions
F3  Shape directions
F4  lower-surface slice depth
F5  Occupancy
F6  Technical inspector
F7  Debug Slice
```

Overlay state is independent from Surface/Interior/Debug Slice perspective.

## Selected cell/object inspector

The normal right-side card reports facts that correspond to the selected object/cell and current presentation surface.

For an object it includes identity/presentation name, authoritative XYZ and whether a MoveTo is active.

Cell facts include:

- selected XYZ;
- visible/current Terrain definition and real terrain Z;
- visible Water amount and real Water Z;
- Water amount / geometric cell capacity with a graphical progress bar;
- bounded optical Water depth in Surface view;
- SoilMoisture amount / effective local Soil capacity with a graphical progress bar;
- explicit `n/a (non-absorbing terrain)` when the supporting terrain has no Soil hydrology;
- object count in the selected standing cell.

Technical mode additionally exposes current Soil infiltration limit, `SurfaceWaterStorage`, Shape class, Occupancy state, Navigation transition count and selected object id where applicable.

The inspector reads `SimulationView`; it does not maintain a second hydrology or topology model.

## Water presentation

Water quantity/depth comes from authoritative Water + Geometry.

Surface opacity is derived from bounded contiguous vertical optical depth; the resolver caps visual accumulation rather than scanning arbitrarily deep columns.

Motion uses `WaterMotionResolver`, which reads sparse latest-step `WaterFlowLookup` produced by real hydraulic transfers:

```text
no actual transfer sample -> CALM
horizontal transfer       -> WEST / EAST / SOUTH / NORTH
negative-Z transfer       -> FALLING
```

The renderer does not infer flow merely because a theoretical hydraulic slope exists. Calm Water is rendered with a fixed frame (`presentationFrame(CALM) == 0`), while active cardinal/falling Water animates from presentation time and deterministic spatial phase.

## Rain presentation

Rain is fixed-budget screen-space presentation, not world raindrop entities.

`RainRenderer` owns at most 160 deterministic streak seeds. Weather intensity changes active streak count/opacity, and a restrained screen veil makes the weather state readable. There are no per-frame raindrop allocations and particle count does not scale with world size.

Physical precipitation remains simulation state driven by the scenario/runtime schedule. In Rain Cycle, visual weather reads the same `PrecipitationSchedule` active window so visible rain and physical input begin/end together.

## Object and Shape presentation

Simulation definitions do not contain renderer classes. `ObjectPresentationBindings` map definition ids to presentation-only metadata/families.

Generic Shape presentation uses `ShapePresentationRegistry`, where exact concrete Shape knowledge is localized to typed presentation bindings registered by the presentation composition root. Generic renderers do not grow `instanceof RampShape/...` decision chains.

## UI assets

Current developer UI fonts are generated at startup from the packaged `assets/ui/ui-font.ttf` using libGDX FreeType (`gdx-freetype`). Body/title sizes are currently 22/25 px, with integer glyph positioning and linear texture filtering.

This is presentation infrastructure only. Font generation/atlas representation may change without affecting simulation contracts.

## Controls

Application/session:

- `R` — recreate current scenario;
- `Esc` — back out through interaction/view state, then return to browser.

Generic visualizer:

- `Space` — run/pause simulation;
- `N` — one simulation step while paused;
- `WASD` — pan;
- mouse wheel — zoom;
- `PgUp/PgDn` — standing Z in Interior/Debug Slice;
- `G`, `F1`..`F7` — grid/debug/view controls described above;
- `LMB` — inspect/select cell and open applicable context actions;
- `RMB` — close menu/cancel unfinished Move targeting.

The compact status HUD reports run state, tick, FPS and zoom. View state is shown separately rather than encoding simulation meaning into the HUD.

## Performance boundary

World rendering is camera-local. Water optical depth is bounded, rain particle budget is fixed, Move preview is active only while targeting and expansion-budgeted, and UI is immediate-mode.

`VisualizerPerformanceTelemetry` separates observed frame interval from CPU work inside major renderer stages. Optimize only measured hot paths and keep presentation optimizations semantically invisible to simulation.

## Testing boundary

Headless simulation tests own semantic correctness. Visualizer tests cover deterministic presentation math/resolution, interaction state, route preview boundaries, debug-panel layout, Water motion/opacity mapping, rain density and scenario setup.

Aesthetic readability and real desktop performance remain manual acceptance where automated tests would only imitate a human judgement.

See [Debug Scenarios Guide](../guides/debug-scenarios.md), [Typed Presentation Bindings decision](../decisions/004-typed-presentation-bindings.md), [Water](water.md) and [Movement](movement.md).

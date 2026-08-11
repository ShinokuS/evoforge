# Minimal Z-level Visualizer

The first EvoForge visualizer is a **debug observer of the real simulation**, not a second world model and not a game UI.

Its purpose is to make already-implemented systems visible before Pathfinder, Occupancy and the first agent vertical slice are added.

## Module boundary

```text
simulation  pure Java, authoritative and headless
    ↑
core        libGDX presentation/debug visualizer
    ↑
lwjgl3      desktop launcher
```

`core` depends on `simulation`. `simulation` has no dependency back on libGDX or presentation code.

The production composition root is `SimulationAssembly`. It is a setup phase used to register definitions and build initial terrain, Shapes and objects. `start()` closes setup mutation and returns `SimulationRuntime`.

A started runtime exposes command submission, `SimulationTime`, `SimulationStepper` and a read-only `SimulationView`. The view contains only observation capabilities such as Terrain, Geometry, Object, Transform, Navigation and cell-object lookup.

The visualizer never receives `SpatialSystem`, `TerrainSystem`, `GeometrySystem`, `MovementSystem`, `Scheduler` or `SimulationClock`.

## Z-plane meaning

The visualizer Z coordinate is the **standing/navigation plane**, because Spatial objects and Navigation transitions live at that coordinate.

Under the current supported-position Shape law, the terrain/Shape supporting standing position `(x,y,z)` is read at:

```text
(x, y, z - 1)
```

Therefore a selected standing plane `Z` renders:

```text
current plane:
    objects at Z
    support terrain/Shape at Z - 1

lower context plane:
    no objects
    dimmed support terrain/Shape at Z - 2
```

The lower plane is presentation context only. It does not alter visibility, Navigation or simulation truth.

A `RampShape` is drawn on the standing plane it supports and receives an explicit rise-direction arrow. Presentation may know concrete Shape types for diagnostics; Navigation remains generic and has no `instanceof RampShape` branch.

## Time

Rendering FPS and simulation ticks are independent.

The visualizer owns a small fixed-step accumulator. It is the only component that maps real frame time to requests for production ticks:

```text
real frame delta
    ↓
VisualizerTimeController
    ↓
SimulationStepper.advance()
```

`SimulationStepper` remains the only production operation used to advance one simulation tick.

The first visualizer supports:

- paused state;
- `N` for exactly one tick while paused;
- `Space` for run/pause at one debug speed.

The application starts paused so the tick-zero state and already-started timed Movement actions can be inspected before anything completes.

## Rendering and inspection

The current view deliberately uses primitive debug graphics rather than making sprite art part of the simulation contract.

Implemented diagnostics:

- orthographic camera;
- `WASD` pan;
- mouse-wheel zoom;
- `PageUp` / `PageDown` standing-Z selection;
- selected plane plus dimmed lower plane;
- default/full terrain distinction from Ramp terrain;
- Ramp rise-direction arrow;
- objects read through `CellObjectLookup` only for visible cells;
- left-click cell selection;
- live outgoing structural-transition overlay from `NavigationLookup`;
- object selection when the clicked cell contains an object;
- HUD with tick, standing Z and time mode;
- object inspector with `ObjectId`, definition id and authoritative XYZ.

Transition overlay colors distinguish flat, upward and downward edges. The overlay is a view of the same primitive mask Movement currently consumes.

## Demo world

`VisualizerDemoWorld` is presentation-owned deterministic setup content. It is not part of simulation semantics and is not the test-only Scenario Harness.

It builds:

- a flat platform on standing `Z=0`;
- a real positive-Y Ramp sample connecting standing `Z=-1` to `Z=0`;
- one slow and one fast mover on the flat platform.

Both movers start an equal-cost adjacent `MoveStep` at tick zero. The fast mover completes after 2 ticks and the slow mover after 8 ticks, proving that the visualizer observes authoritative timed Movement rather than moving sprites itself.

Headless tests verify the Ramp transitions and this timing difference without creating a libGDX window.

## Performance boundary

The renderer iterates only the camera-visible XY range rather than scanning the whole object repository. Terrain/Geometry reads are intentionally left on the current sparse storage path so the visualizer becomes a representative workload for later profiling.

No storage/chunk optimization is introduced merely because lookup allocation is suspected. Allocation rate, GC and frame cost should be measured with JFR on the live visualizer before changing sparse-coordinate representation.

## Deliberate omissions

The first visualizer has no:

- movement interpolation;
- Scene2D game UI;
- path overlay;
- transition-cost heatmap;
- Occupancy display;
- action-completion inspector;
- roofs/cutaway logic;
- presentation-driven world mutation;
- semantic assumption that positive/negative Z means above/below ground.

External tilesets such as DawnLike can replace primitive presentation later without changing the runtime/view boundary.

## Next consumers

The visualizer is intended to remain useful while the next simulation milestones arrive:

```text
Occupancy
    ↓
Pathfinder
    ↓
first agent / Cow vertical slice
```

Those systems may add debug overlays, but they should consume their own authoritative read contracts rather than turning the visualizer into an owner of simulation state.

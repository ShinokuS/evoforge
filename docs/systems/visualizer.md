# Visualizer

## Purpose

Observe the real authoritative simulation during development. The visualizer is not a second world model and never owns simulation truth.

## Runtime boundary

`ZLevelVisualizer` receives only:

```text
SimulationView
SimulationTime
SimulationStepper
```

An executable boundary test protects this constructor contract.

`SimulationView` now also exposes read-only Occupancy state, so presentation can diagnose dynamic space conflicts without receiving the mutable Occupancy owner.

## Presentation ownership

The accepted visualizer is split by existing responsibility:

```text
ZLevelVisualizer             lifecycle + render-order orchestration
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

## Diagnostics

- `G`: grid off/subtle/debug
- `F2`: authoritative Navigation transition overlay
- `F3`: Shape direction diagnostics supplied through typed presentation bindings
- `F4`: lower-surface visibility depth `0 / 1 / 4 / 8`
- `F5`: Occupancy overlay at the selected standing Z
- click: selected cell/object at the current standing Z
- HUD: tick, Z, FPS, zoom/sampling and inspector context

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

## Testing boundary

Headless tests cover cut priority, current-surface query, depth/cover/exposure geometry, sealed/open caverns, future non-terrain occlusion, cache retarget correctness, deterministic topology and presentation dependency boundaries. Occupancy correctness itself is tested in the headless simulation layer; the F5 visual styling remains manually inspected.

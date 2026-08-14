# Hydrology Visual Acceptance

## Goal

Make finite surface Water and configured Rain immediately readable in the development visualizer without adding a second simulation, particle world or persistent presentation state per cell/drop.

## Water presentation

The visualizer reads authoritative `WaterLookup` and `WaterSurfaceLookup` only for the camera-visible XY range.

- one shared procedural 16x16 atlas contains four Water animation frames;
- cell opacity is derived from current finite Water amount divided by neutral geometric capacity;
- neighbouring cells share one global animation phase with a deterministic coordinate offset;
- no Water animation component/state is stored in simulation or per rendered cell;
- no reflections, refractions, fluid mesh, framebuffer post-processing or flow-vector field are used in this slice.

If the world has no wet columns, the Water renderer returns before scanning the visible range.

## Rain presentation

Rain is a fixed-budget screen-space effect driven by a presentation-only weather lookup.

- `Clear` exits immediately;
- `Rain` draws one subtle atmosphere veil plus at most 96 deterministic streaks;
- streak seeds are allocated once when the renderer is created;
- no raindrop entities or per-frame particle allocations exist.

The presentation weather contract is intentionally separate from physical precipitation cadence. The current simulation has periodic precipitation rather than a full atmospheric weather-duration model; a future Weather domain can adapt into the same presentation lookup.

## UI

The existing scenario status line includes `Weather: Clear` or `Weather: Rain <intensity>%`. This reuses the existing UI draw pass rather than introducing another widget renderer.

## Acceptance scenario

The visualizer catalog contains `Environment -> Rain & Water`.

The scene starts after a deterministic hydrology prewarm. A small basin contains surfaces with different infiltration behavior so Water opacity differences and continued hydraulic relaxation can be observed immediately. FullShape terrain walls contain the test water; they are not world-bound semantics and exist only as explicit terrain in this scenario.

Press `Space` to continue simulation time and watch rainfall continue feeding the finite Water system.

## Performance rule

The first visual slice intentionally prefers a small bounded cost over visual richness. Any future splash, ripple, flow-direction or lighting enhancement should remain derived/read-only and should be justified by representative profiling before adding persistent state or world-wide work.

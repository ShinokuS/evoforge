# Hydrology Visual Acceptance

## Goal

Keep finite Water readable and testable in the development visualizer without creating an unbounded hydraulic workload or a second presentation simulation.

## Finite world boundary

Hydrology acceptance worlds configure explicit inclusive XYZ `WorldBounds` through `SimulationAssembly.worldBounds(...)`.

`WorldGeometryLookup` composes the ordinary landscape geometry with that objective world fact. Coordinates outside configured bounds present `FullShape` to Geometry consumers, so Water, Navigation, Movement and traversal cannot treat the exterior as open space. Assemblies that do not configure bounds preserve the historical unbounded-world behavior.

The boundary is not represented by generated wall terrain and does not require Water-specific edge checks.

## Water presentation

The visualizer reads authoritative `WaterLookup` and uses `WaterSurfaceLookup` only as a sparse XY early-out.

- one shared procedural Water atlas is reused by every visible cell;
- cell opacity is derived from current finite Water amount divided by neutral geometric capacity;
- all visible neighbouring Water cells share one animation phase;
- cutaway rendering resolves the nearest visible Water cell at or below the selected Z instead of assuming the topmost wet cell is the drawable layer;
- no Water animation component/state is stored in simulation or per rendered cell.

If the world has no wet columns, the Water renderer returns before scanning the visible range.

## Compact stress suite

The `Water / Hydrology` group intentionally contains only three visual scenarios.

### Rain Cycle

The only continuously climate-driven acceptance world. It covers:

- intermittent precipitation;
- soil-first infiltration;
- low-infiltration puddles;
- impermeable runoff;
- roof / highest-surface shielding;
- exposed Water and retained-moisture evaporation;
- long-term non-flooding balance.

For acceptance math only, one tile is treated as a 1 m x 1 m footprint and one full normalized cell as 1 m water depth. Therefore 1 mm of water depth equals 1000 normalized volume units.

One 240-tick climate cycle is treated as one scenario day for balancing rates:

- rain event: 3000 units = 3.0 mm;
- evaporation: 20 units per exposed column per tick, at most 4.8 mm over the cycle before precipitation-tick suppression;
- loam infiltration limit: 3.0 mm per rain event;
- compacted-clay infiltration limit: 0.8 mm per event.

The deliberately negative free-water balance means puddles should dry before the next rain pulse instead of accumulating toward a flood. This is a dimensionally meaningful acceptance climate, not yet a full meteorological evapotranspiration model.

### Water Z Flow

Rain-free deterministic setup using exact initial Water. It combines:

- a real multi-Z deep pool;
- a vertical falling shaft;
- one-edge-per-update propagation;
- Z cutaway continuity.

### Water Geometry Stress

Rain-free bounded horizontal stress combining:

- symmetric multi-exit flow;
- a long FullShape barrier and detour/equalization path;
- Ramp low-face admission;
- Ramp high-face blocking;
- finite spreading inside a closed map.

Existing lower-level tests remain the authority for exact conservation, deterministic mutation order, integer deadband convergence, dormancy/wake, capacity displacement and mover-specific Water traversal.

## Performance rules

Hydrology acceptance intentionally bounds work in several independent ways:

- finite world bounds cap the maximum hydraulic frontier;
- non-climate stress worlds use exact initial Water rather than repeated precipitation;
- rain occurs only once per long climate cycle;
- Water presentation has no per-cell persistent animation state;
- Water diagnostics recompute only when the simulation tick changes, never once per render frame while paused;
- rain remains a fixed-budget screen-space effect.

Any future ripple, splash, flow-direction or lighting enhancement should remain derived/read-only and should be justified by profiling before adding persistent state or world-wide work.

# Hydrology Visual Acceptance

## Goal

Make finite Water and configured Rain immediately readable in the development visualizer without adding a second simulation, particle world or persistent presentation state per cell/drop.

## Water presentation

The visualizer reads authoritative `WaterLookup`, `WaterSurfaceLookup` and neutral `GeometryLookup` only for the camera-visible XY range.

- one shared procedural 16x16 atlas contains six Water animation frames;
- cell opacity is derived from current finite Water amount divided by neutral geometric capacity;
- all visible neighbouring Water cells share one global animation phase so tile boundaries read as one coherent surface;
- a presentation-only `WaterSliceResolver` resolves the nearest wet XYZ cell visible through the selected horizontal cut and configured lower-depth band;
- completely solid geometry stops that vertical lookup, so Water does not leak visually through roofs or full terrain;
- Water above the selected cut is hidden, while Water below it remains visible through actually open cutaway space;
- deeper visible Water is progressively dimmed to make Z separation readable without creating per-cell state;
- no Water animation component/state is stored in simulation or per rendered cell;
- no reflections, refractions, fluid mesh, framebuffer post-processing or flow-vector field are used in this slice.

`WaterSurfaceLookup` remains the cheap sparse XY early-out. It is not treated as the full drawable Water state because it intentionally stores only the topmost wet Z per column.

If the world has no wet columns, the Water renderer returns before scanning the visible range.

## Rain presentation

Rain is a fixed-budget screen-space effect driven by a presentation-only weather lookup.

- `Clear` exits immediately;
- `Rain` draws one subtle atmosphere veil plus at most 96 deterministic streaks;
- streak seeds are allocated once when the renderer is created;
- no raindrop entities or per-frame particle allocations exist.

The presentation weather contract is intentionally separate from physical precipitation cadence. The current simulation has periodic precipitation rather than a full atmospheric weather-duration model; a future Weather domain can adapt into the same presentation lookup.

## UI

The existing scenario status line includes `Weather: Clear` or `Weather: Rain <intensity>%`. Focused Water scenarios also expose bounded presentation-only diagnostics with total finite Water, wet-cell count, retained SoilMoisture and per-Z Water totals. These diagnostics only read authoritative state.

## Water / Hydrology acceptance suite

The visualizer catalog contains a dedicated `Water / Hydrology` group.

| Scenario | Main failure class it is meant to expose |
| --- | --- |
| `Rain & Water` | soil-first infiltration, finite surface runoff and low-cost rain presentation |
| `Water Z Stack` | deep Water occupying several Z cells and cutaway continuity while switching Z |
| `Water Vertical Fall` | one-local-step vertical propagation from an elevated runoff source |
| `Water Equalization` | hydraulic-head relaxation through one physical gate |
| `Water Symmetric Split` | deterministic bounded multi-edge outflow and ordering bias |
| `Water Ramp Gates` | Ramp low-face opening vs high-face blocking using physical Shape facts |
| `Water Barrier Detour` | no flow through FullShape barriers and no basin teleportation |
| `Water Sky Shield` | highest-surface rain targeting and shielding of lower terrain by a roof |
| `Water Evaporation Cycle` | precipitation cadence, same-tick evaporation suppression and Water-before-Soil sink behavior |

Each scenario starts from deterministic setup/prewarm and is backed by a headless assertion that the intended physical condition actually exists. The suite complements, rather than replaces, lower-level Water tests for exact mass conservation, insertion-order determinism, deadband convergence, dormancy/wake and geometry-driven displacement.

## Manual acceptance

For Water scenarios:

1. use `Space` to advance simulation time;
2. use `PgUp/PgDn` on `Water Z Stack` and `Water Vertical Fall` to verify Water remains readable across cuts;
3. compare the visible surface with the diagnostic `zN=volume` values;
4. verify walls/roofs never show Water from physically hidden cells beneath them;
5. inspect `Water Ramp Gates` from both sides of the Ramp;
6. watch `Water Symmetric Split` for directional bias;
7. watch the evaporation scenario across several rain pulses;
8. pan/zoom aggressively and compare `VisualizerPerf` with non-Water scenarios.

## Performance rule

The visual layer intentionally prefers bounded cost over visual richness. Z resolution adds only a short camera-local downward lookup bounded by the existing cutaway lower-depth setting and only for XY columns already known to contain Water. Any future splash, ripple, flow-direction or lighting enhancement should remain derived/read-only and should be justified by representative profiling before adding persistent state or world-wide work.

## Known non-scenario coverage

Some invariants are more reliable as headless tests than as a visual scene and remain covered there:

- exact conservation during redistribution;
- mutation/insertion-order determinism;
- convergence into the integer deadband and dormancy;
- explicit external mutation wake-up;
- geometry changes that reduce capacity without silently deleting Water;
- authoritative Movement/MoveTo revalidation against mover-specific wading depth.

The suite does not claim to prove mechanics that do not exist yet, such as groundwater, pressure networks, swimming, waves or finite generated-world boundary semantics.

# Hydrology Visual Acceptance

## Goal

Keep finite Water physically coherent, readable and testable without unbounded hydraulic work or a second presentation simulation.

The acceptance block now covers the complete minimal surface-hydrology contract needed before higher-level needs such as Thirst / Drink:

- finite conserved free Water;
- neutral Geometry openings and partial shapes;
- deterministic local redistribution;
- SoilMoisture retention and infiltration;
- material/coordinate-local Soil capacity;
- small surface/depression storage before horizontal runoff;
- precipitation and evaporation;
- sparse dormancy/wake;
- objective actual-flow samples for presentation.

## Finite world boundary

Hydrology acceptance worlds configure explicit inclusive XYZ `WorldBounds` through `SimulationAssembly.worldBounds(...)`.

`WorldGeometryLookup` composes ordinary landscape geometry with that objective world fact. Coordinates outside configured bounds present `FullShape` to Geometry consumers, so Water, Navigation, Movement and traversal cannot treat the exterior as open space. Assemblies without configured bounds preserve historical unbounded-world behaviour.

The boundary is not represented by generated wall terrain and requires no Water-specific edge checks.

## Free Water, SoilMoisture and surface storage

These are deliberately different facts.

`SoilMoisture` is finite Water retained inside terrain. `SoilHydrology.capacity` is the maximum retained moisture of one terrain cell, while `infiltrationLimit` bounds one local infiltration transfer.

`SurfaceWaterStorage` is still free Water. It represents small surface/depression storage held by micro-relief before horizontal runoff begins. It is not a solver epsilon and is not stored inside `FullShape`.

For horizontal flow the solver first respects the real Geometry opening/sill and then allows only the volume above local surface storage to become mobile runoff. Vertical falling ignores surface storage entirely; a shallow film above an open drop can still fall.

This gives three useful states without deleting mass:

1. dry/unsaturated terrain receives Water into SoilMoisture;
2. saturated terrain may hold a shallow free surface film without spreading it across the whole plane;
3. Water above surface storage becomes ordinary conserved runoff.

## Run-on infiltration

Precipitation is only an external Water source. It is not the only route into SoilMoisture.

`WaterSoilExchangeSystem` inspects only the sparse Water cells already activated by authoritative Water mutations. Before the next local flow solve, free Water resting on absorbent supporting terrain may transfer into SoilMoisture using the same local `SoilHydrology` limits as direct rain.

Therefore Water arriving from a neighbouring cell can wet dry ground instead of remaining as an artificial free film. The exchange removes exactly the volume added to SoilMoisture, so Water + Soil accounting remains conserved apart from explicit sources/sinks.

No world scan is introduced: stable regions sleep with the existing Water frontier.

## Deterministic local Soil properties

Material definitions keep a declarative base `SoilHydrology`. Optional `SoilHydrologyVariation` adds deterministic coordinate-local capacity variation.

The resolved value is a pure function of:

- material definition;
- configured seed;
- X/Y/Z coordinates.

No runtime RNG state is consumed. The same seed and coordinates always produce the same capacity. Starting `SoilMoisture` is not randomized.

This makes equal rainfall produce naturally uneven saturation and puddle onset while keeping the simulation deterministic and replayable.

## Actual flow read-side

`WaterFlowSystem` publishes a sparse `WaterFlowLookup` containing the dominant actual transfer through cells that participated in the latest solver step.

It is an objective diagnostic of real transfer, not a visual velocity field. A solver step that performs no transfer clears the snapshot, so a dormant/equalized lake reports no active flow.

Presentation can therefore distinguish calm Water from horizontal flow and vertical falling without inferring motion merely from a possible hydraulic slope.

## Water presentation contract

The visualizer reads authoritative Water state and may consume the actual-flow read-side described above.

- one shared procedural Water atlas is reused by visible cells;
- opacity derives from finite optical depth and is bounded to a three-cell scan;
- calm Water should remain visibly alive through a slow coherent non-directional animation;
- actual horizontal transfer may drive directional travelling detail;
- actual downward transfer may use a distinct falling/churn treatment;
- no persistent per-cell visual animation object, particle state or second fluid simulation is allowed.

If the world has no wet columns, the Water renderer returns before scanning the visible range.

## Compact acceptance suite

The `Water / Hydrology` group intentionally contains three focused scenes.

### Rain Cycle

The climate acceptance world starts at tick zero rather than from a prewarmed flooded snapshot.

For acceptance math, one tile is treated as a 1 m x 1 m footprint and one full normalized cell as 1 m Water depth, so 1 mm equals 1000 normalized volume units.

The scene uses:

- equal starting `SoilMoisture = 0` on the exposed soil field;
- one absorbent terrain material with deterministic local maximum-capacity variation;
- one 3 mm precipitation event every 120 ticks;
- a 1.2 mm soil-surface storage threshold before horizontal runoff;
- a small elevated impermeable roof for sky-exposure acceptance;
- a separate finite 3 x 3 lake in a physical depression;
- periodic evaporation strong enough to dry transient puddles before the next shower while the deeper lake evaporates gradually.

Local soil capacity varies from roughly 1 mm to 4 mm around the material base while the 3 mm infiltration limit is common. Therefore identical rain naturally creates a mixture of cells that absorb the shower and cells that saturate early and form free Water.

The lake is intentionally independent from puddle formation so precipitation/evaporation of already-existing Water can be inspected separately.

### Water Z Flow

Rain-free deterministic setup using exact initial Water. It combines:

- a real multi-Z deep pool;
- a vertical falling shaft;
- one-edge-per-update propagation;
- Z cutaway continuity;
- eventual dormancy when transfer ceases.

### Water Geometry Stress

Rain-free bounded horizontal stress combining:

- symmetric multi-exit flow;
- a long `FullShape` barrier and detour/equalization path;
- Ramp low-face admission;
- Ramp high-face blocking;
- finite spreading inside a closed map.

Lower-level tests remain authority for exact conservation, deterministic mutation order, integer convergence, dormancy/wake, surface-storage runoff, vertical fall, run-on infiltration, local capacity determinism, capacity displacement and mover-specific Water traversal.

## Rain presentation

Rain presentation remains fixed-budget and screen-space. Weather intensity controls visual density/strength, but rain does not create world objects or a particle population proportional to map size.

At normal desktop zoom the acceptance target is immediately readable rain: sufficient density, clear falling direction, useful streak length and contrast without overwhelming terrain inspection.

## Performance rules

Hydrology acceptance bounds work independently at several layers:

- finite world bounds cap the maximum hydraulic frontier;
- solver stress worlds use exact initial Water rather than repeated precipitation;
- Water flow and Water/Soil exchange operate only on sparse active cells;
- surface storage lets tiny stable films become dormant instead of activating an ever-growing plane;
- evaporation is periodic rather than evaluated every tick;
- Water presentation is camera-visible only and optical-depth work is capped;
- actual-flow presentation reads a sparse latest-step diagnostic;
- no per-cell persistent Water animation state exists;
- Water diagnostics recompute only when simulation tick changes;
- rain remains a fixed-budget screen-space effect.

Groundwater, Darcy flow, erosion/sediment, river world generation, full meteorology and sophisticated wave physics remain intentionally outside this milestone.

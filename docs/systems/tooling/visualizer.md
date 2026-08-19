# Visualizer and Developer Inspection Tools

## In plain language

The Visualizer is EvoForge's **window into the simulation**. It lets a developer watch Terrain, Water, agents, routes and diagnostics, but it must never become another simulation engine.

If the screen draws a Cow halfway between two cells, the authoritative Cow position is still whatever Spatial reports. If a debug overlay colors a path, Navigation/Pathfinding still own the route facts. Hiding Water or zooming far away never changes hydraulic rules.

The application also contains pre-runtime world-generation inspection tools. They consume the same production generators used by the engine and display immutable generated facts before a runtime exists.

## Current status

Two broad tool families exist:

```text
runtime scenario visualizer
  observes/commands a started SimulationRuntime

world-generation preview
  generates and inspects immutable V12 elevation/shape facts
  without starting a second runtime
```

Both are developer tools. Headless tests remain the primary correctness proof; manual visual acceptance complements tests for aesthetics/readability.

## Runtime observer boundary

`ZLevelVisualizer` is built from read-oriented/runtime capabilities such as:

```text
SimulationView
SimulationTime
SimulationStepper
presentation bindings
```

When interaction needs to request a real action, presentation uses an explicit command adapter/sink. It does not receive mutable `MovementSystem`, `LiquidSystem` or Landscape internals just because a UI button exists.

```text
user click
   ↓
presentation command sink
   ↓
production Control/domain command
   ↓
authoritative system
   ↓
SimulationView
   ↓
visualizer redraws result
```

## Scenario model

The developer application contains focused deterministic scenarios for Geometry/Navigation, Movement, Occupancy, Pathfinding, Water/Hydrology and Agents.

Each scenario creates a fresh `ScenarioSession` containing its own ordinary runtime, initial view and optional diagnostics/presentation bindings.

Reset recreates the scenario rather than adding debug-only global reset APIs to simulation domains.

Scenarios serve three purposes:

- make a mechanic understandable to a human;
- expose state transitions that are awkward to infer from test output;
- provide manual visual acceptance evidence.

They do not replace headless invariant tests.

## Presentation perspectives

The runtime visualizer has explicit presentation-only perspectives:

### Surface

Shows the highest relevant Terrain/Water/object surface by visible XY column. Lower covered content is not drawn through upper Terrain.

### Interior

A presentation-local interior/cutaway context entered through portal metadata. Portal presentation does not teleport actors or create Navigation edges.

### Debug Slice

Explicit developer Z/cutaway inspection for internal layers.

Changing these views never changes simulation coordinates/topology.

## Cell-centric interaction

World interaction is cell-first. Selecting a cell updates its inspector; object/portal actions are composed from the authoritative objects/facts at that cell.

Movement targeting uses a disposable PathSearch only for preview/advice. Actual execution is submitted through the production MoveTo command path, and Movement/Occupancy/Water constraints remain authoritative.

Cancelling a move uses the production cancellation command rather than deleting Movement state from presentation.

## Water presentation

Water visuals read authoritative quantity + Geometry. The renderer may derive bounded optical depth, presentation opacity and animation from current Water/flow diagnostics.

`WaterMotionResolver` reads the latest real transfer sample; it does not invent motion merely because a theoretical slope exists.

Presentation may suppress tiny visually meaningless flux or use deterministic animation phase. Such choices cannot suppress hydraulic simulation work.

Rain presentation likewise uses a fixed rendering budget; visible raindrops are not authoritative world entities.

## V12 world-generation preview

The current `WorldGenerationPreviewScreen` explicitly uses:

```text
GenerationRevision.V12
RngRevision.V1
WorldGenerationPreviewSettings
        ↓
WorldGenesis + WorldSpec
        ↓
ElevationGenerationStage
        ↓
ElevationField
        ↓
TerrainShapeGenerationStage.forRevision(V12)
        ↓
TerrainShapeField
        ↓
2D / 3D presentation
```

This is crucial: the preview is not an approximate reimplementation of V12. It calls production generation code and only changes how the resulting facts are displayed.

The displayed sea plane in 3D is presentation-only at the V9+ sea-level datum `z=0`.

## World-generation settings panel

The **WORLD** tab currently exposes:

### World dimensions/provenance

```text
Width
Length
Seed
NEXT seed
Random seed on Generate
```

When **Random seed on Generate** is enabled, each Generate selects a new random 64-bit seed for the developer session and writes that exact value back into the ordinary visible/copyable seed field **before generation**. The generated world still has an exact reproducible seed.

Unchecking random mode lets the displayed value be reused to reproduce that world.

### V12 semantic land-shape controls

```text
Land             -> landCoverage
Continent scale  -> landmassScale
Fragmentation    -> fragmentation
Macro height     -> relief
Rolling hills    -> localRelief
Landform size    -> landformScale
Ruggedness       -> ruggedness
```

These are normalized semantic controls documented in [World Genesis](../world-generation/world-genesis.md). The panel does not expose V12 recipe internals such as coast transition distance or ridge weight.

### Preview-only controls

```text
2D / 3D view
Z contrast
Terrain surface visibility
Ocean water visibility
```

These affect presentation only and never change generated facts except when an actual generation setting is edited and Generate is pressed.

## 2D preview and LOD

2D mode displays generated `ElevationField` + `TerrainShapeField` with presentation-only LOD so large worlds remain inspectable.

Important law:

```text
LOD changes drawing sample density
NOT generated Terrain
```

Current LOD budgets are live developer tuning. The Performance tab exposes:

```text
Detailed range
Far detail
```

and reports current visible columns, rendered samples and LOD stride.

The current fast defaults shown by the panel are approximately:

```text
9,000 exact/detailed cell budget
6,000 far/overview sample budget
```

The earlier expensive “nearly x2 but still x1” presentation hot zone was specifically tightened so 2D preview steps to coarser LOD before exact per-cell rendering becomes unnecessarily expensive. This is a rendering optimization only.

2D controls include:

```text
WASD or drag  pan
wheel         zoom
F             fit world
F3            shape-direction overlay
Esc           return to development tools
```

## 3D preview

3D mode builds a surface mesh from sampled exact generated elevation. It uses chunked meshes and a configurable maximum sample axis.

Performance panel:

```text
3D mesh axis
```

Current guidance/defaults in the UI:

```text
160  fast default
256–384 higher-detail inspection
512  high-quality inspection limit
```

Changing this value rebuilds only preview mesh chunks; it does not regenerate V12.

3D controls:

```text
left drag   orbit
wheel       zoom
Esc         return to development tools
```

A small vertical exaggeration is presentation-only to improve relief readability.

## Performance telemetry

The worldgen overlay reports generation time and frame/presentation statistics such as FPS, frame/CPU time and current LOD/mesh sample counts.

Runtime visualizer telemetry similarly separates observed frame interval from CPU work in major presentation stages.

Performance optimization follows measured rendering work. Presentation budgets/caches are allowed to become cheaper with zoom/distance because they do **not** change authoritative simulation fidelity.

## Manual visual acceptance versus tests

Automated tests should protect deterministic facts:

- same V12 seed/intent -> same ElevationField;
- land coverage/range/slope/scale laws;
- shape fitting invariants;
- LOD sample-budget logic;
- settings/random-seed state semantics.

Humans must still inspect questions such as:

- Do landforms look coherent/organic?
- Are hills/depressions readable?
- Does 2D/3D representation communicate the same morphology?
- Is performance/LOD acceptable in actual use?

The accepted Stage 0 V12 baseline passed both headless checks and manual 2D/3D inspection before the architecture refactor was merged.

## Invariants

- Visualizer never owns authoritative simulation state.
- Camera/view/LOD/mesh quality never changes simulation/generation semantics.
- Runtime interactions go through production command/domain paths.
- Worldgen preview calls production V12 generation contracts.
- A random preview seed is always captured/displayed as an exact reproducible value.
- Debug/presentation overlays never become hidden Navigation/Water/AI truth.
- Aesthetics are manually accepted; deterministic semantics remain headless-testable.

## Current limitations

The tools are developer-facing, not a finished player UI/world-creation flow. Worldgen preview currently focuses on V12 base elevation/surface Shape; Stage 1+ should add inspectable generated facts only when the real stage introduces them.

Advanced roof/occlusion, rich build tools, full lighting, large art sets and broad presentation caching remain future work.

## Code and tests

Primary runtime presentation lives under:

```text
core/.../visualizer/
```

Worldgen preview code lives primarily under:

```text
core/.../visualizer/screen/WorldGeneration* 
```

Key current classes include `WorldGenerationPreviewScreen`, `WorldGenerationSettingsPanel`, `WorldGeneration2DLod`, `WorldGeneration3DDetail` and `WorldGenerationShape2DRenderer`.

## Sources

**Internal EvoForge tooling/presentation design.** The observer boundary, preview/LOD behavior and visual acceptance workflow are project infrastructure.

See [Debug Scenarios Guide](../../guides/debug-scenarios.md), [World Generation](../world-generation/overview.md), [Terrain Generation](../world-generation/terrain-generation.md), [Generated World Diagnostics](generated-world-diagnostics.md), and [ADR-004](../../decisions/004-typed-presentation-bindings.md).

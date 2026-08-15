# Runtime Composition

## Purpose

Build the production simulation once, then expose only the capabilities appropriate to runtime consumers.

## Owns

Bootstrap/composition only. `SimulationAssembly` may wire mutable owners during setup; it is not itself an authoritative domain owner.

## Public runtime boundary

`SimulationAssembly.start()` yields a `SimulationRuntime` exposing:

```text
submit   external Command submission
SimulationTime
SimulationStepper
SimulationView
```

`SimulationView` groups read-only world capabilities used by presentation and other observers. It exposes lookups/views, not mutable systems.

Current production composition includes the established Landscape/Geometry/Navigation/Traversal/Occupancy/Movement/Pathfinding stack, finite Water/Soil hydrology, Water-aware traversal, Need/Stock/Growth and autonomous-agent read projections. Adding a new read capability is explicit rather than exposing a general mutable service locator.

## Optional finite world bounds

Before `start()`, a scenario/runtime may configure one inclusive finite box:

```java
assembly.worldBounds(minX, maxX, minY, maxY, minZ, maxZ);
```

The assembly configures the shared `WorldGeometryLookup`. Inside the box it delegates to ordinary landscape Geometry. Outside the box it resolves `FullShape`, which gives Geometry consumers one physically closed boundary without teaching Water, Navigation, Movement or Pathfinding special coordinate-edge rules.

Setup mutations that place Terrain/objects or initial Water also reject coordinates outside configured bounds.

Bounds are optional. An assembly that does not configure them keeps the earlier unbounded semantics. This is a runtime containment capability, not yet a chunk-generation/streaming model.

## Hydrology composition

The assembly wires hydrology so ownership remains explicit:

```text
SoilMoistureSystem
WaterSystem
SurfaceWaterStorageLookup
WaterSoilExchangeSystem
WaterFlowSystem / WaterFlowProcess
shared SkySurfaceLookup
optional precipitation / evaporation schedules
```

Run-on Water-to-Soil exchange occurs before the next local flow solve. External precipitation/evaporation mutate through the authoritative Soil/Water boundaries and wake hydraulic work only when free Water actually changes.

## Traversal composition

One production `WaterWadingConstraint` is shared by advisory MoveTo query composition and authoritative Movement start/commit revalidation. The assembly performs this wiring; Water does not become Navigation state and MoveTo does not import Water directly.

## Invariants

- setup mutation does not leak through `SimulationRuntime`;
- presentation receives `SimulationView`, `SimulationTime` and `SimulationStepper`, not a service-locator `World`;
- adding a read capability is explicit and reviewable;
- cross-system adapters are composed at the bootstrap boundary rather than by reversing domain dependencies;
- production composition and test fixtures may differ in construction ergonomics without changing domain semantics;
- optional runtime bounds are represented through shared Geometry, not duplicated by every consumer.

## Diagnostics and tests

`VisualizerBoundaryTest` reflects over the visualizer constructor so mutable simulation dependencies cannot silently enter presentation. Integration tests exercise production composition for movement/pathfinding, hydrology, world bounds and autonomous-agent slices.

## Deferred

Queued/asynchronous external command delivery, networking, persistence composition and generated/streamed world loading remain future consumers.

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

Current production composition includes the established Landscape/Geometry/Navigation/Traversal/Occupancy/Movement/Pathfinding stack, generic liquid/retained-Soil foundations with Water-specific environment/traversal projections, Need/Stock/Growth and autonomous-agent read projections. Adding a new read capability is explicit rather than exposing a general mutable service locator.

## Optional finite world bounds

Before `start()`, a scenario/runtime may configure one inclusive finite box:

```java
assembly.worldBounds(minX, maxX, minY, maxY, minZ, maxZ);
```

The assembly configures the shared `WorldGeometryLookup`. Inside the box it delegates to ordinary landscape Geometry. Outside the box it resolves `FullShape`, which gives Geometry consumers one physically closed boundary without teaching liquids, Navigation, Movement or Pathfinding special coordinate-edge rules.

Setup mutations that place Terrain/objects or initial liquid also reject coordinates outside configured bounds.

Bounds are optional. An assembly that does not configure them keeps unbounded semantics. This is a runtime containment capability, not yet a chunk-generation/streaming model.

## Liquid and hydrology composition

The assembly wires one shared liquid authority:

```text
LiquidSystem
    |
    +--> LiquidFlowSystem
    |        |
    |        `--> LiquidFlowProcess
    |
    +--> WaterSystem                 typed Water facade
    |
    `--> SoilLiquidInfiltrationSystem
                 |
                 v
          SoilLiquidSystem
```

Definition/configuration inputs are independent:

```text
LiquidTransportDefinitions      per-liquid kinematic viscosity
SoilPropertiesDefinitions       material pore capacity + permeability
SoilPropertiesVariationDefinitions
SurfaceRetentionDefinitions     material free-liquid microtopography
```

`LiquidFlowProcess` runs generic Soil infiltration immediately before each active hydraulic solve. There is no parallel Water flow process and no Water-only Soil exchange compatibility layer.

Water-specific environment composition then attaches to the shared owners:

```text
WaterSystem
    +--> VerticalSkySurfaceSystem
    +--> optional precipitation schedule
    +--> optional evaporation schedule
    +--> Water wading
    `--> Water-filtered presentation/diagnostics
```

`SimulationView` exposes `SoilLiquidLookup`, `SoilPropertiesLookup`, generic surface retention, Water-filtered quantity/surface/flow lookups and the other established read capabilities. It does not expose a second Water-moisture authority.

External precipitation/evaporation mutate through Water/retained-Soil owners and wake shared hydraulic work only when free-liquid state actually needs redistribution.

## Traversal composition

One production `WaterWadingConstraint` is shared by advisory MoveTo query composition and authoritative Movement start/commit revalidation. The assembly performs this wiring; Water does not become Navigation state and MoveTo does not import Water directly.

## Invariants

- setup mutation does not leak through `SimulationRuntime`;
- presentation receives `SimulationView`, `SimulationTime` and `SimulationStepper`, not a service-locator `World`;
- adding a read capability is explicit and reviewable;
- one free-liquid world has one `LiquidSystem` and one `LiquidFlowSystem`;
- typed Water capabilities filter/adapt shared state rather than duplicating authority;
- cross-system adapters are composed at the bootstrap boundary rather than by reversing domain dependencies;
- production composition and test fixtures may differ in construction ergonomics without changing domain semantics;
- optional runtime bounds are represented through shared Geometry, not duplicated by every consumer.

## Diagnostics and tests

`VisualizerBoundaryTest` reflects over the visualizer constructor so mutable simulation dependencies cannot silently enter presentation. Integration tests exercise production composition for movement/pathfinding, generic liquid/Water hydrology, world bounds and autonomous-agent slices.

## Deferred

Queued/asynchronous external command delivery, networking, persistence composition and generated/streamed world loading remain future consumers.

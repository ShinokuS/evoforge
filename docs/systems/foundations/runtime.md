# Runtime Composition

## In plain language

`SimulationAssembly` is where EvoForge **builds the machine**. `SimulationRuntime` is the machine after the start button has been pressed.

During setup, the assembly is allowed to connect mutable systems, definitions, adapters and scheduled processes. After `start()`, normal consumers receive only the capabilities appropriate to a running world: submit external intent, read simulation time, advance the simulation through the production stepper, and observe a read-only `SimulationView`.

The assembly is wiring. It is not the owner of Terrain, Water, Movement or any other domain state.

## Current status

Production startup composes the established runtime domains including:

- objects/definitions/spatial state;
- Landscape + Geometry;
- Navigation + transition cost;
- Occupancy + Movement + MoveTo + Pathfinding;
- generic free liquids + retained Soil constituents;
- Water-specific atmosphere/traversal/read adapters;
- Needs, finite stock, Growth and autonomous agents;
- optional finite world bounds;
- optional generated-world prepared data through the generated bootstrap path.

## Public runtime boundary

`SimulationAssembly.start()` yields `SimulationRuntime`, exposing the stable high-level capabilities:

```text
command submission
SimulationTime
SimulationStepper
SimulationView
```

`SimulationView` groups read-only capabilities. It is deliberately not a mutable service locator.

A presentation system can ask “what objects are in this cell?” or “how much Water is here?” but cannot reach into `LiquidSystem`, `SpatialSystem` or `MovementSystem` and mutate them directly.

## Setup versus running world

```text
SimulationAssembly
  register definitions
  configure bounds/scales
  wire authoritative owners
  register handlers/processes/adapters
  place initial state
        ↓
      start()
        ↓
SimulationRuntime
  immutable configuration
  authoritative domain systems running
  read-only public view for observers
```

Definition stores used by production mechanics are frozen before scheduled runtime work begins.

## Optional finite world bounds

Before start, a runtime may configure one inclusive box:

```java
assembly.worldBounds(minX, maxX, minY, maxY, minZ, maxZ)
```

Inside the box, normal Landscape Geometry applies. Outside it, shared `WorldGeometryLookup` resolves `FullShape`, making the boundary physically closed for Geometry consumers.

This lets Navigation, Movement and liquids observe one boundary law through their existing Geometry dependency rather than implementing separate coordinate-edge checks.

Setup placement/initial liquid also rejects coordinates outside configured bounds.

If no bounds are configured, unbounded coordinate semantics remain available. This is containment, not chunk streaming.

## Liquid/Soil composition

The current shared environmental ownership is:

```text
LiquidSystem                 authoritative free-liquid cells
   │
   ├─ LiquidFlowSystem        generic redistribution
   │    └─ LiquidFlowProcess  scheduled continuation while active
   │
   ├─ WaterSystem             typed Water facade
   │
   └─ SoilLiquidInfiltrationSystem
            ↓
      SoilLiquidSystem        retained constituents in porous Terrain
```

Independent immutable configuration includes:

```text
LiquidTransportDefinitions       liquid viscosity
SoilPropertiesDefinitions        pore capacity + permeability
SoilPropertiesVariationDefinitions
SurfaceRetentionDefinitions      material free-liquid surface reserve
```

Generic liquid flow performs Soil infiltration before each active hydraulic solve. There is not a second Water-only flow engine.

Water-specific environment systems then compose over shared owners:

```text
Water facade
  ├─ VerticalSkySurfaceSystem
  ├─ precipitation / atmospheric forcing
  ├─ evaporation
  ├─ Water-wading traversal constraint
  └─ Water-filtered diagnostics/presentation
```

## Traversal composition

One production `WaterWadingConstraint` is shared between:

- advisory MoveTo/path query filtering;
- authoritative Movement start/commit revalidation.

This is composed at bootstrap. Water does not become Navigation topology, and MoveTo does not hard-code Water storage.

## Cross-system adapter law

When two domains need to interact, composition owns the adapter whenever possible.

Example:

```text
Water quantity + mover wading profile
        ↓ composed WaterWadingConstraint
Pathfinding/MoveTo advice + Movement revalidation
```

This avoids reversing dependencies or teaching generic systems specific content/domain implementations.

## Generated-world relationship

Generated worlds use the same production runtime. `GeneratedWorldRuntimeBootstrap` prepares an ordinary `SimulationAssembly`, materializes generated facts once, composes atmosphere and then calls `start()`.

After start, generated and hand-authored scenarios obey the same domain systems/scheduler laws.

See [Generated World Runtime](../world-generation/generated-world-runtime.md).

## Invariants

- `SimulationAssembly` is composition/setup, not a mutable domain owner.
- `SimulationRuntime` does not expose setup mutation.
- `SimulationView` exposes reads, not mutable service internals.
- One free-liquid world has one `LiquidSystem` and one `LiquidFlowSystem`.
- Water-specific views/adapters filter shared liquid truth rather than duplicate it.
- Cross-domain adapters are wired at composition instead of reversing package dependencies.
- Optional bounds are shared through Geometry.
- Production stepping semantics are centralized in `SimulationStepper`.
- Tests may construct smaller fixtures, but domain laws remain identical.

## Current limitations

Runtime composition does not yet define:

- queued/asynchronous external command delivery;
- networking;
- persistence/save loading;
- chunk streaming/partial loaded-world composition;
- authoritative multithreaded mutation.

Those require explicit visibility/ownership semantics before implementation.

## Code and tests

Primary implementation:

```text
simulation/.../runtime/SimulationAssembly.java
simulation/.../runtime/SimulationRuntime.java
simulation/.../runtime/SimulationView.java
```

Integration tests exercise production composition for traversal, Water/hydrology, finite bounds, agents and generated-world startup. `VisualizerBoundaryTest` guards against mutable simulation dependencies leaking into presentation construction.

## Sources

**Internal EvoForge design.** Runtime composition/capability exposure is project-specific architecture.

See [Architecture](../../architecture.md), [Control](control.md), [Time](time.md), [Hydrology](../environment/hydrology.md), and [Generated World Runtime](../world-generation/generated-world-runtime.md).

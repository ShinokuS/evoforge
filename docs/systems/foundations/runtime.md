# Runtime Composition

## In plain language

`SimulationAssembly` is where EvoForge **builds the machine**. `SimulationRuntime` is the machine after the start button has been pressed.

During setup, the assembly connects mutable systems, definitions, adapters and scheduled processes. After `start()`, normal consumers receive only the capabilities appropriate to a running world: commands, simulation time, production stepping and a read-only view.

The assembly is wiring. It is not the owner of Terrain, Water, Movement or any other domain state.

## Current status

Production startup composes the established runtime domains including:

- objects/definitions/spatial state;
- Landscape + Geometry;
- Navigation + traversal cost;
- Occupancy + Movement + MoveTo + Pathfinding;
- generic free liquids + retained Soil constituents;
- Water-specific atmosphere/traversal/read adapters;
- Needs, finite stock, Growth and autonomous agents;
- optional finite world bounds.

The old V12–V15 generated-world bootstrap path has been retired. Continuum-to-runtime materialization is future work and must use ordinary authoritative runtime owners rather than recreating a parallel runtime.

## Public runtime boundary

`SimulationAssembly.start()` yields `SimulationRuntime`, exposing stable high-level capabilities:

```text
command submission
SimulationTime
SimulationStepper
SimulationView
```

`SimulationView` groups read-only capabilities. It is deliberately not a mutable service locator.

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

Before start, a runtime may configure one inclusive box through `SimulationAssembly.worldBounds(...)`.

Inside the box, ordinary Landscape Geometry applies. Outside it, the shared geometry lookup resolves a closed solid boundary. This lets Navigation, Movement and liquids observe one containment law without separate edge checks.

If no bounds are configured, unbounded coordinate semantics remain available. This is containment, not world streaming.

## Liquid/Soil composition

The current shared environmental ownership is:

```text
LiquidSystem                 authoritative free-liquid cells
   │
   ├─ LiquidFlowSystem        generic redistribution
   │
   ├─ WaterSystem             typed Water facade
   │
   └─ SoilLiquidInfiltrationSystem
            ↓
      SoilLiquidSystem        retained constituents in porous Terrain
```

Water-specific environment systems compose over these shared owners rather than creating a second Water-only flow engine.

## Cross-system adapter law

When two domains need to interact, composition owns the adapter whenever possible. Generic systems should not depend backwards on a concrete content/domain implementation merely to obtain a cross-domain rule.

## Continuum relationship

Continuum currently provides deterministic addressable world-generation foundations only. It does **not** yet bootstrap a generated world into `SimulationRuntime`.

Future integration must follow this boundary:

```text
Continuum generated facts
        ↓
explicit materialization / transfer step
        ↓
ordinary runtime authoritative owners
        ↓
SimulationRuntime
```

After transfer, runtime systems own changing world state. Presentation and page/cache infrastructure remain observers/representation details.

## Invariants

- `SimulationAssembly` is composition/setup, not a mutable domain owner.
- `SimulationRuntime` does not expose setup mutation.
- `SimulationView` exposes reads, not mutable service internals.
- One free-liquid world has one authoritative liquid ownership path.
- Cross-domain adapters are wired at composition instead of reversing package dependencies.
- Optional bounds are shared through Geometry.
- Production stepping semantics are centralized in `SimulationStepper`.
- Continuum integration must not create a second runtime truth.

## Current limitations

Runtime composition does not yet define:

- queued/asynchronous external command delivery;
- networking;
- persistence/save loading;
- chunk/page streaming and partial loaded-world composition;
- Continuum-generated world bootstrap;
- authoritative multithreaded mutation.

## Code and tests

Primary implementation:

```text
simulation/.../runtime/SimulationAssembly.java
simulation/.../runtime/SimulationRuntime.java
simulation/.../runtime/SimulationView.java
```

Integration tests exercise production composition for traversal, Water/hydrology, finite bounds and agents. Presentation boundary tests guard against mutable simulation dependencies leaking into the visualizer.

## Sources

**Internal EvoForge design.** Runtime composition/capability exposure is project-specific architecture.

See [Architecture](../../architecture.md), [Control](control.md), [Time](time.md), [Hydrology](../environment/hydrology.md), [World Generation](../world-generation/overview.md), and [ADR-024](../../decisions/024-continuum-large-world-architecture.md).

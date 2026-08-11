# Project Structure

EvoForge is a multi-module Gradle project. The module boundary is architectural: authoritative simulation code must remain independent of libGDX so it can run headlessly in tests and later in tools, servers, or deterministic scenario runners.

## Repository root

```text
EvoForge/
├── assets/
├── core/
├── docs/
│   ├── ARCHITECTURE.md
│   ├── TECHNICAL_REFERENCE.md
│   ├── ru/
│   └── wiki/
├── lwjgl3/
├── simulation/
├── .github/workflows/
├── build.gradle
├── settings.gradle
├── gradle.properties
├── gradlew
└── gradlew.bat
```

## `simulation`

This is the authoritative domain module and the main architecture target.

Current package structure includes:

```text
io.github.evoforge.simulation
├── result/
├── control/
│   ├── core/
│   ├── sync/
│   ├── terrain/
│   └── movement/
├── definition/
├── time/
└── world/
    ├── World
    ├── object/
    │   └── definition/
    ├── spatial/
    │   └── indexes/
    ├── landscape/
    │   ├── LandscapeMutations
    │   ├── LandscapeSystem
    │   ├── definition/
    │   └── terrain/
    │       └── storage/
    ├── mechanics/
    │   ├── physical/
    │   ├── geometry/
    │   ├── movement/
    │   └── traversal/
    └── navigation/
```

The package tree grows only when a real subsystem exists. Empty packages are not created as roadmap placeholders.

## `core`

`core` is the shared libGDX application/presentation layer. It may read simulation state through public contracts and submit external intent through the Control boundary, but it must not become the owner of simulation state.

A useful rule is: closing the game window must not be conceptually equivalent to destroying the authoritative World model. Presentation is a client of the simulation.

The next visual/debug milestone belongs in presentation-facing code and should read Movement/Spatial/Terrain/Geometry state rather than reimplement authoritative simulation logic.

## `lwjgl3`

`lwjgl3` contains the desktop launcher and platform startup helpers. Platform integration belongs here rather than in the simulation module.

## `assets`

The assets tree contains both presentation assets and source data definitions.

Current definition roots include:

```text
assets/definitions/object/
assets/definitions/landscape/
```

Definitions use stable keys rather than relying on filenames or runtime integer ids as persistence identity.

Current landscape content may include a `traversal` aspect such as:

```json
"traversal": {
  "cost": 1000
}
```

while object content may include a `movement` aspect with `rate`.

## `docs`

Documentation has three roles.

`ARCHITECTURE.md` is intentionally compact and normative. It describes stable semantic boundaries, invariants, and deferred decisions.

`TECHNICAL_REFERENCE.md` tracks the current implementation and may change frequently as code evolves.

`docs/wiki/` contains the long-form Wiki source. `docs/ru/` contains maintained Russian counterparts. GitHub Wiki and VitePress are generated from repository sources after documentation changes reach `main`.

The long-form [Movement System](Movement-System.md) page is the primary explanatory reference for current timed movement and TransitionCost behavior.

## `result`

`simulation/result` is neutral infrastructure shared by domain operations and Control.

Current types:

```text
OperationResult
ResultCode
OperationResults
```

It defines only the minimal accepted/rejected observation floor and namespaced result code. It does not own domain semantics.

## `control`

The Control surface is discoverable under one root:

```text
control/
├── core/
├── sync/
├── terrain/
└── movement/
```

`core` contains generic Command/Handler/Dispatcher contracts and does not import world-domain types. `sync` contains the current immediate delivery implementation.

Concrete commands are grouped by intent/use-case. Current vertical slices are:

```text
terrain/
    PlaceTerrainCommand
    PlaceTerrainResult
    PlaceTerrainHandler

movement/
    MoveStepCommand
    MoveStepResult
    MoveStepHandler
```

World packages do not depend on Control. Internal mechanics may call narrow domain APIs directly rather than manufacturing Commands as internal RPC.

## `definition`

The generic definition package provides reusable infrastructure for loading composition-driven definitions. It includes stable `DefinitionId` values, compiler registration, file reading, loading, and runtime registries/catalogs.

Domain areas such as objects and landscape wrap generic definition ids in typed ids so systems do not accidentally mix definition domains.

Mechanic-specific compiled stores remain with their mechanics. For example:

```text
world/mechanics/movement/MovementDefinitions
world/mechanics/traversal/LandscapeTraversalDefinitions
```

The generic definition package therefore does not become a giant central schema.

## `time`

The time package is now production infrastructure used by Movement.

Current important types include:

```text
SimulationTime
SimulationClock
SimulationStepper
Scheduler
ScheduledTask
ScheduledHandler
HandlerId
HandlerRegistry
TaskHandle
ProcessScheduler
BoundProcessScheduler
```

`SimulationStepper` owns the current production one-tick phase order. `ProcessScheduler` is the narrow domain-facing ability to schedule a process after a delay; `BoundProcessScheduler` binds that ability to one registered handler.

Scheduler knows activation time/routing, not the domain meaning of a Movement/Crafting/Growth process.

## `world/object`

This package owns runtime object identity and existence. `ObjectRepository` is slot/generation based and implements the read-only `ObjectLookup` contract. `ObjectFactory` creates definition-backed objects.

`WorldObject` does not accumulate movement speed, position, action state or terrain-specific fields. Those belong to specialized mechanics/owners.

## `world/spatial`

This package owns positions for `WorldObject` instances only. `TransformState` stores ObjectId-to-XYZ state; spatial indexes provide reverse/derived position queries; `SpatialSystem` coordinates authoritative mutations and index updates.

Timed Movement does not own a second position. While a `MovementAction` is active, Spatial remains at the source; completion may later call `SpatialSystem.move` after revalidation.

## `world/landscape`

Landscape is not represented as millions of `WorldObject` instances. Terrain content is stored separately as:

```text
XYZ -> LandscapeDefinitionId | absence
```

`TerrainSystem` owns terrain storage and terrain-specific invariants. `LandscapeMutations`, implemented by `LandscapeSystem`, is the coordinated write capability used when terrain lifetime must remain coherent with Geometry.

The current storage is sparse and replaceable.

Landscape definitions can additionally carry mechanic-owned compiled aspects such as actor-independent `traversal.cost`; the terrain cell itself still stores only its `LandscapeDefinitionId`.

## `world/mechanics/geometry`

Geometry is layered over present terrain. It determines local structural topology but does not own terrain material identity.

Current core types include:

```text
Shape
FullShape
RampShape
GeometrySystem
GeometryState
GeometryLookup
TransitionMask
TransitionPorts
TransitionComposition
SolidCellBlocking
GridTransitionLength
ShapeTraversalFactor
```

`Shape` owns local topology roles and may also expose a local intrinsic departure/arrival traversal factor. The same role law applies to both. Geometry does not inspect actor identity or MovementRate.

`GridTransitionLength` represents the intrinsic length of immediate cardinal/double-diagonal/triple-diagonal grid directions in fixed-point units.

## `world/mechanics/traversal`

Traversal is the actor-independent price layer for a directed structural edge.

Current types:

```text
SurfaceTraversalCost
LandscapeTraversalDefinitions
LandscapeTraversalDefinitionCompiler
TransitionCost
TransitionCostLookup
TransitionCostCalculator
```

`TransitionCostCalculator` combines:

```text
source landscape surface cost
source Shape departure factor
destination landscape surface cost
destination Shape arrival factor
grid direction length
```

It does not decide whether an edge exists and does not know a concrete mover. Movement calls it only after Navigation has accepted the directed edge. Future Pathfinder should consume the same `TransitionCostLookup` rather than maintain independent prices.

## `world/mechanics/movement`

Movement owns execution of one timed adjacent object transition.

Current types:

```text
MovementRate
MovementDefinitions
MovementDefinitionCompiler
MovementStartResult
MovementActionId
MovementAction
MovementStateStore
MovementSystem
MovementActionProcessor
```

Responsibility split:

```text
MovementSystem
    -> validate/start one adjacent action
    -> obtain TransitionCost
    -> convert cost to ticks using MovementRate + carry
    -> schedule completion

MovementStateStore
    -> active MovementAction state
    -> per-object timing carry
    -> one-active-action-per-object invariant

MovementActionProcessor
    -> resume scheduled process
    -> completion-time revalidation
    -> call SpatialSystem.move or interrupt
```

Movement does not perform pathfinding and does not own Occupancy yet.

## `world/navigation`

Navigation consumes only `GeometryLookup` and exposes structural adjacency through `NavigationLookup.transitions(x,y,z)`.

It does not know ObjectId, actor abilities, traversal cost, concrete Shape types, or pathfinding algorithms.

This is intentionally separate from `TransitionCostLookup`: one answers **which structural edges exist**, the other prices an already-valid edge.

## Tests

Simulation tests mirror domain areas under `simulation/src/test/java`. Unit tests validate local contracts; integration tests cross subsystem boundaries; property/reference tests validate generic laws against independent expectations.

Current Movement/Traversal coverage includes:

```text
movement definition compilation
exact delayed completion timing
different MovementRate values
diagonal grid length
fractional timing carry
one active action per object
completion-time revalidation
landscape traversal definition compilation
two-cell TransitionCost formula
directed Shape departure/arrival factors
scenario-level proof that cost changes authoritative duration
batch tick advancement equivalence
```

Control and Landscape also include executable dependency/boundary contract tests so key package-direction and mutation laws are more than documentation.

Run the full simulation suite with:

```bash
./gradlew :simulation:test --rerun-tasks --console=plain
```

Windows:

```bat
.\gradlew.bat :simulation:test --rerun-tasks --console=plain
```

Routine `clean` is intentionally avoided because incremental builds are normally sufficient and cheaper.

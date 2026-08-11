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

Current package structure:

```text
io.github.evoforge.simulation
├── result/
├── control/
│   ├── core/
│   ├── sync/
│   └── terrain/
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
    │   └── geometry/
    └── navigation/
```

The package tree grows only when a real subsystem exists. Empty packages are not created as roadmap placeholders.

## `core`

`core` is the shared libGDX application/presentation layer. It may read simulation state through public contracts and submit external intent through the Control boundary, but it must not become the owner of simulation state.

A useful rule is: closing the game window must not be conceptually equivalent to destroying the authoritative World model. Presentation is a client of the simulation.

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

## `docs`

Documentation has three roles.

`ARCHITECTURE.md` is intentionally compact and normative. It describes stable semantic boundaries, invariants, and deferred decisions.

`TECHNICAL_REFERENCE.md` tracks the current implementation and may change frequently as code evolves.

`docs/wiki/` contains the long-form Wiki source. `docs/ru/` contains maintained Russian counterparts. GitHub Wiki and VitePress are generated from repository sources after documentation changes reach `main`.

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
└── terrain/
```

`core` contains generic Command/Handler/Dispatcher contracts and does not import world-domain types. `sync` contains the current immediate delivery implementation. Concrete commands are grouped by intent/use-case; the first example is the terrain placement slice.

World packages do not depend on Control. Internal mechanics may call narrow domain APIs directly rather than manufacturing Commands as internal RPC.

## `definition`

The generic definition package provides reusable infrastructure for loading composition-driven definitions. It includes stable `DefinitionId` values, compiler registration, file reading, loading, and runtime registries/catalogs.

Domain areas such as objects and landscape wrap generic definition ids in typed ids so systems do not accidentally mix definition domains.

## `time`

The time package contains the simulation clock and scheduler foundation. Handler registration is separate from domain semantics, allowing scheduled work to remain generic infrastructure.

## `world/object`

This package owns runtime object identity and existence. `ObjectRepository` is slot/generation based and implements the read-only `ObjectLookup` contract. `ObjectFactory` creates definition-backed objects.

## `world/spatial`

This package owns positions for `WorldObject` instances only. `TransformState` stores ObjectId-to-XYZ state; `CellSpatialIndex` provides reverse cell lookup derived from that state; `SpatialSystem` coordinates authoritative mutations and index updates.

## `world/landscape`

Landscape is not represented as millions of `WorldObject` instances. Terrain content is stored separately as:

```text
XYZ -> LandscapeDefinitionId | absence
```

`TerrainSystem` owns terrain storage and terrain-specific invariants. `LandscapeMutations`, implemented by `LandscapeSystem`, is the coordinated write capability used when terrain lifetime must remain coherent with Geometry.

The current storage is sparse and replaceable.

## `world/mechanics/geometry`

Geometry is layered over present terrain. It determines local structural topology but does not own terrain material identity.

Current core types:

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
```

## `world/navigation`

Navigation consumes only `GeometryLookup` and exposes structural adjacency through `NavigationLookup.transitions(x,y,z)`.

It does not know ObjectId, actor abilities, path cost, concrete Shape types, or pathfinding algorithms.

## Tests

Simulation tests mirror domain areas under `simulation/src/test/java`. Unit tests validate local contracts; integration tests cross subsystem boundaries; property/reference tests validate generic laws against an independent resolver.

Control adds an explicit dependency-contract test so package-direction rules are executable architecture rather than documentation only.

Run the full simulation suite with:

```bash
./gradlew :simulation:test --rerun-tasks --console=plain
```

Windows:

```bat
.\gradlew.bat :simulation:test --rerun-tasks --console=plain
```

Routine `clean` is intentionally avoided because incremental builds are normally sufficient and cheaper.

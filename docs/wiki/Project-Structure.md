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
├── definition/
├── time/
└── world/
    ├── World
    ├── object/
    │   └── definition/
    ├── spatial/
    │   └── indexes/
    ├── landscape/
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

`core` is the shared libGDX application/presentation layer. It may read simulation state through public contracts and submit commands through the future control boundary, but it must not become the owner of simulation state.

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

`docs/wiki/` contains the long-form Wiki source. The GitHub Wiki is generated from these files by GitHub Actions after documentation changes reach `main`.

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

Run the full simulation suite with:

```bash
./gradlew :simulation:test --rerun-tasks --console=plain
```

Windows:

```bat
.\gradlew.bat :simulation:test --rerun-tasks --console=plain
```

Routine `clean` is intentionally avoided because incremental builds are normally sufficient and cheaper.

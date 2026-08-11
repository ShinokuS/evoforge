# EvoForge Wiki

EvoForge is a deterministic emergent-simulation project built on Java 21. The authoritative simulation lives in the pure-Java `simulation` module; libGDX is used only by presentation and launcher layers.

This Wiki is the long-form technical guide to the project. It explains not only what classes exist, but why subsystem boundaries exist, which invariants are stable, which decisions are deliberately deferred, and how new mechanics should be added without destabilizing existing systems.

## Start here

If you are new to the project, read these pages in order:

1. [Project Overview](Project-Overview.md)
2. [Architecture Principles](Architecture-Principles.md)
3. [Project Structure](Project-Structure.md)
4. [World Model](World-Model.md)
5. [Shape Contract](Shape-Contract.md)
6. [Transition Algebra](Transition-Algebra.md)
7. [Navigation](Navigation.md)
8. [Testing Strategy](Testing-Strategy.md)
9. [Development Workflow](Development-Workflow.md)
10. [Roadmap and Deferred Decisions](Roadmap-and-Deferred-Decisions.md)

## Current architecture at a glance

```text
Definitions
    │
    ├── Object definitions
    └── Landscape definitions

WORLD
├── Objects
│   ├── ObjectRepository        identity / existence
│   ├── ObjectFactory           definition-backed creation
│   └── SpatialSystem           ObjectId -> XYZ
│
└── Landscape
    └── TerrainSystem           XYZ -> LandscapeDefinitionId | absence
             │
             ▼
        GeometrySystem          terrain presence -> Shape
             │
             ▼
        NavigationSystem        Shape contributions -> structural edges
```

The central design rule is ownership: every mutable authoritative fact has one owner. Shared coordinates do not imply shared storage, and a convenient query does not justify moving domain responsibility into the query layer.

## Geometry and navigation in one picture

A terrain `Shape` is anchored at a terrain coordinate and contributes local topology. Navigation composes those contributions without knowing concrete shape types.

```text
FullShape        RampShape        FullShape
    █                /                █
    █               /                 █

lower position  <-> ramp position <-> upper position
```

A structural edge exists only when the generic transition algebra resolves it:

```text
resolved = departures & arrivals & ~blocks
```

The edge itself always goes to one of the 26 immediate XYZ neighbors. The resolver may read supporting geometry farther below the source because the Shape that confirms a destination surface can be anchored below that destination.

## Stable documentation layers

The project intentionally keeps three documentation levels:

- `docs/ARCHITECTURE.md` is the concise architectural contract: stable boundaries, invariants, and deferred decisions.
- `docs/TECHNICAL_REFERENCE.md` describes the current implementation, packages, algorithms, tests, and known technical gaps.
- this Wiki is the explanatory layer: examples, diagrams, reasoning, extension guidance, and subsystem walkthroughs.

The Wiki is generated from `docs/wiki/` in the main repository. Do not edit generated Wiki pages as the primary source; update `docs/wiki/` through the normal pull-request workflow instead. See [Wiki Maintenance](Wiki-Maintenance.md).

## Current project phase

The implemented foundation includes definitions, object identity, scheduling, discrete object spatial state, landscape terrain, geometry, structural transition algebra, `FullShape`, cardinal `RampShape`, and directed local navigation. The next architectural consumer is the Control Backbone, followed later by scenario execution, basic movement, occupancy, pathfinding, and the first agent vertical slice.

The project deliberately does not pre-build systems without a consumer. Caches, rich movement costs, actor capability overlays, falling, chunk layouts, and advanced pathfinding remain deferred until real workloads define their requirements.

## Navigation

Use the sidebar for the full documentation map. The [Glossary](Glossary.md) defines project-specific terms such as *authoritative owner*, *terrain anchor*, *standing position*, *departure*, *arrival*, *block*, and *structural edge*.

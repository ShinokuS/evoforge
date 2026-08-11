# Project Overview

## What EvoForge is

EvoForge is a deterministic emergent-simulation project. Its architecture is designed for a world in which many independent mechanics can interact without every new feature requiring changes to a central object type, giant cell structure, or universal update loop.

The project targets a simulation with roughly one million persistent objects, more than one hundred thousand positioned objects, and on the order of ten thousand simultaneously active agents. These are architectural scale targets rather than performance promises. They exist to reject designs that require mandatory global scans or per-tick work proportional to every persistent entity.

## What the project optimizes for

The primary goals are semantic stability, deterministic behavior, extensibility, and measured performance. The project prefers narrow interfaces and explicit owners over central mutable registries. Performance work follows evidence: unnecessary work is removed first, locality and indexes come next, and specialized data-oriented structures are introduced only for proven hot paths.

A subsystem is considered successful when a new mechanic can use its public contract without learning its storage representation or concrete implementation types.

## What EvoForge is not

EvoForge is intentionally not built as:

- a pure ECS where every property is forced into component tables;
- a universal `WorldCell` object containing every environmental mechanic;
- a system where every object receives `update(dt)` every frame;
- a universal physics engine whose abstractions dictate all gameplay;
- a central type switch that knows every object, terrain type, Shape, or Command;
- a command bus through which every internal mutation must be routed;
- a framework that pre-implements speculative infrastructure before a real consumer exists.

Selective data-oriented design is allowed and expected in measured hot paths, but it is an implementation technique rather than the domain model.

## Technology baseline

The current baseline is Java 21 with libGDX 1.14.x presentation modules and a pure-Java `simulation` module. The simulation module has no libGDX dependency and is tested headlessly.

```text
simulation/  authoritative domain and simulation code
core/        libGDX application / presentation layer
lwjgl3/      desktop launcher
assets/      data definitions and presentation assets
```

The authoritative state must not migrate into `core` or `lwjgl3` for convenience.

## High-level execution model

EvoForge combines several architectural ideas rather than following one named pattern exclusively:

```text
OO domain model
+ immutable composition-driven definitions
+ specialized mutable state owners
+ scheduler/event-driven execution
+ external-intent Command boundary
+ narrow coordinated domain write capabilities
+ deterministic authoritative mutation
+ indexed spatial/world queries
+ selective DOD after profiling
```

Objects are real domain objects with stable identity, but mutable mechanics are not accumulated inside `WorldObject`. Definitions describe immutable composition. Systems own authoritative runtime properties. The scheduler controls when work occurs but does not own domain semantics.

External Player/AI/script/scenario intent converges on Control Commands. Internal simulation processes are not forced back through Command and may use explicitly granted narrow domain APIs directly.

## World decomposition

The current world is intentionally split into object and landscape domains.

```text
WORLD
├── Objects
│   ├── identity / existence
│   ├── definitions
│   └── object positions
│
└── Landscape
    ├── terrain material/content
    ├── coordinated LandscapeMutations boundary
    └── mechanics layered over terrain
```

Both domains use the same integer XYZ address space. That does not mean they share one storage owner. Terrain is not converted into `WorldObject` instances just because it occupies coordinates.

## Determinism

For the same authoritative initial state, submitted command sequence, and authoritative random state, the simulation must produce the same supported result. This means authoritative behavior cannot depend on unspecified `HashMap` iteration order, uncontrolled random sources, thread timing, or worker threads mutating the world directly.

The current synchronous Control delivery executes one submitted command immediately, so later calls observe earlier mutations. Any future queued delivery must specify its own deterministic ordering and visibility semantics explicitly.

Cross-platform bit-identical floating-point semantics are not currently promised. A stricter numeric contract will be introduced only if a mechanic actually requires it.

## Extension philosophy

The project distinguishes new content from new mechanics.

If existing mechanics already express a new object or landscape type, add definition data. If genuinely new runtime behavior is required, introduce a specialized mechanic with its own definition compiler/state owner/system and tests. Do not expand a central `WorldObject`, `TerrainSystem`, or registry simply because adding one field would be convenient.

The same rule applies to geometry: a new Shape is a new `Shape` implementation. Navigation must not gain `instanceof RampShape` or a switch over known shape types.

For external intent, a new Command adds a typed command/result/handler under the appropriate Control use-case. `CommandDispatcher` does not gain a central domain switch. Internal mechanics should not invent Commands merely to call another system.

## Current state

Completed foundations include:

```text
Object identity and repository
Definition loading and aspect compilation
Simulation clock and scheduler
Discrete XYZ object positioning and spatial index
Landscape definitions and terrain storage
Coordinated LandscapeMutations lifecycle boundary
Geometry abstraction and Shape contract
TransitionMask / TransitionPorts / TransitionComposition
FullShape
Cardinal RampShape
Directed structural Navigation resolver
Control Backbone core and synchronous delivery
PlaceTerrainCommand vertical slice
```

The next major consumer is the Scenario Harness. Later phases will introduce basic movement, occupancy, pathfinding, and the first agent vertical slice.

See [Control Backbone](Control-Backbone.md) for the command model and [Roadmap and Deferred Decisions](Roadmap-and-Deferred-Decisions.md) for the deliberate gaps that remain open.

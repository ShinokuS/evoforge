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
- a central type switch that knows every object, terrain type, Shape, Command or timed process;
- a command bus through which every internal mutation must be routed;
- a generic Action framework that owns every timed mechanic;
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
+ actor-independent structural topology and edge cost
+ selective DOD after profiling
```

Objects are real domain objects with stable identity, but mutable mechanics are not accumulated inside `WorldObject`. Definitions describe immutable composition. Systems own authoritative runtime properties. Scheduler controls when work occurs but does not own domain semantics.

External Player/AI/script/scenario intent converges on Control Commands. Internal simulation processes are not forced back through Command and may use explicitly granted narrow domain APIs directly.

Timed Movement is the first concrete proof of this model: a synchronous external command may start a long-lived `MovementAction`; Scheduler later resumes the domain process directly, without turning completion into another internal Command.

## World decomposition

The current world is intentionally split into object and landscape domains.

```text
WORLD
├── Objects
│   ├── identity / existence
│   ├── definitions
│   ├── Spatial position
│   └── timed Movement state
│
└── Landscape
    ├── terrain material/content
    ├── coordinated LandscapeMutations boundary
    ├── Geometry / Shape topology
    └── landscape traversal definitions
```

Both domains use the same integer XYZ address space. That does not mean they share one storage owner. Terrain is not converted into `WorldObject` instances just because it occupies coordinates.

## Structural movement model

The current movement chain deliberately separates different questions:

```text
Navigation
    -> is A -> B a valid directed structural neighbor edge?

TransitionCost
    -> what is the actor-independent intrinsic price of that valid edge?

MovementRate
    -> how fast does this object convert cost into simulation time?

MovementAction
    -> wait, revalidate and commit Spatial or interrupt
```

Navigation depends only on Geometry and does not know actor identity, cost or Pathfinder.

TransitionCost combines both supporting landscape cells, each Shape's own departure/arrival traversal contribution, and discrete grid direction length. It is currently actor-independent.

Movement then converts that cost into ticks with deterministic per-object fractional carry. The object remains authoritatively at its source cell until scheduled completion succeeds.

Future Pathfinder must consume the same Navigation + TransitionCost semantics rather than inventing a second topology or edge-price model.

## Time model

Simulation time is discrete and authoritative.

```text
SimulationClock
    -> current tick

SimulationStepper
    -> advance one production tick

Scheduler
    -> activate due domain processes in deterministic order
```

Current production one-tick order is:

```text
clock.advance()
Scheduler.dispatchDue(clock.tick())
```

Presentation FPS is not simulation time. A future 1x/2x/5x presentation-speed control should change how quickly production ticks are advanced in real time, not alter MovementRate or edge cost to simulate speed-up.

## Determinism

For the same authoritative initial state, submitted command sequence, and authoritative random state, the simulation must produce the same supported result. This means authoritative behavior cannot depend on unspecified `HashMap` iteration order, uncontrolled random sources, thread timing, or worker threads mutating the world directly.

Timed Movement and TransitionCost now provide concrete deterministic numeric behavior:

```text
fixed-point integer edge-cost arithmetic
stable Scheduler ordering
per-object movement timing carry
minimum one-tick movement duration
production tick semantics independent of caller batching/FPS
```

The current synchronous Control delivery executes the submitted handler immediately. For timed Movement, this means the action is started immediately but completion still occurs later through simulation time.

Cross-platform bit-identical floating-point semantics are not currently promised. Current Movement/TransitionCost avoids floating point in authoritative timing/cost arithmetic entirely.

## Extension philosophy

The project distinguishes new content from new mechanics.

If existing mechanics already express a new object or landscape type, add definition data. For example, a new landscape material can define another positive `traversal.cost` without changing Movement code.

If genuinely new runtime behavior is required, introduce a specialized mechanic with its own definition compiler/state owner/system and tests. Do not expand a central `WorldObject`, `TerrainSystem`, Scheduler or registry simply because adding one field/switch would be convenient.

The same rule applies to geometry: a new Shape is a new `Shape` implementation. Navigation and `TransitionCostCalculator` must not gain `instanceof NewShape` or a switch over known shape types. If the Shape has an intrinsic actor-independent traversal effect, it contributes that locally through the same departure/arrival role law.

For external intent, a new Command adds a typed command/result/handler under the appropriate Control use-case. `CommandDispatcher` does not gain a central domain switch. Internal mechanics should not invent Commands merely to call another system.

For timed mechanics, domain process state stays in the domain and normally binds to Scheduler through one `ProcessScheduler`/handler family rather than a universal `ActionSystem`.

## Current state

Completed foundations and vertical slices include:

```text
Object identity and repository
Definition loading and aspect compilation
SimulationClock and Scheduler
SimulationTime / ProcessScheduler / BoundProcessScheduler
production SimulationStepper
Discrete XYZ object positioning and spatial indexes
Landscape definitions and terrain storage
Coordinated LandscapeMutations lifecycle boundary
Geometry abstraction and Shape contract
TransitionMask / TransitionPorts / TransitionComposition
FullShape
Cardinal RampShape
Directed structural Navigation resolver
GridTransitionLength
Control Backbone core and synchronous delivery
PlaceTerrainCommand vertical slice
deterministic test-only Scenario fixture
movement.rate object capability
Timed MoveStep MovementAction lifecycle
completion-time Movement revalidation
persistent fractional movement timing carry
landscape traversal.cost
Shape departure/arrival traversal factors
actor-independent TransitionCostCalculator
```

The next required milestone is the minimal Z-level visual/debug view so existing terrain, ramps, objects, Navigation and discrete timed Movement become directly observable by a human.

After that, planned milestones are Occupancy, Pathfinder, the first agent vertical slice and World generation.

Known Movement gaps are explicit: destination reservation, early cancellation, reactive wake-up on world mutation, actor-specific surface affinity and multi-step `MoveTo` remain deferred until their consumers exist.

See [Movement System](Movement-System.md) for the detailed current movement/cost contract, [Control Backbone](Control-Backbone.md) for the external-intent model, [Time and Scheduler](Time-and-Scheduler.md) for timed-process binding, and [Roadmap and Deferred Decisions](Roadmap-and-Deferred-Decisions.md) for the remaining deliberate gaps.

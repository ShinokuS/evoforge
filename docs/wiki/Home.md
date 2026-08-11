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
8. [Control Backbone](Control-Backbone.md)
9. [Movement System](Movement-System.md)
10. [Time and Scheduler](Time-and-Scheduler.md)
11. [Testing Strategy](Testing-Strategy.md)
12. [Development Workflow](Development-Workflow.md)
13. [Roadmap and Deferred Decisions](Roadmap-and-Deferred-Decisions.md)

## Current architecture at a glance

```text
External intent
    ↓
Control Backbone
    ↓
authoritative domain APIs

WORLD
├── Objects
│   ├── ObjectRepository        identity / existence
│   ├── ObjectFactory           definition-backed creation
│   ├── SpatialSystem           authoritative ObjectId -> XYZ
│   └── MovementSystem          timed adjacent execution
│            │
│            ├── NavigationLookup        structural permission
│            ├── TransitionCostLookup    intrinsic edge price
│            ├── MovementRate            actor rate
│            └── ProcessScheduler        delayed completion
│
└── Landscape
    ├── LandscapeMutations      coordinated write boundary
    └── TerrainSystem           XYZ -> LandscapeDefinitionId | absence
             │
             ▼
        GeometrySystem          terrain presence -> Shape
             │
             ▼
        NavigationSystem        Shape contributions -> structural edges

TIME
├── SimulationClock
├── Scheduler
├── BoundProcessScheduler
└── SimulationStepper
```

The central design rule is ownership: every mutable authoritative fact has one owner. Shared coordinates do not imply shared storage, and a convenient query does not justify moving domain responsibility into the query layer.

Commands carry external intent into the simulation. Internal processes do not need to turn every mutation into a Command; they may use explicitly granted narrow domain write capabilities.

## Geometry, Navigation, cost and Movement

A terrain `Shape` is anchored at a terrain coordinate and contributes local topology. Navigation composes those contributions without knowing concrete Shape types.

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

Once Navigation says a directed edge exists, `TransitionCostCalculator` prices that edge from both supporting landscape cells, directed Shape traversal factors and grid direction length. Movement then divides that cost by the mover's definition-backed `MovementRate`, preserving fractional timing through per-object carry.

Accepted movement remains discrete and timed:

```text
MoveStepCommand
    -> MovementAction starts
    -> source position remains authoritative
    -> Scheduler wakes completion later
    -> transition is revalidated
    -> SpatialSystem.move commits destination
```

See [Movement System](Movement-System.md) for the full contract and formulas.

## Stable documentation layers

The project intentionally keeps three documentation levels:

- `docs/ARCHITECTURE.md` is the concise architectural contract: stable boundaries, invariants, and deferred decisions.
- `docs/TECHNICAL_REFERENCE.md` describes the current implementation, packages, algorithms, tests, and known technical gaps.
- this Wiki is the explanatory layer: examples, diagrams, reasoning, extension guidance, and subsystem walkthroughs.

The Wiki is generated from `docs/wiki/` in the main repository. Do not edit generated Wiki pages as the primary source; update `docs/wiki/` through the normal pull-request workflow instead. See [Wiki Maintenance](Wiki-Maintenance.md).

## Current project phase

The implemented foundation now includes definitions, object identity, scheduling, production simulation stepping, discrete object spatial state, landscape terrain, geometry, structural transition algebra, `FullShape`, cardinal `RampShape`, directed local Navigation, the Control Backbone, deterministic Scenario fixtures, timed adjacent Movement, and the actor-independent TransitionCost model.

Movement is now a real Scheduler consumer: an accepted `MoveStepCommand` creates an active action, waits a deterministic number of simulation ticks, revalidates the edge at completion, and commits Spatial only if the transition is still valid.

The transition-cost layer now includes:

```text
landscape traversal.cost
source departure Shape factor
destination arrival Shape factor
cardinal / double-diagonal / triple-diagonal grid length
fixed-point deterministic arithmetic
```

The next required gameplay milestone is the minimal Z-level debug visualization, followed by Occupancy, Pathfinder, the first agent vertical slice, and world generation.

The project still deliberately avoids speculative systems. Actor-specific surface affinity, early movement cancellation, reactive wake-up on landscape mutation, full Occupancy semantics, `MoveTo`, Pathfinder details, final renderer architecture, world chunks/regions and generation algorithms remain deferred until their real milestone provides concrete requirements.

## Navigation

Use the sidebar for the full documentation map. The [Glossary](Glossary.md) defines project-specific terms such as *authoritative owner*, *terrain anchor*, *standing position*, *departure*, *arrival*, *block*, *structural edge*, and related simulation concepts.

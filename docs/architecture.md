# EvoForge Architecture

Architecture is the constitution of EvoForge. It defines where truth lives, how concepts depend on one another, how reusable capabilities are exposed, how workflows compose them, and which structural mistakes CI must reject.

The short version is:

> EvoForge is one authoritative `simulation` module built from independent semantic modules. Each semantic module represents a concept that can evolve independently and exposes consumer-neutral capabilities. Mechanics/workflows consume those capabilities; they do not own them merely because they use them first.

See ADR-026 for the decision that supersedes the earlier rigid owner/mechanic taxonomy.

## Start here before any repository change

```text
AGENTS.md
   ↓
project-context.md
   ↓
architecture.md
   ↓
relevant systems/* + ADRs
   ↓
actual semantic module + tests + dependency graph
   ↓
only then: design/edit code
```

The repository must remain understandable without chat history.

## Repository boundary

```text
simulation/   all authoritative simulation, Genesis, world concepts, capabilities, mechanics and agents
core/         libGDX presentation, observer/debug UI and presentation adapters
lwjgl3/       desktop launcher
assets/       authored definitions and presentation resources
docs/         canonical explanation, rationale and history
```

Only `simulation`, `core` and `lwjgl3` are code/Gradle modules unless a future accepted ADR establishes a genuinely independent artifact.

`simulation` is pure Java and must be runnable/testable without presentation state.

## Why semantic modules are the primary unit

Technical stages are unstable repository boundaries:

```text
generation/liquid
physics/liquid
storage/liquid
runtime/liquid
```

They scatter one concept across several places.

Feature chronology is also an unstable boundary:

```text
movement/occupancy
movement/navigation
movement/pathfinding
```

because a later feature may need Occupancy, Navigation or Pathfinding without being Movement.

The stable question is:

> What independent concept does this code represent?

A semantic module groups decisions that change for the same conceptual reason and hides its implementation behind a small public surface.

Examples:

```text
world/object
world/space
world/space/occupancy
world/geometry
world/navigation
world/visibility
world/geophysics
world/terrain
world/liquid
world/soil
world/atmosphere
agents
```

The exact tree grows only with real code.

## The consumer-independence test

Let `C` be a concept/capability and `U` a current consumer.

If `C` can be defined and tested without mentioning `U`, then `C` must not live under `U`.

```text
U -> C
```

not:

```text
U/internal/C
```

### Reference examples

Occupancy means whether a location is available/admissible/reserved. That meaning exists without Movement.

Therefore:

```text
Movement -> Occupancy
Drop     -> Occupancy
Spawn    -> Occupancy
Build    -> Occupancy
```

and never:

```text
Drop -> Movement/Occupancy
```

Navigation means world connectivity/traversability. MoveTo may consume it, but Navigation does not belong to MoveTo.

Pathfinding is an algorithm/capability that answers path queries over Navigation/traversal facts. Agents, Movement, logistics or future systems may use it directly.

Objective Geometry describes physical shape independent of whether a mover, liquid, ray or construction mechanic interprets that shape.

## Architectural roles are orthogonal

The repository no longer requires a block to be exactly one mutually-exclusive architectural type.

A semantic module may contain several roles when they all express one concept:

```text
AUTHORITY   authoritative mutable facts
CAPABILITY  consumer-neutral operation/query
ALGORITHM   replaceable policy/solver
PROJECTION  rebuildable derived cache/index/view
PROCESS     runtime lifecycle intrinsic to the concept
GENESIS     initialization intrinsic to the concept
```

Global roles exist outside semantic concepts:

```text
KERNEL       domain-neutral execution/time/scheduling
COMPOSITION  wiring and lifecycle selection
WORKFLOW     mechanic/use case coordinating semantic capabilities
```

The role does not determine the package. The semantic concept does.

## One authority per mutable fact

For every mutable fact `F`:

```text
|authority(F)| = 1
```

A semantic capability may derive information from another authority without copying it as truth.

Example:

```text
Object Position authority
        ↓
Cell Spatial Index      rebuildable projection
        ↓
Occupancy View          derived present occupancy

Occupancy Reservation authority
        ↑
Occupancy capability
```

This is valid because object position and reservation claim are different facts.

Invalid:

```text
PositionState.position
OccupancyState.position
MovementState.position
RendererState.position
```

all pretending to be authoritative.

## Authorities, capabilities and projections inside one concept

A semantic module can be deep rather than artificially fragmented.

Example shape:

```text
world/space/occupancy/
    OccupancyView.java
    CellAdmission.java
    CellReservation.java
    internal/
        ReservationState.java
        OccupancyResolver.java
        OccupancyIndex.java
```

Consumers know the public semantics. They do not know whether occupancy is computed from an index, sparse map, packed bitset, ECS query or another representation.

## Mechanics/workflows

A mechanic is a causal process that coordinates independent semantic capabilities.

Example:

```text
Movement
   ├── Position mutation
   ├── Occupancy/admission
   ├── Navigation/traversal
   ├── Geometry
   └── Time/Scheduling
```

Movement owns movement-specific state only, for example an active movement order, timing carry or current transition process when those facts are genuinely Movement facts.

It does not own:

```text
object position
occupancy semantics
navigation graph semantics
objective geometry
scheduler state
```

A future Drop mechanic can therefore be:

```text
Drop
   ├── Object placement
   └── Occupancy/admission
```

without any dependency on Movement.

This is the core reuse guarantee.

## Dependency direction follows semantic stability

More fundamental/reusable concepts must not depend on higher-level consumers.

Conceptually:

```text
             presentation adapters
                    ↑
                 agents
                    ↑
          mechanics / workflows
                    ↑
      reusable semantic capabilities
                    ↑
        fundamental world concepts
                    ↑
        kernel + neutral primitives
```

This diagram defines dependency tendency, not a mandatory package-layer tree.

Packages stay semantic.

### Cycles

A cycle such as:

```text
A -> B -> A
```

is evidence of one of these:

1. a smaller reusable capability/contract is missing;
2. A and B are not actually independent concepts;
3. a workflow has been embedded into a lower-level module;
4. one module is reading implementation detail instead of a public semantic contract.

Do not hide the cycle behind an event bus, service locator or universal context.

## Public capability design

Public contracts use consumer-neutral names.

Good:

```text
PositionView
ObjectPlacement
OccupancyView
CellAdmission
CellReservation
GeometryView
NavigationView
PathQuery
VisibilityQuery
LiquidView
LiquidTransfer
```

Suspicious when intended for reuse:

```text
MovementOccupancy
AgentPathService
DropPlacementHelper
BuildGeometryManager
```

A public name that contains one consumer is allowed only if the semantics truly belong only to that consumer.

## Package form

A semantic module normally exposes a small root surface and hides implementation:

```text
<semantic concept>/
    <public contracts and stable semantic types>
    internal/
        <private implementation roles>
```

Do not create empty directory taxonomies in advance.

Do not introduce root technical buckets such as:

```text
capabilities/
services/
shared/
common/
utils/
helpers/
```

Consumer-neutral capability orientation is **not** a global `capabilities` layer.

## Target simulation map

The final map is intentionally conceptual, not a requirement to create empty packages:

```text
io.github.evoforge.simulation
├── kernel/
│   ├── time/
│   ├── scheduling/
│   └── command/
├── definition/                 neutral authored-definition infrastructure only
├── genesis/                    global startup composition only
├── world/
│   ├── continuum/              neutral large-world addressing/materialization
│   ├── geophysics/             continuous configurable macro-geophysical skeleton
│   ├── material/               authored material identity shared by semantic aspects
│   ├── object/                 object identity/existence semantics
│   ├── space/
│   │   ├── position/           object position authority + rebuildable cell index
│   │   ├── orientation/        independent facing/orientation semantics
│   │   ├── occupancy/          consumer-neutral admission/reservation semantics
│   │   ├── placement/          generic placement over admission + position mutation
│   │   └── measurement/        physical space/volume units
│   ├── geometry/               objective physical geometry
│   ├── navigation/             connectivity/traversal/pathfinding capability
│   ├── geology/                authored geological profile/unit/material semantics
│   ├── terrain/
│   ├── liquid/
│   ├── soil/
│   ├── atmosphere/
│   ├── sky/                    derived sky-exposure/surface capability
│   └── interaction/            interaction-access semantics
├── mechanics/
│   ├── movement/               movement-specific workflow only
│   ├── hydrology/              cross-concept environmental water workflows
│   └── terrainmutation/        coordinated Terrain + Geometry + Traversal invalidation
├── agents/
├── persistence/                only when real persistent-state responsibilities exist
└── diagnostics/                observer-only diagnostics when shared scope is real
```

`position`, `occupancy`, `navigation` etc. are shown to clarify the rule; final package boundaries are accepted only after code/dependency audit.

## Genesis

Genesis is lifecycle, not a second semantic owner.

```text
world-level intent
      ↓
global Genesis orchestration
      ↓
semantic-module-local initialization
      ↓
authoritative facts
================ runtime boundary
normal authority/processes
```

Do not create global `generation/<domain>` trees.

## Physics

Physics is not automatically a repository layer.

```text
Liquid-specific law       -> Liquid semantic module
Atmosphere-specific law   -> Atmosphere semantic module
cross-concept physical law -> named mechanic/workflow
consumer-neutral geometric law -> Geometry
consumer-neutral spatial admission -> Occupancy/Space
```

This prevents both technical-layer scattering and first-consumer capture.

## Navigation and Pathfinding

Navigation/Pathfinding are explicitly **not** Movement internals merely because Movement currently uses them.

Navigation owns reusable world-connectivity/traversability semantics.

Pathfinding is a consumer-neutral query/algorithm behind navigation/path contracts.

Conceptually:

```text
PathQuery
   ↑
AStarPathfinder
future hierarchical solver

Consumers:
Movement -> PathQuery
Agent planning -> PathQuery
future logistics -> PathQuery
```

Consumers do not depend on each other.

## Geometry and traversal

Objective Geometry must remain consumer-neutral.

```text
Geometry
   ├── used by Liquid flow
   ├── used by Navigation
   ├── used by Visibility
   ├── used by placement/collision
   └── used by presentation projection
```

Traversal rules that are actor/profile-specific must not contaminate objective geometry.

If reusable traversal semantics emerge independently of Movement, they belong to an independent semantic capability rather than Movement internals.

## Object, position and occupancy

These are distinct concepts:

```text
Object     existence/identity/type
Position   where an object is
Occupancy  whether a spatial location is occupied/admissible/reserved
```

Occupancy may derive actual occupancy from Position + object definitions while owning independent reservations.

A placement operation should compose Position and Occupancy through public contracts so every consumer shares one admission rule.

This is the reference pattern for future reuse decisions.

## Agents

Agent cognition is a semantic concept separate from physical Object/Space capabilities.

An Agent may consume:

```text
Visibility
Navigation/PathQuery
Movement commands
Object queries
Liquid/food opportunities
```

Those capabilities must not depend on Agent merely because Agent currently uses them.

## Kernel

Kernel contains domain-neutral machinery only.

Examples:

```text
clock/time
scheduling
command dispatch
```

Kernel never learns Terrain, Liquid, Occupancy, Agent or Movement semantics.

## Composition

Composition may know many public contracts but owns no domain policy.

```text
many semantic modules -> Composition -> running Simulation
```

No physical formulas, actor decisions or feature-specific rules belong there.

## Definitions

Root `definition` contains only genuinely neutral authored-definition infrastructure.

Domain-specific definitions stay with their semantic concept.

Do not turn runtime algorithm knobs into content definitions merely for configurability.

A semantic setting is justified when it describes authored world meaning independently of the current implementation. Stage 5 is the reference example: ocean prevalence, continental scale, landmass cohesion, fragmentation and macro variation are authored geophysical intent, while lattice spans, salts, interpolation exponents and blend coefficients remain hidden algorithm policy.

## Determinism

For fixed authoritative inputs and compatible model revision:

```text
Result = f(authoritative inputs, revision)
```

Result must not depend on:

- render frame cadence;
- camera/observer position;
- hash iteration order;
- request order where non-semantic;
- cache eviction/reload;
- whether a region is visible.

## Simulation-thread authority

Authoritative mutation currently occurs on the simulation thread.

Background work may prepare immutable/rebuildable results but may not silently mutate live world truth.

A future concurrency model requires an explicit ADR defining deterministic commit/ownership semantics.

## Continuum

Continuum owns neutral large-world addressing/materialization/query/cache mechanics.

It does not own semantic Terrain/Liquid/Geology/Geophysics truth.

Pages/tiles/chunks are representation, not natural geography.

## Performance architecture

Performance optimization stays behind semantic capabilities.

```text
semantic invariant
      ↓
representative workload
      ↓
measurement
      ↓
hidden representation/algorithm optimization
      ↓
same public capability + same semantics
      ↓
regression evidence
```

Rules:

- avoid whole-world scans when work can be local/active-set based;
- avoid mandatory per-object/per-cell work every tick when deterministic sleeping/analytical behavior is equivalent;
- avoid avoidable allocation/boxing on proven hot paths;
- explicitly bound caches in an effectively unbounded world;
- measure memory/work count as well as latency;
- use sparse/packed/ECS/data-oriented/paged representations internally when measurement justifies them;
- never expose one consumer's optimized representation as the semantic interface of a reusable capability.

## Replaceability

Replaceability has three useful levels.

### Algorithm

```text
PathQuery <- AStarPathfinder / future solver
```

Stage 5 follows the same rule: consumers request `MacroGeophysicalField` through the `MacroGeophysics` creation boundary and do not construct the current hidden deterministic implementation directly.

### Semantic-module implementation

Occupancy storage/index/reservation implementation may change without changing Movement/Drop/Build consumers.

### Consumer/workflow

A mechanic can be removed/replaced without taking reusable capabilities with it.

This third level is the important addition from ADR-026.

## Testing architecture

```text
pure rule/algorithm           -> unit/property test
authority/state lifecycle     -> semantic-module invariant test
reusable capability           -> consumer-independent contract test
replaceable seam              -> substitution test
mechanic/workflow             -> focused integration over public capabilities
deterministic process         -> replay/order test
physical transfer             -> conservation/bounds test
large/hot path                -> scale/performance profile
structural law                -> architecture fitness test
visual/aesthetic property     -> manual visual acceptance where necessary
```

A capability that can only be tested through its first consumer is a design warning.

## Architecture fitness checks

CI must progressively enforce:

- accepted Gradle topology only;
- simulation cannot depend on presentation/libGDX;
- no semantic dependency cycles (ArchUnit checks top-level `world/*` slices from production bytecode);
- no foreign `internal` imports;
- Kernel independence from domains;
- reusable semantic modules do not depend on mechanics/agents/presentation consumers;
- mechanics do not hide general-purpose capabilities under feature internals;
- Continuum independence from concrete natural domains;
- no generic root dumping-ground packages;
- deterministic/scale gates stay green.

A critical mechanically-checkable rule should not exist only as prose.

## Change-amplification and reuse tests

Before accepting a boundary ask:

1. If the concept changes, are edits localized to that concept plus explicit consumers?
2. If the current consumer disappears, does the concept still have a coherent home?
3. If a second plausible consumer appears, can it use the existing public capability without moving code?
4. Can the implementation be optimized/replaced without consumers learning storage/algorithm details?

A “no” answer is architectural debt now, not something to postpone until the second feature arrives.

## Stop conditions

Stop and redesign when any of these appears:

- two authorities for one mutable fact;
- reusable concept under its first consumer;
- new consumer would force extraction/movement of old shared code;
- mechanic becomes a warehouse for Occupancy/Navigation/Visibility/etc.;
- lower-level capability depends on higher-level workflow;
- semantic dependency cycle;
- foreign internal import;
- universal service/context bag;
- central concrete-type switch grows with every feature;
- generic `shared/common/services/capabilities` bucket;
- global generation/physics/storage domain split;
- cache/projection becomes authoritative;
- observer/camera changes simulation truth;
- one conceptual change causes unrelated package surgery.

## Architectural sources

Primary/conceptual influences:

- David Parnas, *On the Criteria To Be Used in Decomposing Systems into Modules*: https://doi.org/10.1145/361598.361623
- Simon Brown, modular monolith/package-by-component: https://simonbrown.je/modular-monolith/
- Martin Fowler, Bounded Context: https://martinfowler.com/bliki/BoundedContext.html
- Alistair Cockburn, Hexagonal Architecture: https://alistair.cockburn.us/hexagonal-architecture/
- John Ousterhout, software complexity/deep modules: https://web.stanford.edu/~ouster/cgi-bin/cs190-winter18/lecture.php?topic=complexity
- ArchUnit executable architecture constraints: https://www.archunit.org/userguide/html/000_Index.html

These are influences; EvoForge does not claim to implement any one framework wholesale.

## Current architecture authority

Read in this order:

1. root `AGENTS.md`;
2. this file;
3. ADR-026;
4. relevant system docs;
5. actual current code/tests.

ADR-025 is partially superseded. ADR-023 is historical.

# EvoForge Repository Laws

This file is the mandatory entry point for every human or AI-assisted repository change. Read it **before** designing, editing, moving or deleting code, changing CI, or updating documentation.

The laws below override convenience, historical package names and the needs of the first feature that happens to use a concept.

## 0. Required context before any change

Before editing anything:

1. read this file;
2. read `docs/project-context.md`;
3. read `docs/architecture.md`;
4. read the relevant current page under `docs/systems/`;
5. read relevant accepted ADRs, especially ADR-026;
6. inspect the actual current semantic module, its tests, public contracts and dependency direction;
7. state the concept being changed, its authorities, reusable capabilities, consumers, invariants, performance risk and verification plan.

Never infer current architecture from chat history or old branches when code/tests/normative docs are available.

## 1. Repository boundary

The code-module topology is:

```text
simulation/   all authoritative simulation, Genesis, world state, physics, capabilities, mechanics and agents
core/         libGDX presentation, observer/debug UI and presentation adapters
lwjgl3/       desktop launcher
```

Do not create Gradle modules merely to represent Terrain, Liquid, Generation, Physics, Movement, Agents or another simulation concept.

`simulation` is pure Java and must not depend on presentation/camera/rendering truth.

## 2. Primary architectural unit: the semantic module

The primary decomposition question is:

> **What independent concept does this code represent?**

A semantic module is a cohesive concept whose meaning can be explained and tested without naming the feature that first happened to need it.

Examples of independent concepts include, when real code exists:

```text
world/object
world/space
world/space/occupancy
world/geometry
world/navigation
world/visibility
world/terrain
world/liquid
world/soil
world/atmosphere
agents
```

Do not organize reusable concepts under consumers merely because of chronology.

## 3. Reusable capability law

A consumer never owns a reusable capability merely because it uses it first.

Let `C` be a capability and `U` a mechanic/use case/consumer.

If `C` can be defined without mentioning `U`, then:

```text
U -> C
```

and **not**:

```text
C inside U
C -> U
U and C mixed as one package
```

Examples:

- Occupancy can be defined without Movement -> Occupancy is independent of Movement.
- Navigation can be defined without MoveTo -> Navigation is independent of MoveTo.
- Pathfinding can be defined without Agent decision making -> it is consumer-neutral Navigation capability/algorithm.
- Objective Geometry can be defined without traversal -> Geometry is independent of Movement.
- Generic placement/admission can be used by Movement, Drop, Spawn and Build -> it must not live inside any one of them.

### Reuse test before placing code inside a feature

Ask:

1. Does this concept have meaning without the current consumer?
2. Could another plausible mechanic need the same semantics?
3. Would that second consumer otherwise need to import the first consumer or duplicate rules?

If yes, create/use an independent semantic module or public capability now.

This is justified abstraction when the **domain meaning is already independent**, even with one current consumer. It is not permission to extract speculative helpers.

## 4. Architectural roles are orthogonal

Do **not** force every module into one mutually-exclusive type.

A semantic module may contain several roles when all belong to the same concept:

- **AUTHORITY** — authoritative mutable state;
- **CAPABILITY** — stable operation/query usable by arbitrary consumers;
- **POLICY / ALGORITHM** — replaceable rule/solver behind the capability;
- **PROJECTION** — rebuildable derived cache/index/view;
- **PROCESS** — lifecycle behavior intrinsic to the concept;
- **GENESIS** — initialization intrinsic to the concept.

Repository-wide roles also exist:

- **KERNEL** — domain-neutral execution infrastructure;
- **COMPOSITION** — wiring/lifecycle selection without domain policy;
- **WORKFLOW / MECHANIC** — causal orchestration across independent semantic modules.

A module is split only when concepts or design decisions can vary independently, not to satisfy a role taxonomy.

## 5. One authoritative source per mutable fact

For every mutable fact `F`:

```text
|authority(F)| = 1
```

No coordinator, cache, renderer, mechanic or convenience object may become a shadow authority.

Derived data must satisfy:

```text
P = f(A)

A = authoritative facts
P = rebuildable projection/cache/index
```

Deleting `P` may hurt performance but must not delete world truth.

A reusable capability may own genuinely independent state of its own. Example: Occupancy may derive present occupancy from object positions while owning reservation claims. It must not copy object positions as a second truth.

## 6. Mechanics/workflows compose capabilities

A mechanic owns only the causal process/policy specific to that mechanic.

Example:

```text
Movement
  -> Navigation capability
  -> Traversal capability
  -> Occupancy/admission capability
  -> Position mutation capability
  -> Time/Scheduling
```

A future mechanic:

```text
DropItem
  -> Occupancy/admission capability
  -> Position/placement capability
```

must reuse the same capabilities without depending on Movement.

If adding a new mechanic forces a shared concept to move out of an older mechanic, the old placement was architecturally wrong.

## 7. Package axis: semantic concept first, technical role second

Correct:

```text
world/liquid/
  <small public semantic surface>
  internal/
    genesis/
    flow/
    storage/
```

Also correct when the concept is independently reusable:

```text
world/space/occupancy/
world/navigation/
world/geometry/
```

Forbidden global technical decomposition:

```text
generation/liquid/
physics/liquid/
storage/liquid/
services/liquid/
capabilities/occupancy/
```

Do not create a root `capabilities/`, `services/` or `shared/` dumping ground. Capabilities live with the semantic concept they express.

## 8. Dependency direction follows semantic stability

More fundamental/reusable concepts must not depend on higher-level workflows that consume them.

Typical dependency direction:

```text
kernel + neutral values
        ↑
fundamental semantic concepts
        ↑
reusable semantic capabilities
        ↑
mechanics/workflows
        ↑
agents / scenario orchestration / presentation adapters
```

This is a dependency rule, **not a mandatory folder-layer tree**.

Forbidden:

- lower-level reusable concept -> first consumer;
- foreign `..internal..` imports;
- circular semantic dependencies;
- universal `WorldContext`, `SimulationContext`, `PhysicsContext`, `GenerationContext` or service bags;
- service locators/global arbitrary registries;
- generic event bus as causal simulation backbone.

A cycle means one of three things until proven otherwise:

1. a smaller consumer-neutral capability is missing;
2. two packages are actually one semantic module;
3. a workflow is embedded in a lower-level concept.

## 9. Public surface and internals

Public contracts describe semantic meaning, never the current consumer.

Prefer:

```text
OccupancyView
CellAdmission
CellReservation
PositionView
ObjectPlacement
NavigationGraph
PathQuery
VisibilityQuery
```

Avoid reusable APIs such as:

```text
MovementOccupancy
AgentNavigationService
DropPlacementHelper
```

unless those semantics are genuinely consumer-specific.

Other modules must never import another semantic module's `..internal..` package.

Prefer deep modules: small semantic surface, substantial hidden implementation.

## 10. File/package naming laws

Names describe concepts and contracts, not architectural fashion.

### Packages

Use lower-case semantic nouns or precise process nouns:

```text
liquid
terrain
occupancy
navigation
visibility
movement
scheduling
```

Forbidden generic dumping grounds:

```text
util common misc helpers shared framework managers services base capabilities
```

### Types

Use names such as:

- `<Concept>Id` — stable identity;
- `<Concept>Definition` — authored immutable semantics only;
- `<Concept>View` / precise query noun — read capability;
- verb/operation-specific mutation nouns such as `ObjectPlacement`, `CellAdmission`, `LiquidTransfer`;
- `<Concept>Index`, `<Concept>Cache`, `<Concept>Projection` — derived data;
- mathematically/domain-specific algorithm names such as `AStarPathfinder`, not `Helper`;
- `<Operation>Result` / `<Operation>Attempt` — result local to the operation;
- `<Scope>Assembly` only at true composition boundaries.

Avoid vague `Manager`, `Utils`, `Common`, `Data`, `Stuff`, `Processor`, generic `Service` and default `Impl` naming.

## 11. File creation decision protocol

Before adding a non-trivial file/package:

```text
1. What independent semantic concept is this?
2. Does it already have a module?
3. Is this reusable meaning or merely an implementation helper?
4. Which facts, if any, are authoritative here?
5. Which public capability does a consumer actually need?
6. Does the proposed package name mention a consumer unnecessarily?
7. Could a second plausible consumer reuse it without moving it?
8. What lower-level concepts may it depend on?
9. What must never depend back on it?
10. What invariant/test proves the boundary?
```

If questions 1–7 cannot be answered cleanly, do not create the file yet.

## 12. Genesis, physics and storage are secondary axes

Genesis creates initial facts then hands them to normal authorities. Domain-specific generation remains with the semantic concept created.

Physical behavior belongs with its semantic concept when concept-local. A genuinely cross-concept law becomes a mechanic/workflow.

Storage, ECS/data-oriented layouts, sparse maps, packed arrays, pages and caches are hidden implementation choices unless they themselves represent an independent semantic concept.

Do not create global `generation`, `physics` or `storage` domain trees.

## 13. Kernel law

Kernel contains only domain-neutral machinery such as time, scheduling and generic command dispatch.

Kernel answers **when/how execution is scheduled**, never why Liquid flows or why an Agent acts.

Kernel must not depend on world domains, mechanics, agents or presentation.

## 14. Determinism and authority

For fixed authoritative inputs and compatible model revision, deterministic simulation results must not depend on:

- render frame rate;
- camera position;
- incidental request order;
- hash iteration order;
- cache eviction/reload;
- visibility/off-screen status.

Authoritative mutation occurs on the simulation thread unless a future accepted ADR defines deterministic concurrency/commit semantics.

## 15. Performance laws

Correctness and performance are co-design constraints.

For a non-trivial hot path:

1. define expected scale/workload;
2. avoid avoidable full-world scans, per-tick work, allocations and boxing;
3. keep world-size-independent structures bounded where possible;
4. instrument before complex optimization;
5. optimize behind unchanged semantic contracts;
6. retain scale/performance evidence where regression risk is material;
7. measure memory/work count as well as latency.

A reusable capability must not expose its internal performance representation to consumers just to optimize one current feature.

## 16. Testing laws

Use the nearest meaningful evidence:

- value/pure algorithm -> unit/property tests;
- authority/state lifecycle -> component invariant tests;
- reusable capability -> contract tests independent of any one consumer;
- replaceable algorithm -> substitution tests;
- mechanic/workflow -> focused integration tests across public capabilities;
- deterministic process -> replay/order tests;
- physical transfer -> conservation/bounds tests;
- hot/unbounded path -> scale/performance profiles;
- structural law -> architecture fitness test.

A capability test that can only be expressed through one current consumer is a warning that the capability boundary may not be independent enough.

Do not weaken tests merely because a refactor breaks them; update them only when the intended contract changes.

## 17. Architecture fitness laws

CI must progressively enforce:

- accepted Gradle topology only;
- simulation -> presentation dependency is impossible;
- semantic dependency graph is acyclic;
- foreign `internal` imports are forbidden;
- Kernel is domain-neutral;
- reusable semantic modules do not depend on mechanics/agents/presentation consumers;
- mechanics do not hide consumer-neutral reusable capabilities in their internals;
- Continuum remains independent of concrete natural domains;
- forbidden generic dumping-ground packages do not reappear;
- package/file naming rules that can be checked mechanically;
- deterministic and scale gates remain green.

Important mechanically-checkable laws must not exist only as prose.

## 18. Documentation laws

Normative documentation mirrors semantic modules and public capabilities, not feature chronology.

Canonical structure:

```text
docs/project-context.md
docs/architecture.md
docs/roadmap.md
docs/systems/**
docs/decisions/**
docs/guides/**
docs/journal/**
docs/references.md
```

Every substantive system page should state:

- semantic concept and responsibility;
- authoritative facts;
- reusable public capabilities;
- consumers and allowed dependency direction;
- exact algorithms/formulas/units;
- determinism/order;
- invariants;
- performance model;
- limitations;
- code/tests;
- primary/external sources and algorithm lineage.

## 19. Change protocol

Every production task follows:

```text
read repository laws/current docs
        ↓
identify independent semantic concepts
        ↓
identify reusable capabilities before consumer workflow
        ↓
identify authorities + invariants + dependency direction
        ↓
smallest coherent implementation checkpoint
        ↓
capability/owner/mechanic tests as applicable
        ↓
architecture checks
        ↓
scale/performance evidence where material
        ↓
reconcile normative docs
        ↓
review dependency + naming + reuse boundary diff
        ↓
green PR checkpoint
```

## 20. Stop conditions

Stop and redesign before adding more code if any of these appears:

- second authority for the same fact;
- reusable concept placed under a consumer merely because that consumer came first;
- a second consumer would require moving/extracting an existing capability;
- a mechanic becomes a warehouse for Navigation/Occupancy/Visibility/etc.;
- a lower-level capability depends on a higher-level workflow;
- new universal context/service bag/service locator;
- semantic dependency cycle;
- foreign `internal` import;
- giant `WorldCell`/`WorldFact` universal truth object;
- central switch must learn every new concrete type;
- generic dumping-ground package;
- domain split across global technical layers;
- cache/projection becomes authoritative;
- camera/render distance changes simulation truth;
- one conceptual change requires unrelated package surgery.

Architecture friction is evidence about the boundary, not an inconvenience to bypass.

## 21. Required final review questions

Before declaring any change complete, answer all three:

> Can this concept be understood/replaced without reconstructing unrelated systems?

> Can a new plausible consumer reuse its public capability without moving the concept or importing another consumer?

> If this consumer disappeared entirely, would the reusable capability still have a coherent semantic home?

If any answer is no, the boundary is not complete.

See `docs/architecture.md`, ADR-026, `docs/guides/development-workflow.md`, `docs/guides/testing.md` and `docs/guides/documentation.md` for detailed rules.

# EvoForge Architecture

Architecture is the constitution of EvoForge. It defines **where truth lives, how blocks may depend on one another, how a new idea is classified before code is written, and which structural mistakes CI must reject**.

The short version is:

> EvoForge is one authoritative `simulation` module composed from autonomous semantic owners, explicit cross-owner mechanics, a domain-neutral kernel, rebuildable projections and policy-free composition.

If a change is convenient but violates these rules, the design changes — not the rules by accident.

## Start here before any repository change

Every human or AI-assisted change begins with:

```text
AGENTS.md
   ↓
project-context.md
   ↓
architecture.md
   ↓
relevant systems/* page + accepted ADRs
   ↓
actual owner package + tests + dependencies
   ↓
only then: design/edit code
```

The repository must remain understandable without chat history.

## Repository boundary

```text
simulation/   all authoritative simulation, Genesis, world state, physics, mechanics and agents
core/         libGDX presentation, observer/debug UI and presentation adapters
lwjgl3/       desktop launcher
assets/       authored definitions and presentation resources
docs/         canonical explanation, rationale and history
```

Only `simulation`, `core` and `lwjgl3` are code/Gradle modules. Domain boundaries inside the simulation are semantic Java-package boundaries, not separate Gradle projects.

`simulation` is pure Java. It must be runnable/testable without libGDX and must never depend on camera, renderer, `core` or `lwjgl3` truth.

## Why semantic ownership is the primary axis

Technical stages such as generation, physics, storage and runtime are **secondary aspects**. If they are used as the primary directory axis, one concept is scattered:

```text
generation/liquid
physics/liquid
storage/liquid
runtime/liquid
```

A change to Liquid then requires reconstructing several unrelated technical layers.

EvoForge instead groups by the decision/fact family that changes together:

```text
world/liquid/
    public semantic surface
    internal/
        genesis/
        flow/
        storage/
        projection/
```

This follows information-hiding and package-by-component principles: a module hides changeable implementation decisions behind a small semantic surface and code that changes for the same reason stays together.

## The five allowed architectural block types

Every non-trivial production package/class must be classified as exactly one type.

### 1. OWNER

An Owner has exclusive authority over one family of mutable facts.

Examples:

```text
Terrain     -> terrain material/extent/revision facts
Liquid      -> free-liquid identity/amount/distribution
Soil        -> retained soil composition/state
Object      -> physical object identity and object-owned physical facts
Atmosphere  -> atmospheric state
```

An Owner contains its own storage, owner-local algorithms and lifecycle aspects when needed.

### 2. MECHANIC

A Mechanic owns an independently meaningful interaction between multiple owners.

Examples:

```text
Movement    -> coordinates object location/space availability/geometry/time
Hydrology   -> only if a law genuinely exchanges state among independent water domains
Erosion     -> only if a future law genuinely coordinates several owners
```

A Mechanic does not become a second owner of participating facts. It may own only its own transaction/process state.

### 3. KERNEL SERVICE

Kernel services are domain-neutral execution machinery:

```text
kernel/time
kernel/scheduling
kernel/command
kernel/lifecycle (only when a real responsibility requires it)
```

The scheduler knows **when** work runs. It does not know what Liquid, Growth or Movement means.

### 4. PROJECTION

A Projection is rebuildable derived state:

```text
TerrainSurfaceIndex
LiquidSurfaceIndex
path/navigation caches
presentation views
```

The defining equation is conceptual:

```text
P = f(A)

A = authoritative source facts
P = projection/cache/index
```

Deleting `P` may cost performance, but must not delete or alter `A`. If a projection cannot be reconstructed from authoritative sources, it has accidentally become a second owner.

### 5. COMPOSITION

Composition chooses implementations, connects public contracts and controls lifecycle ordering.

It may know many blocks. It owns no physical formulas, thresholds, agent policy or mutable domain truth.

## Forbidden sixth category

There is no architectural type for “shared convenient code”. Do not create generic dumping grounds:

```text
util/
common/
misc/
helpers/
shared/
framework/
managers/
services/
base/
```

A reusable primitive is extracted only after multiple real consumers prove one stable semantic concept.

## One authoritative owner per mutable fact

The central invariant is:

```text
mutable fact -> exactly one authoritative owner
```

For a fact `F`:

```text
owners(F) = 1
```

Derived caches/views may represent `F`, but they are not owners.

Bad:

```text
LiquidSystem.amount
HydrologyState.amount
WorldCell.water
RendererWaterState
```

all pretending to be truth.

Correct:

```text
Liquid authoritative amount
       ↓ reads
Hydrology / diagnostics / presentation
       ↓ optional derived cache
rebuildable projection
```

Cross-owner mutation uses an explicit semantic mutation capability owned by the fact being changed.

## Deciding Owner versus Mechanic

Use this rule before creating a new package.

### Owner-local behavior

If an algorithm reads B but mutates only authoritative state owned by A, it normally belongs to A:

```text
A behavior: read(B) + mutate(A) -> A/internal/...
```

Example:

```text
Liquid flow
  reads Terrain geometry
  mutates Liquid
  -> world/liquid/internal/flow
```

### Cross-owner mechanic

If a law is independently meaningful specifically because it coordinates several autonomous owners, it may be a mechanic:

```text
Mechanic M
   reads/mutates through public capabilities of A, B, ...
   owns only M-specific process state
```

A mechanic never stores shadow copies of A/B truth.

## Package form

The target form for a semantic component is:

```text
<component>/
    <small public semantic surface>
    internal/
        <owner-local implementation details>
```

The root package is the intentional public surface. Other components may not import another component's `..internal..` package.

Do not mechanically create empty `internal/genesis/storage/...` directories. A package exists only when a real responsibility exists.

## Target simulation map

The exact tree grows only with real code, but the architectural map is:

```text
io.github.evoforge.simulation
├── Simulation / composition entry points
├── kernel/
│   ├── time/
│   ├── scheduling/
│   └── command/
├── definition/                 neutral authored-definition infrastructure only
├── genesis/                    global Genesis orchestration only
├── world/
│   ├── continuum/              neutral large-world addressing/materialization
│   ├── space/                  neutral coordinates/bounds when required
│   ├── geometry/               objective physical geometry
│   ├── geology/
│   ├── terrain/
│   ├── liquid/
│   ├── soil/
│   ├── atmosphere/
│   └── object/
├── mechanics/
│   ├── movement/
│   └── <real cross-owner law>/
├── agents/
├── persistence/                only when persistent-state ownership is real
└── diagnostics/                observer-only diagnostics when shared scope is real
```

This is a classification map, **not permission to create every directory up front**.

## Genesis is lifecycle, not a competing owner

Genesis creates initial authoritative facts and hands them to the same owners that govern those facts at runtime:

```text
semantic intent
      ↓
global Genesis composition
      ↓
owner-local genesis algorithm
      ↓
authoritative owner state
============================== runtime boundary
ordinary owner/runtime processes
```

Root `genesis` may coordinate dependencies/order and world-level inputs. Domain-specific terrain/liquid/geology algorithms stay with their owner.

There is no global `generation/<domain>` hierarchy and no second “generated world” truth after startup.

## Physics is not a repository layer

“Physical” describes a law, not automatically an architectural owner.

```text
Liquid-only physics       -> Liquid
Atmosphere-only physics   -> Atmosphere
cross-owner physical law  -> named Mechanic
shared numeric primitive  -> extract only after proven reuse
```

Do not create a global `physics/` package that re-splits every owner.

## Objects and agents are different concepts

A physical Object answers questions such as:

```text
does this object exist?
where/or how is the physical object represented?
```

An Agent answers:

```text
what does an autonomous entity perceive/remember/need/choose?
```

Agent cognition may read public world/mechanic capabilities. World owners must not depend on agent cognition.

## Objective geometry versus traversal meaning

Geometry describes objective physical shape. It must not encode actor-specific movement policy.

The intended causal chain is:

```text
Geometry
  objective occupied shape/boundary
      ↓
Movement structural interpretation
      ↓
transition/cost constraints
      ↓
dynamic availability/reservation
      ↓
authoritative concrete movement
```

Pathfinding is advisory planning inside the Movement mechanic. It proposes a route from current public facts; Movement revalidates before authoritative transition/commit.

## Dependency laws

Dependencies are explicit, typed and acyclic.

### Public contracts only

```text
A -> B public semantic capability    allowed
A -> B.internal.*                    forbidden
```

### No service locator

Forbidden patterns include universal mutable contexts containing arbitrary systems:

```text
WorldContext
SimulationContext
PhysicsContext
GenerationContext
```

A constructor receives the smallest capabilities it actually needs.

### Cycles are architectural failures

```text
A -> B -> A
```

means the owners/contracts are wrong until proven otherwise. Repair by:

1. moving behavior to the correct owner;
2. extracting/inverting the smallest semantic capability; or
3. identifying a missing cross-owner mechanic.

Do not “solve” a cycle with a generic event bus or service locator.

### Events

Direct typed calls are preferred for causal deterministic simulation. Events may serve diagnostics, presentation notification or genuinely reactive non-authoritative concerns. A global event bus is not the simulation backbone.

## Composition root

The composition root is intentionally asymmetric:

```text
many public dependencies -> Composition -> no domain policy
```

It may select implementations and order initialization. It must not contain formulas, material-specific switches, agent utility policy, terrain thresholds or algorithm details.

If feature-specific methods make the root a domain façade, move that behavior back to its owner/mechanic.

## Definitions

Definitions are immutable authored semantic meaning, not runtime objects.

Root `definition` contains only genuinely neutral infrastructure such as IDs/registries/loaders/normalized values. Domain-specific definition types/compilers live with the semantic owner/mechanic consuming them.

Do not turn algorithm tuning constants into content Definitions merely to make them configurable.

## Commands and results

External commands express intent at the semantic block that owns the operation.

```text
Terrain command  -> Terrain
Movement command -> Movement mechanic
```

Only generic dispatch machinery belongs to `kernel/command`.

Operation results live with their operation. There is no global domain-error bucket that accumulates unrelated result codes indefinitely.

Expected domain rejection is structured data. Broken invariants/programming/configuration errors fail loudly.

## Determinism

For fixed authoritative inputs and a compatible model/algorithm revision:

```text
Result = f(authoritative inputs, revision)
```

and must not depend on incidental factors such as:

- render frame cadence;
- camera/observer location;
- hash iteration order;
- request order where order has no semantic meaning;
- cache eviction/reload;
- whether a region is visible.

Addressable pseudo-random generation uses stable inputs/coordinates rather than incidental call order.

## Simulation-thread authority

Authoritative mutation currently occurs on the simulation thread. Background/concurrent computation may not silently mutate live truth.

Concurrency requires an explicit future ownership design/ADR proving deterministic commit semantics.

## Continuum boundary

Continuum is neutral large-world infrastructure. It owns addressing/materialization/query/cache mechanics, not natural semantic truth such as “this is a mountain” or “this cell contains water”.

Technical pages/tiles/chunks/caches are representation. They are never natural geography and never authoritative simply because data is stored there.

Camera zoom/pan may change what is requested/materialized for observation. It may not change the authoritative physical rules.

## Performance architecture

Performance is a permanent contract for a potentially vast persistent world.

The required optimization loop is:

```text
semantic invariant
      ↓
representative workload + metric
      ↓
measure actual hot path
      ↓
optimize hidden representation/algorithm
      ↓
prove same semantics
      ↓
retain regression evidence when material
```

### General rules

- avoid whole-world scans when work can be bounded/local/active-set based;
- avoid mandatory per-object/per-cell work every tick when a deterministic sleeping/analytical process is equivalent;
- avoid avoidable allocations/boxing/temporary collections on proven hot paths;
- bound caches explicitly by entries/bytes/work where world size is unbounded;
- track memory/work-count as well as wall-clock time;
- use sparse, packed, ECS/data-oriented or paged representations **inside** an owner when measurement justifies them;
- never expose an optimization representation as a new semantic truth.

A performance optimization is invalid if it changes the authoritative model merely because the observer is distant.

## Replaceability

Replaceability exists at two levels.

### Algorithm replacement

```text
semantic contract
   ├── implementation A
   └── implementation B
```

A consumer does not branch on concrete implementation classes.

### Subsystem implementation replacement

An owner's storage/solver/cache/data layout may be rewritten without changing unrelated consumers if its public semantics remain compatible.

Do not confuse replaceability with maximum interface count. Internal helpers remain concrete unless independent variation/reuse is real.

## Testing architecture

A changed architectural/semantic contract receives the nearest evidence:

```text
pure rule/algorithm         -> unit/property test
owner lifecycle/state       -> owner/component test
replaceable seam            -> substitution test
cross-owner composition     -> focused integration test
deterministic process       -> replay/order/seam test
physical transfer           -> conservation/bounds test
large/hot path              -> scale/performance profile
structural law              -> architecture fitness test
visual/aesthetic property   -> manual visual acceptance when necessary
```

Architecture rules that can be mechanically checked must fail CI when violated. See the Testing Guide.

## Naming is architecture

Names reveal ownership and semantics.

Use domain nouns and precise algorithm/action nouns. Avoid vague names such as `Manager`, `Utils`, `Common`, `Data`, `Stuff`, generic `Service` and generic `Processor`.

A package must have one-sentence purpose. A file has one primary type/responsibility. Public names describe stable semantics rather than current storage/algorithm details unless those details are intentionally part of the contract.

The complete naming/file-placement rules are normative in root `AGENTS.md` and the Development Workflow.

## Documentation architecture

Documentation mirrors code ownership:

```text
project-context.md  current recovery baseline
architecture.md     only global laws
roadmap.md          current stage/next blocked work
systems/**          exact current subsystem semantics
                       + formulas/units/order/invariants/performance
                       + interactions/code/tests/sources
decisions/**        durable accepted/superseded rationale
guides/**           contributor procedures
journal/**          explicitly non-normative historical record
references.md       shared external sources/algorithm lineage
```

A system change is incomplete when current normative docs contradict code/tests or when the causal model still requires chat history to explain.

## Architecture fitness checks

CI must progressively enforce:

- accepted Gradle topology only;
- simulation cannot depend on presentation/libGDX;
- semantic components have no dependency cycles;
- foreign `internal` imports are forbidden;
- kernel is domain-neutral;
- owners do not depend on agents/mechanics/presentation;
- Continuum does not depend on concrete natural domains;
- forbidden generic root buckets do not reappear;
- deterministic/scale gates remain green.

A prose-only critical architecture rule is technical debt when it can be made executable.

## Change-amplification test

A good architecture minimizes three forms of complexity:

1. **change amplification** — one conceptual change forces many unrelated edits;
2. **cognitive load** — a contributor must understand unrelated systems to modify one owner;
3. **unknown unknowns** — the contributor cannot discover which other places must change.

Before accepting a change, ask:

> If this concept is replaced, removed or optimized, are the required edits localized to its semantic block and explicit direct consumers?

Semantic dependencies may legitimately break direct consumers. Unrelated systems must not break.

## Stop conditions

Stop feature development and repair the architecture when any of these appears:

- two owners for one mutable fact;
- a new universal context/service bag;
- a semantic dependency cycle;
- another block's internals must be imported;
- one concept is scattered across global technical layers;
- a giant universal `WorldCell`/`WorldFact` emerges;
- generic orchestration branches on every new concrete content/algorithm type;
- a projection/cache is becoming authoritative;
- camera/render state affects simulation truth;
- adding a feature requires broad unrelated edits because ownership cannot be located.

## Architectural lineage and external sources

The architecture intentionally combines ideas rather than adopting one framework wholesale:

- David Parnas, *On the Criteria To Be Used in Decomposing Systems into Modules* — information hiding / changeable design decisions: https://doi.org/10.1145/361598.361623
- Simon Brown, package-by-component / modular monolith — cohesive components with small public surfaces: https://simonbrown.je/modular-monolith/
- Martin Fowler, Bounded Context — explicit model boundaries in large domains: https://martinfowler.com/bliki/BoundedContext.html
- Alistair Cockburn, Hexagonal Architecture — purpose-based ports at real boundaries: https://alistair.cockburn.us/hexagonal-architecture/
- John Ousterhout, deep modules / complexity — small interface, hidden complexity, change amplification/cognitive load: https://web.stanford.edu/~ouster/cgi-bin/cs190-winter18/lecture.php?topic=complexity
- ArchUnit — executable package/dependency architecture rules: https://www.archunit.org/userguide/html/000_Index.html

These are conceptual/architectural sources. EvoForge does not claim to implement DDD, Hexagonal Architecture, ECS or any framework wholesale.

See [ADR-025: Owner-first modular simulation architecture](decisions/025-owner-first-modular-simulation.md) for the durable decision and supersession of the old horizontal module split.

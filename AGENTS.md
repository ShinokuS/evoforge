# EvoForge Repository Laws

This file is the mandatory entry point for every human or AI-assisted repository change. Read it **before** inspecting a ticket, designing a class, editing code, changing CI, or updating documentation.

The laws below override convenience, historical package names and local implementation habits. If a proposed change conflicts with them, change the design before changing the repository.

## 0. Required context before any change

Before editing anything:

1. read this file;
2. read `docs/project-context.md`;
3. read `docs/architecture.md`;
4. read the relevant current page under `docs/systems/`;
5. read relevant accepted ADRs under `docs/decisions/`;
6. inspect the actual current owner package, its tests and its public dependencies;
7. state the intended owner, architectural block type, invariants, performance risk and verification plan.

Never reconstruct current architecture from chat history, old branches or historical Journal notes when current code/tests/normative docs are available.

## 1. Repository boundary

The production repository has three code modules only unless an accepted ADR explicitly changes this:

```text
simulation/   all authoritative simulation, Genesis, world state, physics, mechanics and agents
core/         libGDX presentation, observer/debug UI and presentation adapters
lwjgl3/       desktop launcher
```

`simulation` is pure Java and must not depend on `core`, `lwjgl3`, libGDX presentation classes, camera state or rendering state.

Do not create new Gradle modules to represent Terrain, Liquid, World, Generation, Physics, Agents or other simulation concepts. Their isolation is semantic/package-level and enforced by architecture tests.

## 2. Every production block has exactly one architectural type

Before creating a package or non-trivial class, classify it as exactly one of:

1. **OWNER** — owns one family of authoritative mutable facts.
2. **MECHANIC** — owns a cross-owner law or interaction, but never duplicates authoritative owner state.
3. **KERNEL SERVICE** — domain-neutral execution infrastructure such as time/scheduling/lifecycle/command dispatch.
4. **PROJECTION** — rebuildable cache/index/view derived from authoritative facts; deleting it cannot delete world truth.
5. **COMPOSITION** — wires implementations and lifecycle; contains no domain mathematics/policy.

If the code does not fit one type cleanly, the responsibility is not yet understood. Do not create a generic sixth category.

## 3. Primary package axis: semantic ownership

The top-level question is **who owns this fact/behavior?**, never **what technical kind of code is this?**

Correct:

```text
world/liquid/
  ... public semantic contracts ...
  internal/
    genesis/
    flow/
    storage/
```

Forbidden architecture:

```text
generation/liquid/
physics/liquid/
storage/liquid/
services/liquid/
```

Generation, runtime, physics, storage, algorithms and diagnostics are secondary aspects inside the owning semantic block when they are needed.

## 4. One authoritative owner per mutable fact

For every mutable fact, name exactly one owner.

No second authoritative copy is allowed in a coordinator, cache, renderer, world cell, runtime facade or convenience object.

Derived data must satisfy:

```text
authoritative fact -> rebuildable projection/cache/index
```

A projection may be discarded and rebuilt without changing the simulated world.

## 5. Owner versus mechanic rule

Use this decision rule:

- if behavior reads B but mutates only authoritative state of A, it normally belongs to owner A;
- if a law is independently meaningful and coordinates multiple owners, it may be a `mechanics/<name>` block;
- a mechanic may own its own process/transaction state, but never copies authoritative facts owned by participants.

Examples:

- Liquid flow reads Terrain geometry and mutates Liquid -> Liquid owner.
- Movement coordinates Object position, geometry, availability and time -> Movement mechanic.
- A future erosion law that explicitly exchanges state between terrain/material/liquid owners -> Erosion mechanic.

## 6. Public surface and internals

A semantic component exposes the smallest stable public surface needed by real consumers. Implementation detail stays under `internal` or package-private scope.

Other components must never import another component's `..internal..` package.

Do not create ceremonial `Service`, `Manager`, `Provider`, `Factory`, `Strategy`, `Impl` layers unless the name describes a real independently meaningful responsibility.

Prefer a deep module: small semantic API, substantial hidden implementation.

## 7. Dependency laws

Dependencies must be explicit, typed and acyclic.

Forbidden:

- universal `WorldContext`, `SimulationContext`, `PhysicsContext`, `GenerationContext` or similar service bags;
- service locators/global registries used to discover arbitrary systems;
- generic event bus as the causal simulation backbone;
- owner -> mechanic/agent/presentation dependencies;
- any import of another component's internal implementation;
- circular semantic dependencies.

If A and B need each other's internals, the boundary is wrong. Introduce/invert the smallest semantic contract or identify the missing mechanic/owner.

Composition roots are allowed to know many public contracts. They are not allowed to contain domain policy.

## 8. Naming laws

Names communicate semantic responsibility, not implementation fashion.

### Packages

Use lower-case domain nouns or precise mechanism nouns: `liquid`, `terrain`, `movement`, `scheduling`.

Do not create generic dumping grounds named:

```text
util common misc helpers shared framework managers services base
```

A package must be explainable in one sentence and have one owner/type.

### Types

- authoritative IDs: `<Concept>Id`;
- immutable authored semantics: `<Concept>Definition` / `<Concept>Definitions` only when they truly are authored definitions;
- read-only semantic capability: `<Concept>View` or a precise capability noun; use `Lookup` only when coordinate/key lookup is the actual contract;
- explicit mutation capability: `<Concept>Mutations` or a verb-specific capability;
- runtime owner: use the domain noun or `<Concept>System` only when it truly owns that domain's mutable runtime state/processes;
- algorithm: name the mathematical/domain job (`AStarPathfinder`, `LiquidTransferPlanner`), not `Helper`;
- result: `<Operation>Result` / `<Operation>Attempt` local to the owning operation;
- projection/index/cache: name it explicitly `<Concept>Index`, `<Concept>Cache`, `<Concept>Projection`;
- composition: `<Scope>Assembly` only at a real wiring boundary; prefer one composition root rather than feature-specific assembly proliferation.

A file name must match its primary type and one responsibility. Avoid `Utils`, `Common`, `Manager`, `Data`, `Stuff`, `Processor` and vague `Service` names.

## 9. File creation decision table

Before adding a file, choose its home by this order:

```text
Does it own a mutable fact?                  -> owning domain
Does it implement behavior of one owner?    -> that owner's internal area
Does it coordinate independent owners?      -> mechanics/<law>
Is it domain-neutral execution machinery?   -> kernel/<responsibility>
Is it rebuildable derived state?            -> owning consumer/domain projection area
Does it only wire existing blocks?          -> composition root/area
Is it authored semantic meaning?            -> generic definition infrastructure or owner-local definition
Otherwise                                   -> do not create it until responsibility is clear
```

Never choose a package merely because a similar class already happens to be there.

## 10. Genesis and runtime

Genesis creates initial authoritative facts and then hands them to ordinary owners.

Root Genesis code is composition/coordination only. Domain-specific generation algorithms live with the domain they create.

Genesis does not remain a second runtime owner after startup.

Do not introduce a global `generation/<domain>` hierarchy.

## 11. Physics and algorithms

Do not create a global `physics` package merely because code is physical.

- owner-local physical law -> owner;
- cross-owner physical law -> mechanic;
- generic numerical primitive -> extract only after multiple real consumers prove a stable common concept.

Replace independently meaningful algorithms behind narrow semantic seams. Do not turn every private helper into an interface.

## 12. Determinism and simulation authority

For the same authoritative inputs and compatible algorithm revision, deterministic systems must reproduce the same authoritative result independent of:

- render frame rate;
- camera/observer position;
- request order where order is not semantic;
- cache eviction/reload;
- incidental collection iteration order.

Authoritative mutation occurs on the simulation thread unless a future accepted ADR explicitly redesigns ownership/concurrency.

Optimization may reduce work. It may not silently substitute different physical rules because a region is off-screen or distant.

## 13. Performance laws

Correctness and performance are co-design constraints, not cleanup stages.

For every non-trivial hot-path candidate:

1. define expected scale/workload;
2. avoid avoidable full-world scans, per-tick work, allocations, boxing and temporary collections;
3. keep world-size-independent structures bounded where the model permits;
4. instrument representative work before complex optimization;
5. optimize behind the same semantic contract;
6. retain a benchmark/scale profile when regression risk is material;
7. verify memory as well as latency/work count.

ECS/data-oriented storage, packed representations, paging and caches are allowed inside owners as replaceable implementation details. They do not define repository architecture.

Never claim a performance improvement without representative evidence.

## 14. Testing laws

Every changed contract needs the nearest meaningful automated evidence.

Required categories as applicable:

- unit tests for pure algorithms/value rules;
- owner tests for lifecycle/state invariants;
- deterministic/replay/order tests;
- conservation/bounds tests for physical flows;
- substitution tests for replaceable seams;
- integration tests only at real cross-owner composition boundaries;
- architecture tests for structural laws;
- scale/performance profiles for hot or unbounded-looking paths;
- manual visual acceptance only for properties automation cannot establish.

Do not weaken a test merely because new code fails it. Change tests when the intended contract deliberately changes and update normative documentation in the same change.

A bug fix must add the smallest regression test that would have caught the bug when practical.

## 15. Architecture fitness laws

Executable architecture tests must protect at least:

- only the accepted Gradle module topology;
- no simulation -> presentation/libGDX dependency;
- no semantic package cycles;
- no foreign `internal` imports;
- kernel independence from domains;
- owner independence from agents/mechanics/presentation;
- Continuum independence from concrete natural domains;
- forbidden generic root packages do not reappear;
- package/file naming and ownership rules that can be checked mechanically.

A structural rule that is important and mechanically checkable should not exist only as prose.

## 16. Documentation is part of the change

Normative documentation must describe current truth, not intentions or chat history.

Canonical ownership:

```text
docs/project-context.md      fastest current recovery path
docs/architecture.md         repository-wide laws only
docs/roadmap.md              current accepted stage and next blocked work
docs/systems/**              exact current subsystem semantics/formulas/interactions
docs/decisions/**            durable rationale/ADRs
docs/guides/**               contributor procedures
docs/journal/**              explicitly non-normative history/audits/acceptance
docs/references.md           reusable external sources and algorithm lineage
```

Every system page must identify ownership, public contracts, exact algorithm/formulas/units, determinism/order, invariants, interactions, performance model, limitations, code/tests and sources.

External algorithms/research must be linked to primary/original sources where possible and labeled as direct model, algorithm lineage or conceptual influence. Never cite a paper to imply fidelity the implementation does not have.

Historical Journal records are not silently rewritten into current truth; add forward links to current normative pages instead.

## 17. Change protocol

Every production task follows this sequence:

```text
read laws/current context
        ↓
identify owner + block type
        ↓
state invariant + performance risk + evidence
        ↓
smallest coherent implementation checkpoint
        ↓
focused tests
        ↓
architecture checks
        ↓
full affected tests / scale evidence
        ↓
reconcile normative docs
        ↓
review dependency/package/naming diff
        ↓
green PR checkpoint
```

Do not stack new scope on unexplained red CI.

Refactors that claim behavior preservation separate mechanical moves from semantic changes whenever possible.

## 18. Stop conditions

Stop and redesign before adding code if any of these appears:

- a second owner for the same fact;
- a new global context/service bag;
- a cross-package cycle;
- a need to import another block's internals;
- a giant `WorldCell`/`WorldFact` universal truth object;
- a central switch that must learn every new concrete type/algorithm;
- a generic package with no semantic owner;
- a domain split across top-level technical layers;
- a cache/projection becoming authoritative;
- camera/render distance changing simulation truth;
- a new feature requiring edits across many unrelated packages without a real semantic reason.

Architecture friction is a signal, not an inconvenience to work around.

## 19. Required final review question

Before declaring any repository action complete, answer:

> If a future developer wants to replace, remove, optimize or understand this one concept, can they find its owner quickly and change it without reconstructing unrelated systems?

If the answer is no, the change is not architecturally complete.

See `docs/architecture.md`, `docs/guides/development-workflow.md`, `docs/guides/testing.md`, `docs/guides/documentation.md` and ADR-025 for the canonical detailed rules and rationale.

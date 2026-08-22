# ADR-025: Owner-first modular simulation architecture

- Status: Accepted
- Scope: Repository-wide simulation architecture, package ownership and dependency direction
- Decision: EvoForge keeps all authoritative simulation code in one `:simulation` module and decomposes it first by semantic owner/axis of change; owner-local Genesis/physics/storage/runtime details stay with that owner, while cross-owner laws are explicit mechanics and infrastructure remains domain-neutral.

## Context

ADR-023 previously split `Foundation`, `World`, `Generation` and `Simulation` into separate Gradle modules. That made technical stages explicit, but it also allowed one semantic concept to be scattered across competing places:

```text
generation/liquid
world/liquid
physics/liquid
runtime/liquid
```

A mature Liquid domain naturally needs authored meaning, initial-world creation, authoritative runtime state, storage, flow/transport physics and projections. Those concerns change around **Liquid**, not around four independent repository layers.

The architecture reset also found that several apparently separate old packages formed circular dependencies. Folder names therefore communicated more independence than the code actually had.

EvoForge needs boundaries that remain useful as world generation, hydrology, agents, storage and physical simulation become much larger.

## Decision

### One authoritative simulation module

The code-module topology is:

```text
:simulation
:core
:lwjgl3
```

`simulation` contains all authoritative world state, Genesis, physics, mechanics and agents. Domain isolation inside it uses semantic package boundaries, Java visibility and executable architecture tests rather than a Gradle project per concept.

### Semantic owner is the primary axis

Place code first by **who owns the fact/behavior and changes for the same semantic reason**.

Secondary aspects live inside that owner when they are owner-specific:

```text
world/liquid/
    <small public semantic surface>
    internal/
        genesis/
        flow/
        storage/
        projection/
```

Do not recreate global `generation/<domain>`, `physics/<domain>` or `storage/<domain>` trees.

### Exactly five architectural block types

Every non-trivial production block is exactly one of:

1. **OWNER** — exclusive authority over a family of mutable facts.
2. **MECHANIC** — independently meaningful cross-owner interaction without duplicate owner truth.
3. **KERNEL SERVICE** — domain-neutral execution infrastructure.
4. **PROJECTION** — rebuildable derived cache/index/view.
5. **COMPOSITION** — wiring/lifecycle selection without domain policy.

If a block does not fit one type cleanly, its responsibility is not yet understood.

### One owner per mutable fact

For each mutable fact `F`:

```text
owners(F) = 1
```

Caches, indexes, diagnostics and presentation may derive the fact only when they remain rebuildable and cannot silently become a second truth.

### Owner versus mechanic

If behavior reads B but changes only authoritative state of A, it normally belongs to A.

If a law exists specifically to coordinate several independent owners, it may be a Mechanic. The Mechanic owns only its own process/transaction state and changes participant facts through participant-owned public mutation capabilities.

### Public surface and internals

A semantic component exposes the smallest stable public contract required by real consumers. Implementation detail stays package-private or below `internal`.

Another component may not import another component's `..internal..` package.

Do not mechanically add `api/impl/service/repository/factory` layers to every component.

### Dependencies are typed and acyclic

Cross-component consumers receive the narrowest semantic capabilities they need.

Universal contexts/service bags, global service locators and a generic event bus are forbidden substitutes for explicit dependency design.

A semantic cycle is treated as an ownership/contract defect and repaired rather than normalized.

### Kernel stays neutral

Clock, scheduling and command/lifecycle machinery answer domain-neutral questions such as **when** work runs. They do not know why Liquid flows, why an Agent acts or whether Terrain exists.

### Genesis is lifecycle, not a second owner

Global Genesis coordinates initial construction. Domain-specific generation belongs to the domain being created and hands facts to the ordinary runtime owner.

Genesis does not remain an alternate world authority after startup.

### Physics is owner-local unless the law is genuinely cross-owner

There is no global Physics layer merely because behavior is physical.

Owner-local physics remains with its owner. Cross-owner physical laws become named Mechanics. Shared numerical infrastructure is extracted only after multiple real consumers prove a stable common concept.

### Composition is broad in dependencies, empty of domain policy

A composition root may choose implementations and lifecycle order. It may not own feature formulas, thresholds, material-specific branches or mutable domain truth.

### Replaceability means information hiding, not interface proliferation

Independently variable algorithms/processes receive semantic seams. Private helpers remain concrete until independent variation/reuse is real.

A compatible replacement must not require unrelated consumers to learn the implementation.

### Performance representation stays internal

Sparse maps, ECS/data-oriented layouts, packed arrays, pages and caches may be used inside owners/mechanics when measurement justifies them. They do not define repository architecture and may not become second truths.

### Structural laws become executable

Architecture tests/CI must progressively reject:

- semantic dependency cycles;
- foreign `internal` access;
- domain knowledge in Kernel;
- Owner dependencies on Agents/Mechanics/Presentation;
- Continuum dependencies on concrete natural domains;
- simulation/presentation dependency inversion;
- forbidden generic dumping-ground packages;
- unsupported Gradle-module proliferation.

## Why

This structure optimizes for the long-lived axis of change. A contributor changing Liquid, Terrain, Movement or Agents begins in one semantic place. Storage, solver, paging and lifecycle implementation can evolve behind that boundary.

It also makes duplicate ownership easier to detect: two packages claiming the same mutable fact are an architectural failure rather than “different layers”.

The single `:simulation` module removes artificial coarse build boundaries; package visibility and architecture tests provide finer enforcement at the boundaries that actually matter.

## Consequences

Benefits:

- concepts are discoverable by semantic name;
- code that changes together stays together;
- owner implementations can be replaced with lower blast radius;
- Generation/Physics/Storage cannot become parallel authorities by default;
- package cycles/internal leaks can fail CI;
- performance/data-layout refactors stay implementation detail;
- new domains need no central special case or Gradle module.

Costs:

- public capabilities must be designed deliberately;
- composition roots have explicit dependency lists;
- old umbrella packages/facades must be dismantled rather than cosmetically renamed;
- package-level architecture tests are mandatory;
- ownership disputes must be resolved before implementation.

## Alternatives considered

**Separate Foundation / World / Generation / Simulation modules** — rejected as the semantic architecture because they split one owner by technical phase. A truly reusable/deployable neutral library could become a module later only through a separate accepted decision.

**Global Layered/Clean/Onion package tree** — rejected as the primary folder axis. Dependency-direction ideas remain useful locally, but global technical layers scatter a single EvoForge concept.

**Universal ECS repository architecture** — rejected. ECS/data-oriented storage remains available as an internal hot-path implementation technique.

**Full plugin/microkernel framework** — rejected. EvoForge keeps a small neutral kernel and replaceable components without a universal `Plugin`, service registry or context bag.

**Global event-driven architecture** — rejected for causal simulation. Direct typed dependencies keep deterministic cause/effect auditable; events remain suitable for observers/diagnostics when appropriate.

## Architectural sources and influence

- David Parnas, *On the Criteria To Be Used in Decomposing Systems into Modules* — information hiding: https://doi.org/10.1145/361598.361623
- Simon Brown, modular monolith/package-by-component — cohesive components with small public surfaces: https://simonbrown.je/modular-monolith/
- Martin Fowler, Bounded Context — explicit model boundaries: https://martinfowler.com/bliki/BoundedContext.html
- Alistair Cockburn, Hexagonal Architecture — semantic ports at true boundaries: https://alistair.cockburn.us/hexagonal-architecture/
- John Ousterhout, software complexity/deep modules — change amplification and cognitive load: https://web.stanford.edu/~ouster/cgi-bin/cs190-winter18/lecture.php?topic=complexity
- ArchUnit — executable Java architecture constraints: https://www.archunit.org/userguide/html/000_Index.html

These are architectural influences, not claims that EvoForge implements any one framework wholesale.

## Current implementation

Draft PR #132 performs the reset. Its first checkpoint folds the old `:foundation`, `:world` and `:generation` projects into `:simulation` without changing behavior. Later checkpoints reclassify packages by semantic ownership, repair cycles and add architecture fitness tests.

Until the PR is accepted, some package locations on the reset branch are transitional. The PR may not leave Draft state until code, tests and normative documentation agree with this ADR.

The repository-root `AGENTS.md` is the mandatory operational entry point for these laws.

## Related documentation

- [Architecture](../architecture.md)
- [Project Context](../project-context.md)
- [Development Workflow](../guides/development-workflow.md)
- [Testing Strategy](../guides/testing.md)
- [Documentation Guide](../guides/documentation.md)
- [ADR-023](023-strict-modular-architecture.md) — superseded horizontal module topology; its useful cohesion/replaceability principles are incorporated here.

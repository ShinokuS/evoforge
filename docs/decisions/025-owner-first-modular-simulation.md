# ADR-025: Owner-first modular simulation architecture

- Status: Accepted
- Scope: Repository-wide simulation architecture, package ownership and dependency direction
- Decision: EvoForge keeps all authoritative simulation code in one `:simulation` module and decomposes it first by semantic owner/axis of change; owner-local Genesis/physics/storage/runtime details stay with that owner, while cross-owner laws are explicit mechanics and infrastructure remains domain-neutral.

## Context

EvoForge previously strengthened compile-time separation by extracting `:foundation`, `:world` and `:generation` from `:simulation` and defining a horizontal dependency pyramid in ADR-023.

That structure made individual technical stages explicit, but it created a more serious long-term problem: one semantic concept could be physically scattered across several competing architectural locations.

For example, a mature Liquid domain naturally needs:

- authored meaning/properties;
- initial-world creation;
- authoritative runtime state;
- storage representation;
- flow/transport physics;
- projections/indexes;
- diagnostics and public read/mutation capabilities.

If Generation, World/state and Physics/Simulation are primary top-level modules/layers, Liquid becomes split by technical phase:

```text
generation/liquid
world/liquid
physics/liquid
runtime/liquid
```

The repository then answers “what technical stage is this?” before it answers the more stable question “who owns this concept and its changing decisions?”. A future change to Liquid requires knowledge of multiple horizontal areas and can create duplicate authorities.

The architecture reset also audited the existing package graph and found that apparently separate old packages such as Landscape, Mechanics, Navigation, Object, Pathfinding, Scale and Spatial formed circular dependencies. The folder tree therefore communicated more independence than the code actually had.

The project needs architecture that remains usable as world generation, hydrology, agents, large-world storage and physical simulation become much more complex without repeatedly reorganizing the whole repository.

## Decision

### 1. One authoritative simulation module

The production code-module topology is:

```text
:simulation
:core
:lwjgl3
```

`simulation` contains all authoritative simulation/world-generation behavior and state. Domain isolation inside it is expressed by Java package boundaries, visibility and executable architecture tests rather than one Gradle project per semantic domain.

### 2. Semantic owner is the primary decomposition axis

Code is placed first by **who owns the fact/behavior and changes for the same semantic reason**.

Secondary implementation aspects such as Genesis, runtime, storage, physics and algorithms live inside the owner when they are owner-specific.

Example target:

```text
world/liquid/
    <small public semantic API>
    internal/
        genesis/
        flow/
        storage/
        projection/
```

The project must not recreate global horizontal trees such as `generation/<domain>`, `physics/<domain>` or `storage/<domain>`.

### 3. Exactly five architectural block types

Every non-trivial production block is one of:

1. **OWNER** — exclusive authority over a family of mutable facts.
2. **MECHANIC** — independently meaningful cross-owner interaction that does not duplicate owner truth.
3. **KERNEL SERVICE** — domain-neutral execution infrastructure.
4. **PROJECTION** — rebuildable derived cache/index/view.
5. **COMPOSITION** — wiring/lifecycle selection without domain policy.

A block that does not fit one type cleanly is not yet architecturally understood.

### 4. One authoritative owner per mutable fact

For each mutable fact `F`:

```text
owners(F) = 1
```

Caches, indexes, diagnostics and presentation may derive equivalent information only when it can be rebuilt from the authority and cannot silently become a second truth.

### 5. Owner versus mechanic classification

If behavior reads another domain B but changes only authoritative state of A, it normally belongs to A.

If a law exists specifically to coordinate multiple independent owners and is meaningful independently of any one owner, it may be a Mechanic.

A Mechanic owns only its own process/transaction state; it mutates participant facts through participant-owned public mutation capabilities.

### 6. Package roots are semantic public surfaces

A semantic component exposes the smallest stable public contract required by real consumers. Internal implementation details remain package-private or below `internal`.

Another component may not import another component's `..internal..` code.

The project deliberately avoids automatically adding `api/impl/service/repository/factory` layers to every component.

### 7. Dependencies are typed and acyclic

Cross-component consumers receive the narrowest semantic capabilities they need.

Universal contexts/service bags, global service locators and a generic event bus are forbidden as substitutes for deciding dependencies.

A package cycle is treated as evidence of an ownership/contract problem and must be repaired rather than normalized.

### 8. Kernel stays neutral

Clock, scheduling and other execution machinery answer domain-neutral questions such as **when** work runs.

They do not know why Liquid flows, why an Agent chooses an action or whether Terrain exists.

### 9. Genesis is lifecycle, not a second owner

Global Genesis coordinates initial construction. Domain-specific generation belongs to the domain being created and hands its initial facts to the ordinary authoritative owner.

Genesis does not remain an alternate world/state authority after runtime begins.

### 10. Physics is owner-local unless the law is genuinely cross-owner

There is no global Physics layer/package merely because behavior is physical.

Owner-local physics remains with its owner. Cross-owner physical laws become named Mechanics. Shared numerical infrastructure is extracted only after multiple real consumers prove a stable concept.

### 11. Composition may be broad in dependencies but empty of domain policy

A composition root may know which implementations exist and in what lifecycle order they connect. It may not own feature formulas, thresholds, material-specific branches or authoritative domain state.

### 12. Replaceability means information hiding, not interface proliferation

Independent algorithms/processes receive explicit semantic seams when they can vary independently.

Private implementation helpers remain concrete until real independent variation/reuse exists.

A compatible replacement must not require unrelated consumers to learn the concrete implementation.

### 13. Performance representations remain internal

Sparse maps, ECS/data-oriented layouts, packed arrays, pages, caches and other performance techniques may be used inside owners/mechanics when measured evidence supports them.

Those representations do not define semantic repository architecture and may not become second truths.

### 14. Structural laws become executable

Architecture tests/CI must progressively reject:

- semantic dependency cycles;
- foreign `internal` access;
- domain knowledge in Kernel;
- Owner dependencies on Agents/Mechanics/Presentation;
- Continuum dependencies on concrete natural domains;
- simulation/presentation dependency inversion;
- reintroduction of forbidden generic dumping-ground packages;
- unsupported Gradle module proliferation.

## Why

This structure optimizes for long-lived change boundaries rather than current implementation phases.

A developer who wants to understand or replace Liquid, Terrain, Movement or Agents starts in one semantic place. Storage, solver and lifecycle implementation can evolve behind that place without teaching unrelated consumers about the change.

The model also makes duplication easier to detect: if two packages both appear to own the same fact, the architecture is wrong instead of the duplication being justified by “different layers”.

The single `:simulation` module removes artificial build boundaries while package visibility + ArchUnit-style structural tests provide finer-grained enforcement at the actual semantic boundaries.

## Consequences

### Positive

- concepts are discoverable by semantic name;
- code that changes together stays together;
- owner-local implementation can be replaced with lower blast radius;
- Generation/Physics/Storage cannot become parallel authorities by default;
- package cycles and internal leaks can be tested explicitly;
- data-oriented/performance refactors can remain implementation detail;
- new domains can be added without adding central special cases or Gradle modules.

### Costs

- some package boundaries require more deliberate public capability design;
- composition roots have explicit dependency lists;
- old umbrella packages/facades must be dismantled rather than merely renamed;
- package-level architecture tests become mandatory because Gradle no longer provides domain-level firewalls;
- semantic ownership disputes must be resolved before implementation rather than hidden by technical-layer placement.

## Alternatives considered

### Separate Foundation / World / Generation / Simulation Gradle modules

Rejected as the primary semantic architecture. It gives strong coarse compile-time direction but horizontally scatters individual domains and makes “state versus physics versus generation” compete with semantic ownership.

Neutral libraries may become separate modules in the future only if they become genuinely independently reusable/deployable artifacts, not merely to organize source code.

### Global layered/Clean/Onion package tree

Rejected as the primary folder axis. Directional dependency ideas remain useful locally, but global `domain/application/infrastructure` or `generation/physics/storage` trees would scatter a single EvoForge concept.

### Universal ECS as repository architecture

Rejected. ECS/data-oriented storage may be an internal implementation technique for measured hot paths, but `components/systems/entities` is not the semantic map of EvoForge.

### Full plugin/microkernel framework

Rejected. EvoForge keeps a small neutral kernel and replaceable components, but does not introduce a universal `Plugin`, dynamic service registry or mutable context bag.

### Global event-driven architecture

Rejected for causal simulation. Direct typed dependencies make deterministic cause/effect easier to audit. Events remain available for diagnostics/observers or genuinely reactive boundaries.

## Architectural sources and influence

- David Parnas, *On the Criteria To Be Used in Decomposing Systems into Modules* — hide design decisions likely to change: https://doi.org/10.1145/361598.361623
- Simon Brown, modular monolith/package-by-component — small public component surface, cohesive feature ownership: https://simonbrown.je/modular-monolith/
- Martin Fowler, Bounded Context — explicit model boundaries: https://martinfowler.com/bliki/BoundedContext.html
- Alistair Cockburn, Hexagonal Architecture — semantic ports/adapters at true boundaries: https://alistair.cockburn.us/hexagonal-architecture/
- John Ousterhout, software complexity/deep modules — information hiding, change amplification and cognitive load: https://web.stanford.edu/~ouster/cgi-bin/cs190-winter18/lecture.php?topic=complexity
- ArchUnit — executable architecture constraints for Java packages: https://www.archunit.org/userguide/html/000_Index.html

These are architectural influences, not claims that EvoForge implements any one framework wholesale.

## Current implementation

The architecture reset is implemented through Draft PR #132.

The first accepted checkpoint inside that PR collapses the old `:foundation`, `:world` and `:generation` Gradle projects into `:simulation` without changing behavior. Subsequent checkpoints reclassify existing packages by semantic ownership, remove mixed umbrella ownership, repair cycles and add executable architecture fitness tests.

Until PR #132 is accepted/merged, package locations in `develop` may still reflect the previous architecture; this ADR is the target law for the reset branch and the architecture docs must be reconciled with the final code before the PR leaves Draft state.

## Related documentation

- [Repository Laws](../../AGENTS.md)
- [Architecture](../architecture.md)
- [Project Context](../project-context.md)
- [Development Workflow](../guides/development-workflow.md)
- [Testing Strategy](../guides/testing.md)
- [Documentation Guide](../guides/documentation.md)
- [ADR-023: Strict modular architecture and replaceable boundaries](023-strict-modular-architecture.md) — superseded horizontal module topology; several abstraction principles remain incorporated here.

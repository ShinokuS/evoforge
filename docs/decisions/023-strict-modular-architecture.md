# ADR-023: Strict modular architecture and replaceable boundaries

- Status: Superseded by [ADR-025](025-owner-first-modular-simulation.md)
- Scope: Repository-wide architecture, module structure and algorithm/process extensibility
- Decision: EvoForge is composed from small cohesive ownership blocks with explicit one-way dependencies and narrow typed seams. Independently meaningful algorithms, processes and policies are replaceable through composition; package/file structure mirrors those responsibilities. Abstraction is mandatory at real semantic boundaries and deliberately avoided for private implementation detail that has no independent consumer.

> **Historical note:** the cohesion, narrow-contract, replaceability and anti-speculative-abstraction principles below remain important and were incorporated into ADR-025. The specific `foundation -> world -> generation/simulation` Gradle-module topology in section 11 is no longer canonical because it horizontally split one semantic domain across technical stages. It is preserved here as historical rationale rather than silently rewritten.

## Context

EvoForge is expected to evolve by replacing and combining algorithms rather than repeatedly rewriting orchestration around one concrete implementation. A feature can pass tests while still become difficult to evolve if meaning, model policy, execution, repair logic, presentation and orchestration are fused together.

The opposite failure is speculative abstraction: turning every helper into a strategy or building a universal plugin framework before multiple real consumers need it.

## Decision

### 1. One responsibility per block

A class, record, interface, process or package should have one explainable architectural job.

### 2. Dependencies point through contracts

Cross-block dependencies use the narrowest typed semantic contract required by the consumer. Generic orchestration does not depend on concrete implementation internals or ambient mutable service bags.

### 3. Independently meaningful algorithms/processes are replaceable

When a model can vary without redefining the surrounding subsystem, it receives an explicit typed seam and is supplied through composition.

### 4. Orchestrators compose; they do not own domain policy

Composition roots may choose implementations and order real dependencies. Mathematics, thresholds, feature-specific rules and mutable state stay with their proper owners.

### 5. Meaning and execution remain distinct

A useful general direction is:

```text
semantic/authored meaning
        ↓
explicit model/calibration policy when needed
        ↓
replaceable algorithm/process
        ↓
typed fact or authoritative domain mutation
```

A model constant has one owner. Algorithms do not silently reinterpret already-defined semantics.

### 6. Package/file structure mirrors ownership

Packages communicate responsibility and dependency direction. Unrelated mechanisms do not accumulate in generic `util`, `manager`, `service`, `common` or giant stage classes merely because they are convenient to reach.

### 7. Generic code does not branch on replaceable concretes

Avoid concrete algorithm switches, material-name special cases and revision-specific branches in unrelated consumers when a typed adapter or composition root is the correct owner.

### 8. Abstraction is required at semantic seams, not everywhere

Replaceable does not mean every private helper becomes an interface. Internal implementation stays simple and concrete until another real implementation or consumer proves a stable independent concept exists.

### 9. Replaceability is verified

Where substitution is an architectural requirement, tests exercise the seam with an alternate/fake implementation and prove downstream code does not require the standard concrete class.

### 10. Architectural debt blocks further accumulation

If a new feature can only be added by broadening an already-confused owner, introducing concrete-type branching or passing an increasingly universal context object, identify the smallest missing boundary before adding scope.

### 11. Historical World, Generation and Simulation module boundary

> **Superseded.** This section records the architecture that ADR-025 replaces.

The dependency direction chosen at the time was:

```text
foundation
    ↑
   world
  ↑    ↑
generation  simulation
             ↑
            core
```

The semantic responsibilities were defined as:

- **Foundation** owned neutral value types and contracts that had no knowledge of World, Generation, Simulation or Presentation.
- **World** owned authoritative state, spatial/materialization access, revisions and persistence-facing state lifecycle.
- **Generation** owned reproducible initial-world inputs and algorithms that produced/refined World state. It did not own runtime physics.
- **Simulation** owned rules, physics, scheduling, AI, solvers and policies that consumed World through narrow read/mutation contracts.
- **Presentation** observed World/Simulation and never defined authoritative truth.

The implementation used standalone Gradle `:foundation`, `:world` and `:generation` modules as compile-time firewalls.

That topology was later rejected because the same semantic owner naturally needs state, Genesis, storage and owner-local physics. Splitting those responsibilities by technical stage increases change amplification and risks parallel ownership. ADR-025 keeps the semantic principles while replacing the physical topology with owner-first package modules inside one `:simulation` Gradle module.

## Why

The original decision correctly recognized the need for local failures, explicit contracts, replaceable algorithms and recoverable package structure. Those motivations remain valid.

The coarse Gradle-module boundary did not survive deeper analysis of the future axis of change. It made technical phase more important than semantic ownership. ADR-025 is therefore the current authority for repository/package topology.

## Consequences

Historical consequences of this ADR included:

- important seams used narrow interfaces/contracts;
- composition roots acquired more explicit dependencies;
- package/file moves were justified when ownership was hidden;
- architecture/substitution tests were expected at important seams;
- Gradle module boundaries were temporarily used as compile-time dependency firewalls;
- universal frameworks required evidence from multiple real consumers;
- refactors preserving behavior were separated from semantic changes.

Current consequences are defined by ADR-025. In particular, domain architecture enforcement moves from coarse Gradle projects to semantic package visibility + executable architecture tests inside `:simulation`.

## Historical implementation

This ADR was implemented by extracting `NormalizedValue` to `:foundation`, Continuum to `:world`, and Genesis contracts to `:generation`. `:simulation` consumed Foundation/World while runtime Landscape/Spatial/Occupancy/Liquid/Soil code remained in Simulation.

The owner-first architecture reset intentionally folds those modules back into `:simulation` before reorganizing by semantic owner. This historical section remains to explain why those earlier commits existed.

## Related documentation

- [ADR-025: Owner-first modular simulation architecture](025-owner-first-modular-simulation.md) — current authority
- [Architecture](../architecture.md)
- [Development Workflow](../guides/development-workflow.md)
- [Green checkpoint development](022-green-checkpoint-development.md)
- [Continuum large-world architecture](024-continuum-large-world-architecture.md)
- [Project Context](../project-context.md)

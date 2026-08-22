# ADR-023: Strict modular architecture and replaceable boundaries

- Status: Accepted
- Scope: Repository-wide architecture, module structure and algorithm/process extensibility
- Decision: EvoForge is composed from small cohesive ownership blocks with explicit one-way dependencies and narrow typed seams. Independently meaningful algorithms, processes and policies are replaceable through composition; package/file structure mirrors those responsibilities. Abstraction is mandatory at real semantic boundaries and deliberately avoided for private implementation detail that has no independent consumer.

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

### 11. World, Generation and Simulation have one-way ownership boundaries

The long-term dependency direction is:

```text
foundation
    ↑
   world
  ↑    ↑
generation  simulation
             ↑
            core
```

The semantic responsibilities are stricter than the package names:

- **World** owns authoritative state, spatial/materialization access, revisions and persistence-facing state lifecycle.
- **Generation** produces reproducible initial state or regeneration inputs for World. It does not own runtime physics.
- **Simulation** owns rules, physics, scheduling, AI, solvers and policies that consume World through narrow read/mutation contracts.
- **Presentation** observes World/Simulation and never defines authoritative truth.

Forbidden dependency directions:

- `world -> simulation`
- `world -> core/lwjgl3`
- `generation -> simulation`
- `simulation -> generation`

A simulation algorithm must be replaceable without changing World ownership. A generation algorithm must be replaceable without changing simulation mechanics. World must not know which solver, pathfinder, AI policy or renderer consumes its state.

The first compile-time firewall is the standalone Gradle `:world` module. Its build rejects project dependencies on `:simulation`, `:core` and `:lwjgl3`. Further extraction must preserve this direction rather than reintroduce indirect service-locator coupling.

## Why

This structure keeps failures local, makes algorithms understandable in isolation and lets future models change without reopening unrelated systems. It also makes repository context recoverable from code/package structure rather than conversation history.

ADR-023 works together with ADR-022: this ADR limits architectural shape; ADR-022 limits development step size.

## Consequences

- Important seams use narrow interfaces/contracts.
- Composition roots may have more explicit dependencies.
- Package/file moves are justified when ownership is hidden.
- Architecture/substitution tests are expected at important seams.
- Gradle module boundaries are used as compile-time dependency firewalls where ownership is fundamental.
- Universal frameworks require evidence from multiple real consumers.
- Refactors that preserve behavior are separated from semantic changes.

## Current implementation

Current examples include:

- Continuum production code, correctness tests and Continuum-specific scale profiles live in the independent `:world` module;
- `:simulation` consumes `:world`, while `:world` has a compile-time guard against depending on simulation or presentation modules;
- Continuum model, field and materialization responsibilities remain separated under `world.continuum`;
- deterministic coordinate-addressed sampling remains isolated from bounded materialization;
- runtime owners such as Landscape, Spatial, Occupancy, Liquids and Soil still live in `:simulation` and are the next ownership-audit area; moving them must separate authoritative state from solver/policy concerns rather than moving mixed packages wholesale;
- presentation consumes read/command boundaries instead of mutable domain systems.

Future Continuum stages must add new semantic layers behind similarly narrow contracts rather than rebuilding one monolithic generator.

## Related documentation

- [Architecture](../architecture.md)
- [Development Workflow](../guides/development-workflow.md)
- [Green checkpoint development](022-green-checkpoint-development.md)
- [Continuum large-world architecture](024-continuum-large-world-architecture.md)
- [Continuum Development Plan](../systems/world-generation/continuum-development-plan.md)
- [Project Context](../project-context.md)

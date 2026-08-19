# ADR-023: Strict modular architecture and replaceable boundaries

- Status: Accepted
- Scope: Repository-wide architecture, module structure and algorithm/process extensibility
- Decision: EvoForge is composed from small cohesive ownership blocks with explicit one-way dependencies and narrow typed seams. Independently meaningful algorithms, processes and policies are replaceable through composition; package/file structure mirrors those responsibilities. Abstraction is mandatory at real semantic boundaries and deliberately avoided for private implementation detail that has no independent consumer.

## Context

EvoForge is expected to evolve by replacing and combining algorithms rather than repeatedly rewriting orchestration around one concrete implementation. That requires more than generally “clean code”. A feature can pass tests while still becoming difficult to evolve if calibration, model policy, spatial synthesis, repair logic, presentation and orchestration are fused into one class or one package with unclear ownership.

The opposite failure is also possible: making every helper configurable or introducing one universal strategy/plugin framework before real consumers need it. That increases indirection without creating useful replaceability.

The project therefore needs an explicit architectural quality contract alongside ADR-022's development-process contract.

## Decision

Every non-trivial subsystem is designed around **cohesive ownership blocks and explicit seams**.

### 1. One responsibility per block

A class, record, interface, process or package should have one explainable architectural job. A file that simultaneously owns semantic input, calibration, algorithm policy, spatial/runtime execution and presentation is a design defect unless those concerns are genuinely inseparable.

### 2. Dependencies point through contracts

Cross-block dependencies use the narrowest typed semantic contract required by the consumer. Generic orchestration does not depend on concrete implementation classes, implementation-specific fields, ambient mutable contexts or service-locator bags.

### 3. Independently meaningful algorithms and processes are replaceable

When an algorithm/process can reasonably vary without redefining the surrounding subsystem, it receives an explicit typed seam and is supplied through composition.

Examples include generation algorithms, calibrators, planners, selectors, runtime processes or model strategies whose replacement is independently meaningful and testable.

Replacing one compatible implementation should not require editing unrelated downstream consumers.

### 4. Orchestrators compose; they do not implement domain policy

Composition roots and orchestrators may choose implementations and order real dependencies. They do not accumulate the mathematics, thresholds, feature-specific branches or mutable state owned by those implementations.

### 5. Meaning, calibration, model policy and execution remain distinct

When a domain has all of these concerns, the normal direction is:

```text
semantic/authored meaning
        ↓
domain calibration
        +
versioned/immutable model policy
        ↓
replaceable algorithm/process
        ↓
typed fact or authoritative domain mutation
```

A model constant has one policy owner. An algorithm should not silently re-derive user semantics that were already calibrated upstream.

### 6. Package and file structure mirrors ownership

Directories/packages are part of the architecture, not a dumping ground. Related types live together under the domain that owns them; unrelated mechanisms do not accumulate in generic `util`, `manager`, `service`, `common` or giant stage classes merely because they are convenient to reach.

Names should reveal responsibility. A contributor returning months later should be able to locate the owner of a fact/algorithm from the package tree and file names without reconstructing historical chat context.

### 7. Generic code does not branch on replaceable concretes

Avoid `instanceof` chains, concrete algorithm switches, feature-name/material-name branches and revision-specific special cases in unrelated consumers when a typed adapter, palette, recipe or composition root is the correct owner.

### 8. Abstraction is required at semantic seams, not everywhere

“Replaceable” does **not** mean every private helper becomes an interface. Internal mathematics/data structures remain simple and concrete until another real implementation or consumer proves a stable independent concept exists.

The desired architecture is:

```text
few strong abstractions
+ simple concrete implementations
+ explicit composition
```

not a deep speculative framework.

### 9. Replaceability is verified

Where substitution is an architectural requirement, tests should exercise the seam: provide a small alternate/fake implementation and prove orchestration/downstream behavior does not require the standard concrete class.

Architecture tests complement behavioral tests; they protect the ability to change implementations later.

### 10. Architectural debt blocks further accumulation

If a new feature can only be added by making an existing owner broader, introducing a concrete-type branch in generic code or passing an increasingly universal context object, stop and identify the smallest missing contract first.

Do not knowingly make a confused boundary larger merely because the immediate feature can be made to work.

## Why

This structure keeps failures local, makes algorithms understandable in isolation and allows future models to be swapped or combined without reopening unrelated code. Clear package ownership also makes context recovery reliable for humans and AI-assisted development: the repository itself explains where a change belongs.

The rule works together with ADR-022. ADR-023 limits **architectural shape**; ADR-022 limits **development step size**. Small checkpoints without clear boundaries still produce tightly coupled code, while beautiful abstractions developed in one huge unverified batch are still difficult to debug. Both contracts are required.

## Consequences

- Some subsystems expose several narrow interfaces instead of one concrete all-in-one stage.
- Composition roots may have more constructor dependencies, but those dependencies state real ownership explicitly.
- Package/file moves are justified when current structure hides ownership.
- Substitution/architecture tests are expected at important replaceable seams.
- New universal frameworks require evidence from multiple real consumers.
- A standard implementation may remain simple and concrete internally even while the boundary around it is replaceable.
- Refactoring for clarity/replaceability should preserve behavior and be separated from semantic changes according to ADR-022.

## Alternatives considered

One concrete implementation per feature with direct class dependencies was rejected because future algorithm replacement then propagates through orchestration and downstream consumers.

A universal plugin/service registry was rejected because it hides causal dependencies and erases useful domain types.

“Interface every helper” was rejected because speculative indirection makes code harder to read and edit without creating real architectural freedom.

Folder structure based mainly on technical type (`utils`, `managers`, `services`) was rejected when it obscures domain ownership; packages should primarily communicate responsibility and dependency direction.

## Current implementation

Existing examples include:

- `WorldGenerationAlgorithms` / `WorldPreparationAlgorithms` composing typed stage contracts;
- V12 semantic intent → `V12LandformCalibrator` + immutable calibration/recipe → replaceable elevation algorithm;
- V13 mountain intent → replaceable `MountainCalibrator` + immutable `MountainCalibration` / `MountainRecipe` → replaceable `MountainElevationAlgorithm`, composed by `V13MountainTerrainGenerator`;
- generic Terrain shape fitting separated from concrete runtime Shape bindings;
- Simulation owners such as Landscape, Spatial, Occupancy, Liquids and Soil holding separate authoritative facts behind narrow capabilities.

Future work must preserve the same discipline rather than treating these examples as world-generation-only conventions.

## Related documentation

- [Architecture](../architecture.md)
- [Development Workflow](../guides/development-workflow.md)
- [Green checkpoint development](022-green-checkpoint-development.md)
- [World-generation algorithm contracts](011-world-generation-algorithm-contracts.md)
- [World preparation and calibration boundary](021-world-preparation-and-calibration-boundary.md)
- [Project Context](../project-context.md)

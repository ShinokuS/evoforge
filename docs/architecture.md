# Architecture

This is the **global semantic contract** of EvoForge. It contains only rules that cross subsystem boundaries or constrain how the project may be extended. Current subsystem behavior belongs in [`systems/`](systems/runtime.md); historical reasoning belongs in [`decisions/`](decisions/001-authoritative-ownership.md).

## Project model

EvoForge is a deterministic emergent simulation built around specialized authoritative state owners, immutable definition data, event/scheduler-driven execution, discrete three-dimensional coordinates, narrow read capabilities and headless correctness tests.

The project is deliberately not a universal ECS, a giant mutable `WorldCell`, a design where every object updates every frame, or a framework that predicts every future mechanic in advance.

## Module boundary

```text
simulation/   authoritative pure-Java simulation, headless-testable
core/         libGDX presentation/debug application
lwjgl3/       desktop launcher
assets/       definition and presentation source data
```

`simulation` must not depend on libGDX or presentation concepts. Presentation may observe simulation through explicit read capabilities but never becomes an owner of authoritative state.

## Global invariants

1. **One authoritative owner.** Every mutable authoritative property has exactly one owner.
2. **Identity is narrow.** `ObjectRepository` owns object identity/existence; mechanics do not accumulate there.
3. **Definitions are immutable runtime descriptions.** Runtime state and definition data remain separate.
4. **Read through capabilities.** Systems consume narrow read contracts instead of mutable internals of other owners.
5. **Mutation is explicit.** Cross-owner writes use a clearly owned semantic mutation capability rather than shared mutable state.
6. **Expected impossibility is data.** Normal domain rejection is structured; broken programming/configuration/invariants are exceptions.
7. **Simulation-thread authority.** Authoritative mutation remains on the simulation thread until explicitly redesigned.
8. **Scheduler is infrastructure.** It owns activation/order, not domain meaning.
9. **Public semantics survive representation changes.** Storage, cache and algorithm choices are replaceable behind contracts.
10. **Commands are external intent.** Internal continuing processes do not turn the Control layer into internal RPC.
11. **Generic Control is domain-neutral.** It routes and observes commands; world domains do not depend on Control.
12. **Navigation is structural topology.** Transition cost prices valid edges; occupancy describes dynamic availability; movement executes a concrete actor transition.
13. **Spatial position stays authoritative during timed movement.** An in-flight action does not create a second authoritative coordinate.
14. **Presentation never defines simulation truth.** Camera visibility, cutaway rules, interpolation and debug overlays cannot determine authoritative mechanics.
15. **Simulation is observer-independent.** Camera visibility, rendering range, player proximity or whether a region is currently presented must not select different authoritative behavior rules or simulation fidelity. Observation alone is not a cause in the world.
16. **Optimization preserves semantics.** Scheduling, sleeping processes, analytical progression, batching, indexing and compact representations may reduce computational work only when they preserve the authoritative outcome required by the mechanic; optimization must not replace distant-world behavior with different rules merely because nobody is watching.
17. **Measure hot paths before changing representation.** Once a path is proven hot, avoid unnecessary scans, allocations, boxing and temporary collections.
18. **Fundamental mechanics are observable and testable.** New systems arrive with headless correctness tests and a diagnostic strategy.
19. **World containment is shared geometry, not per-domain edge policy.** When a runtime configures finite `WorldBounds`, the common Geometry view presents coordinates outside that box as physically closed. Water, Navigation, Movement and other geometry consumers must not each invent a different map-edge rule. A runtime with no configured bounds may deliberately retain unbounded semantics.

## Extension discipline

### No concrete-type decision chains in generic consumers

A generic consumer must not grow extension logic such as:

```java
if (value instanceof ConcreteA) { ... }
else if (value instanceof ConcreteB) { ... }
```

when the concrete type selects replaceable subsystem behavior.

Concrete types may be known by a **specialized typed adapter/binding** and by the **composition root that registers it**. Generic consumers dispatch through the narrow abstraction. Registration uses exact Java types, not string identifiers.

This is the same architectural principle whether the consumer is simulation or presentation: concrete knowledge is localized instead of spreading through renderers, systems and inspectors.

Do not add a generalized framework for semantics that do not yet exist. The rule is to choose a clean extension point for current behavior, not to invent future behavior.

### Composition before central switches

When a new implementation fits an existing semantic contract, add the implementation and its tests/binding. Existing generic consumers should not require modification merely to recognize its concrete class.

If a genuinely new implementation cannot fit the current contract, revisit the contract with the new consumer as evidence instead of adding a type-specific escape hatch.

## World addressing

Authoritative positions use:

```text
(int x, int y, int z)
```

`int` is the public coordinate representation, not a promise that every integer is a usable coordinate in every runtime.

A `SimulationAssembly` may configure one inclusive finite `WorldBounds`. Its shared `WorldGeometryLookup` resolves every coordinate outside that box as `FullShape`, so generic physical/structural consumers observe one closed boundary through their ordinary Geometry dependency. Setup placement and initial Water also reject coordinates outside configured bounds.

If no bounds are configured, the runtime preserves the earlier unbounded coordinate semantics. Chunk/region layout, generated versus unloaded state, packed internal coordinates and streaming remain separate future representation decisions.

Shared XYZ coordinates are addresses; they do not imply a universal owner of all state at that coordinate.

## Core relationship of traversal systems

```text
Geometry        local structural/physical shape semantics
    ↓
Navigation      does a directed structural edge exist?
    ↓
TransitionCost  what is the actor-independent intrinsic price of that edge?
    ↓
Occupancy       is relevant space dynamically available/claimed?
    ↓
Movement        can this actor start and complete the concrete move?
```

Pathfinding is a consumer of these facts, not their owner. It must not invent a second topology or cost model. Dynamic mover constraints such as Water wading may filter advisory Pathfinding and are revalidated by Movement, but they still do not become Navigation topology.

## Time and execution

Simulation time is discrete. Scheduler/process infrastructure controls *when* work runs; domain systems own *what* that work means. Rendering frame rate is not simulation time.

External commands may initiate domain work. Continuing domain actions are owned by their mechanic and scheduled through narrow infrastructure contracts.

Observer-independent simulation does not require every entity to execute work every tick. A sleeping, travelling, crafting or otherwise predictable process may be scheduled or advanced analytically when the mechanic can preserve the same authoritative semantics. Computational activity follows causal work, not camera distance.

## Testing and architectural guards

Headless tests are the primary correctness mechanism. Important boundaries that are cheap to express structurally should be executable tests: module dependencies, constructor capabilities, generic/concrete dependency rules and other invariants that should fail immediately when violated.

Visual quality remains manually inspected where unit tests would only pretend to measure aesthetics. Pure rendering math, deterministic selection, cache correctness and presentation boundaries should still be tested where practical.

## Performance philosophy

Optimization is continuous but evidence-driven:

```text
instrument → reproduce → identify hot path → optimize behind contract → verify
```

Do not postpone obvious measured regressions until release, and do not introduce chunks, caches, packed storage or concurrency without a representative workload. Prefer eliminating unnecessary work while preserving world semantics over changing the truth of the simulation according to observation or proximity.

## Documentation change rule

Documentation follows semantic ownership just like code:

- a new subsystem normally adds one new `systems/<name>.md` page;
- completed subsystem pages change only when **their own semantics or contract** change;
- this file changes only when a **global architectural rule** is deliberately revised;
- implementation-only refactors normally require no semantic documentation change;
- durable rationale goes to `decisions/`;
- exploratory context goes to `notes/` and is explicitly non-normative.

See [Documentation Guide](guides/documentation.md).

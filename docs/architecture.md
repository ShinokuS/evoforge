# Architecture

Architecture is the small set of rules that every EvoForge system must obey. It does **not** describe every mechanic. Think of it as the constitution of the simulation: individual System pages explain the laws of Water, movement, agents, world generation and so on; this page explains the boundaries that keep those laws from contradicting each other.

If you do not write software, the most important idea is simple: **the simulated world must have one consistent truth, and looking at the world must never change that truth.**

## What EvoForge is

EvoForge is a deterministic emergent simulation built from specialized state owners, immutable definition data, explicit scheduled processes, discrete three-dimensional coordinates and narrow contracts between systems.

It is deliberately **not**:

- one giant mutable `WorldCell` object that stores every possible property;
- a universal ECS whose abstractions are chosen before real mechanics need them;
- a simulation where every object must update once per rendering frame;
- a renderer that secretly decides what is physically present;
- a framework that predicts every future mechanic in advance.

The design favors small explicit owners and replaceable algorithms because those are easier to reason about, test and evolve.

## Repository boundary

```text
simulation/   authoritative pure-Java simulation + world generation
core/         libGDX presentation, debug scenarios and developer tools
lwjgl3/       desktop launcher
assets/       authored definitions and presentation data
docs/         explanations, decisions, guides and historical journal
```

`simulation` may be run and tested without libGDX. Presentation code may read the simulation through explicit capabilities, but presentation never owns authoritative simulation state.

## A mental model: facts have owners

Imagine a Cow standing in Water. Several facts are involved:

```text
"this Cow exists"                 ObjectRepository
"the Cow is at x,y,z"             Spatial
"that destination is reserved"    Occupancy
"the terrain has this shape"      Geometry / Landscape
"this much free Water is here"    LiquidSystem / Water facade
"this much Water is in Soil"      SoilLiquidSystem
```

They all describe one world, but each fact has one owner. A system that needs a fact asks for a narrow read capability instead of reaching into another owner's mutable internals.

This prevents two systems from silently disagreeing about the same truth.

## Global laws

### 1. One authoritative owner per mutable fact

Every mutable authoritative property has exactly one owner. Derived caches and presentation views may duplicate information for speed or convenience, but they must be rebuildable from the authoritative source and may not become a second truth.

### 2. Identity is narrow

`ObjectRepository` answers whether an object exists and owns its identity. Movement, needs, orientation, stock, growth and other mechanics do not accumulate inside the identity repository.

### 3. Definitions and runtime state are different things

Definitions describe immutable authored meaning: what kind of object/material/agent something is. Runtime systems own changing state: where it is, how hungry it is, how much Water is present, and so on.

A definition is not a mutable game object.

### 4. Read through capabilities; mutate through explicit owners

Systems depend on the smallest read contract they need. Cross-owner mutation uses an explicit semantic mutation capability owned by the domain being changed.

This makes dependencies visible and testable.

### 5. Expected rejection is data; broken invariants are exceptions

A normal domain failure such as “destination occupied” or “not enough stock” should be returned as a structured result. Programming errors, invalid configuration and impossible invariant violations should fail loudly as exceptions.

### 6. Simulation-thread authority

Authoritative mutation currently happens on the simulation thread. Concurrency may be added later only with an explicit ownership redesign; background work must not silently mutate live world truth.

## Time and execution

### Simulation time is not rendering time

The simulation advances in discrete ticks. Rendering can run faster or slower without changing authoritative time.

The Scheduler and process infrastructure answer **when** work executes. Domain systems answer **what that work means**.

### The scheduler is infrastructure, not a domain owner

A movement process, growth process or liquid process may sleep until useful work is due. The scheduler does not own movement, growth or Water semantics; it only orders activation.

### External commands are intent, not internal RPC

The Control layer accepts external intent and reports results. Continuing internal actions are owned by their mechanic and scheduled through process infrastructure rather than repeatedly calling Control as an internal message bus.

### Sleeping and analytical progression are allowed

Observer-independent simulation does not mean every entity performs work every tick. A predictable action may sleep or advance analytically if it preserves the same authoritative result required by the mechanic.

Optimization may reduce work; it may not replace the rules with cheaper rules merely because nobody is watching.

## Space and containment

### Public coordinates

Authoritative locations use integer addresses:

```text
(x, y, z)
```

The coordinates are addresses in one continuous world. Sharing coordinates does not imply that one universal cell object owns every fact at that address.

### Optional finite bounds

A runtime may configure inclusive `WorldBounds`. Outside a configured box, shared `WorldGeometryLookup` presents physically closed geometry (`FullShape`). This creates one common containment rule for Water, Navigation, Movement and other structural consumers.

Without configured bounds, the older unbounded addressing semantics remain available.

Loaded/unloaded streaming state is a separate future problem. `outside bounds` must not be confused with `not currently loaded`.

## Structural traversal chain

Traversal responsibilities are intentionally layered:

```text
Geometry
  “what physical shape occupies the cells?”
      ↓
Navigation
  “does a directed structural edge exist?”
      ↓
TransitionCost
  “what is the actor-independent intrinsic cost of that edge?”
      ↓
Occupancy
  “is the relevant space dynamically available/reserved?”
      ↓
Movement
  “can this actor start and complete this concrete transition?”
```

Pathfinding reads these facts to propose a route. It does not invent another topology or another cost system.

Dynamic actor constraints such as Water wading may filter route advice and are always revalidated by authoritative Movement when the move actually starts/commits.

### Position remains authoritative during timed movement

An in-progress move does not create a second “halfway” authoritative coordinate. Presentation may interpolate visually, but the simulation keeps one discrete authoritative position and commits the transition according to Movement semantics.

## Presentation and observation

### Presentation never defines truth

Camera visibility, cutaway rules, sprite choice, interpolation and debug overlays are presentation. They may explain simulation truth but never determine it.

### The simulation is observer-independent

Camera distance, player proximity, whether a cell is rendered, or whether a region is currently inspected must not select different authoritative behavior rules.

This is stronger than “the renderer does not mutate state”: it also forbids silently using a lower-fidelity simulation model merely because a region is off-screen.

## Extensibility

### Public semantics must survive representation changes

A storage structure, cache or algorithm may be replaced behind its contract without changing the public meaning of the system.

### Generic consumers do not branch on concrete implementation/content types

Avoid extension chains such as:

```java
if (value instanceof ConcreteA) { ... }
else if (value instanceof ConcreteB) { ... }
```

or content switches such as:

```text
if mountain -> granite
if river -> sand
```

when the concrete type/key is merely selecting replaceable behavior.

Concrete knowledge belongs in a specialized typed adapter/binding and in the composition root that registers it. Generic orchestration depends on narrow abstractions.

### Composition before central switches

When a new implementation fits an existing semantic contract, add it behind that contract. Generic consumers should not need a new special case just to recognize a concrete class.

If a real new consumer proves the contract insufficient, revise the smallest owning contract rather than adding an escape hatch.

### Do not build universal abstractions without consumers

A possible future common concept is not enough reason to create a framework. Build the smallest correct boundary for the current real mechanic and extract shared structure only when multiple real consumers prove it exists.

## World-generation architecture

World generation creates the **initial facts of the world**. Runtime Simulation owns what happens after handoff.

Generation stages are replaceable behind typed semantic seams. Orchestration depends on generated facts and layer-specific contracts rather than concrete algorithms or a universal mutable generation context.

The canonical authoring path is:

```text
human semantic intent / definitions
            ↓
validation / semantic compilation
            ↓
world-specific domain calibration
            ↓
versioned algorithm recipe where needed
            ↓
replaceable spatial algorithm
            ↓
immutable typed generated facts
            ↓
preparation / materialization / bootstrap
            ↓
ordinary authoritative runtime
```

Generated-world bootstrap is composition, not a second simulation. It may translate immutable generated facts into setup calls, but it must not continue owning Terrain, Water or Soil after runtime starts.

Intentional changes to deterministic generated facts require explicit generation-version compatibility handling.

See [World Generation](systems/world-generation/overview.md).

## Determinism

For a fixed authoritative input set and compatible algorithm/revision, the result must be replayable. Random-looking generation uses deterministic addressable samples rather than depending on incidental call order.

Determinism is an architectural feature because it enables exact tests, regression comparison, seed reproduction and reliable debugging.

It is not a claim that all future gameplay must be predictable to the player; it is a claim that the engine can reproduce the same authoritative computation from the same inputs.

## Performance

Optimization follows evidence:

```text
instrument
   ↓
reproduce representative workload
   ↓
identify actual hot path
   ↓
optimize behind the same semantic contract
   ↓
verify behavior + performance
```

Once a path is known to be hot, avoid unnecessary scans, allocations, boxing and temporary collections. But do not introduce chunks, packed coordinates, concurrency or alternate distant-world rules without a measured need and a semantic design.

## Testing and observability

Fundamental mechanics require headless deterministic tests. Important architectural boundaries that can be expressed structurally should also be executable tests so violations fail immediately.

Diagnostics are part of system design: important state transitions should be inspectable without teaching the visualizer to own simulation truth.

Visual aesthetics are different. Unit tests should protect deterministic geometry/selection/invariants, while final visual quality is manually accepted when necessary.

## Documentation ownership

Documentation follows the same ownership principle as code:

- [Project Context](project-context.md) — concise current baseline and recovery path;
- this file — only global cross-system rules;
- `systems/<group>/...` — current behavior/algorithms of a subsystem;
- `roadmap.md` — milestone status and intentionally deferred scope;
- `decisions/` — durable rationale for accepted architectural choices;
- `guides/` — practical contributor workflows;
- `journal/` — historical, exploratory, audit and acceptance context;
- `references.md` — reusable external model/algorithm sources.

Implementation-only refactors normally do not require semantic documentation changes. A semantic change must update the owning System page in the same PR; a global architectural change also updates this page and normally receives an ADR when the reason should survive.

See the [Documentation Guide](guides/documentation.md).

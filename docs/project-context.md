# Project Context

This page is the fastest current-state recovery path for EvoForge. It answers: **what the project is, what architecture is authoritative, what is currently being changed, and what work is blocked until that change is accepted**.

Repository-root `AGENTS.md` is mandatory reading before this page for any code/documentation action.

## EvoForge in one minute

EvoForge is a deterministic simulation of a persistent three-dimensional world. Complex behavior should emerge from explicit physical and behavioral rules rather than presentation tricks or content-specific hard-coding.

```text
authored semantic meaning
        ↓
deterministic owner-local Genesis
        ↓
authoritative semantic owners
        ↓
explicit cross-owner mechanics
        ↓
observable world state
        ↓
visualizer / diagnostics
```

The renderer is an observer. Camera/render state never decides what is physically true.

## Repository map

```text
simulation/   all authoritative simulation, Genesis, world state, physics, mechanics and agents
core/         libGDX visualizer, debug scenarios and presentation adapters
lwjgl3/       desktop launcher
assets/       authored definitions and presentation data
docs/         canonical rules, systems, decisions, guides and history
```

Only `simulation`, `core` and `lwjgl3` are code/Gradle modules under the current architecture.

## Architecture reset is the current blocking work

Draft PR #132 is rebuilding the codebase around [ADR-025: Owner-first modular simulation architecture](decisions/025-owner-first-modular-simulation.md).

The previous horizontal Gradle split into `foundation`, `world`, `generation` and `simulation` has been rejected because it divided one semantic domain across technical stages. ADR-023 is preserved as a superseded historical decision.

Current target laws:

- one authoritative owner per mutable fact;
- semantic owner/axis of change is the primary package boundary;
- owner-local Genesis, storage, physics and runtime implementation stay with that owner;
- cross-owner laws are explicit Mechanics and do not duplicate owner state;
- Kernel is domain-neutral execution infrastructure;
- Projections are rebuildable and never a second truth;
- Composition chooses/wires implementations but owns no domain policy;
- public semantic surfaces are narrow; foreign `internal` access is forbidden;
- dependencies are explicit and acyclic;
- architecture, determinism, testing, performance and documentation rules must become executable/CI-checked where practical.

No new Continuum/world-generation feature stage begins until this reset is accepted.

## Global simulation laws

These must survive every stage:

1. **one authoritative owner per mutable fact**;
2. **narrow typed read/mutation capabilities across owners**;
3. **observer/camera independence**;
4. **deterministic replay from authoritative inputs + compatible revision**;
5. **Definitions are immutable authored meaning, not runtime objects**;
6. **pages/chunks/caches/indexes are representation/projection, never natural truth**;
7. **abstraction at real semantic seams, simple concrete internals elsewhere**;
8. **no universal framework/context/service locator without an explicit future architecture decision**;
9. **package/file structure mirrors semantic ownership**;
10. **Genesis creates initial facts and hands them to ordinary owners**;
11. **performance optimization preserves semantics and is backed by representative evidence**;
12. **normative documentation changes with the contract it describes**.

## Current architecture map

The exact package tree is being migrated, but the canonical semantic map is:

```text
simulation
├── kernel/            time, scheduling and other neutral execution services
├── definition/        neutral authored-definition infrastructure only
├── genesis/           global initial-world composition only
├── world/             objective semantic owners
│   ├── continuum/
│   ├── space/
│   ├── geometry/
│   ├── geology/
│   ├── terrain/
│   ├── liquid/
│   ├── soil/
│   ├── atmosphere/
│   └── object/
├── mechanics/         true cross-owner laws such as Movement
├── agents/            autonomous cognition/needs/perception/decision
└── composition/       only if/where a real composition area is justified
```

Do not create empty packages merely to match this diagram. A package exists only when a real owner/responsibility exists.

## Continuum status

The dense V12–V15 world-generation line remains retired. Continuum remains the canonical large-world foundation.

Accepted work before the architecture reset includes deterministic addressable sampling, bounded page/cache work, scale/performance instrumentation and multi-resolution/local query/map work recorded in the Continuum system pages and development history.

Continuum must remain neutral infrastructure: coordinates/pages/caches/materialization are technical representation, not Terrain/Liquid/Geology truth.

The next substantive Continuum/world-generation stage is intentionally blocked until PR #132 finishes and the new package/dependency/testing/documentation laws are green.

## Definitions policy

Definitions describe immutable authored semantic meaning. Root definition infrastructure is neutral; domain-specific definition types/compilers belong with the owner/mechanic that consumes them.

Human-facing semantic controls normally use normalized meaning (`0..1` or `-1..1`) where appropriate. Solver coefficients/thresholds/tuning constants remain implementation/model policy unless they are genuinely authored semantic content.

## Performance policy

For an unbounded/persistent world, performance is architectural:

```text
representative workload
      ↓
measure work/memory/latency
      ↓
identify hot path
      ↓
optimize hidden implementation
      ↓
prove same semantic result
      ↓
retain regression profile when material
```

Camera distance or visibility may not select cheaper authoritative rules. Sparse/data-oriented/ECS/packed/page representations are internal techniques, not semantic architecture.

## Fast recovery path

Read in this order:

```text
AGENTS.md
docs/project-context.md
docs/architecture.md
docs/roadmap.md
docs/decisions/025-owner-first-modular-simulation.md
relevant docs/systems/** page
```

Then inspect the current owner package and its tests. During architecture PR #132, inspect the PR branch rather than assuming `develop` package paths are already final.

If current normative docs conflict with executable code/tests, reconcile the contradiction in the same change. Do not use chat history as the missing source of truth.

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

## Architecture reset accepted

PR #132 establishes [ADR-026: Semantic capability architecture](decisions/026-semantic-capability-architecture.md) as the authoritative repository architecture. The rejected horizontal `foundation` / `world` / `generation` split and the superseded exclusive owner-first taxonomy are historical only.

Current laws:

- one authoritative source per mutable fact;
- the primary unit is an independent semantic concept, not a technical layer or first consumer;
- reusable capabilities live with the concept they express and never inside Movement/Agent/another consumer merely because that consumer appeared first;
- mechanics/workflows coordinate independent semantic capabilities and own only workflow-specific process/policy state;
- authority, capability, algorithm, projection, process and Genesis are orthogonal roles within/around semantic modules;
- Kernel is domain-neutral execution infrastructure;
- public semantic surfaces are narrow, consumer-neutral and acyclic; foreign `internal` access is forbidden;
- mechanically decidable architecture laws, determinism, coverage and representative scale gates are enforced by tests/CI;
- package placement must pass the reuse test in root `AGENTS.md`.

PR #133 completed the final post-reset semantic cleanup: ArchUnit enforces production bytecode dependency direction and top-level world-module cycle freedom; the ambiguous `world/spatial`, umbrella `world/landscape`, generic `world/surface`, and consumer-owned `world/object/placement` boundaries are retired. The architecture gate is complete.

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

The canonical semantic map is:

```text
simulation
├── kernel/            time, scheduling and other neutral execution services
├── definition/        neutral authored-definition infrastructure only
├── genesis/           global initial-world composition only
├── world/             objective semantic owners
│   ├── continuum/      neutral large-world addressing/materialization
│   ├── geophysics/     continuous macro-geophysical skeleton
│   ├── material/
│   ├── object/
│   ├── space/          position, orientation, occupancy, placement, measurement
│   ├── geometry/
│   ├── navigation/
│   ├── geology/        authored geological profile/unit/material semantics
│   ├── terrain/
│   ├── liquid/
│   ├── soil/
│   ├── atmosphere/
│   ├── sky/
│   └── interaction/
├── mechanics/         true cross-concept workflows: Movement, Hydrology, TerrainMutation
├── agents/            autonomous cognition/needs/perception/decision
└── composition/       only if/where a real composition area is justified
```

Do not create empty packages merely to match this diagram. A package exists only when a real owner/responsibility exists.

## Continuum status

The dense V12–V15 world-generation line remains retired. Continuum remains the canonical large-world foundation.

Accepted work before the architecture reset includes deterministic addressable sampling, bounded page/cache work, scale/performance instrumentation and multi-resolution/local query/map work recorded in the Continuum system pages and development history.

Continuum remains neutral infrastructure: coordinates/pages/caches/materialization are technical representation, not Terrain/Liquid/Geology/Geophysics truth.

Stages 0–4 are complete. **Stage 5 — Macro Ocean + Geophysical Skeleton is IN PROGRESS in PR #135 and is not yet manually accepted.** The independent `world/geophysics` concept owns the continuous macro-elevation skeleton; ocean/land is derived from that same field relative to the shared sea datum. Existing Continuum infrastructure only samples/materializes/projects it.

Stage 5 now exposes authored `MacroGeophysicsDefinition` controls for ocean prevalence, continental scale, landmass cohesion, fragmentation and macro variation. These are normalized semantic world-generation inputs rather than exposed solver coefficients. Contrasting `SUPERCONTINENT`, `BALANCED`, `ARCHIPELAGO` and `OCEANIC` presets exist for quick inspection, while custom definitions remain the actual contract.

Stage 6 remains blocked until Stage 5 passes final automated verification and explicit manual acceptance of the F2 macro-geography across contrasting profiles.

See [Stage 5 — Macro Ocean + Geophysical Skeleton](systems/world-generation/stage5-macro-geophysics.md).

## Definitions policy

Definitions describe immutable authored semantic meaning. Root definition infrastructure is neutral; domain-specific definition types/compilers belong with the owner/mechanic that consumes them.

Human-facing semantic controls normally use normalized meaning (`0..1` or `-1..1`) where appropriate. Solver coefficients/thresholds/tuning constants remain implementation/model policy unless they are genuinely authored semantic content.

Stage 5 is the reference world-generation example: `MacroGeophysicsDefinition` exposes meaningful world character, while lattice spans, salts, interpolation exponents and blend coefficients remain hidden inside the replaceable geophysical algorithm.

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
docs/decisions/026-semantic-capability-architecture.md
relevant docs/systems/** page
```

Then inspect the current semantic module, its public capabilities, dependencies and tests.

If current normative docs conflict with executable code/tests, reconcile the contradiction in the same change. Do not use chat history as the missing source of truth.

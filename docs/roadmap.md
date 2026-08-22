# Roadmap

The Roadmap answers two questions: **what is currently accepted, and what is the next work that is allowed to begin?** Exact mechanics belong in `systems/`; global laws belong in `architecture.md`.

## Current blocking milestone — owner-first architecture reset

Draft PR #132 is the only active prerequisite for further substantive Continuum/world-generation work.

Goal:

```text
old mixed/horizontal boundaries
        ↓
one authoritative :simulation module
        ↓
semantic OWNER / MECHANIC / KERNEL / PROJECTION / COMPOSITION blocks
        ↓
acyclic public-contract dependencies
        ↓
architecture + correctness + scale/performance + documentation gates
```

The reset is not complete until all of these are true:

- `:simulation`, `:core`, `:lwjgl3` are the only code/Gradle modules;
- existing production code is reclassified into clear semantic owners/mechanics/kernel/projections/composition;
- old umbrella/technical buckets and duplicate authorities are removed;
- package cycles and foreign-internal dependencies are repaired;
- repository naming/file-placement laws are documented and mechanically enforced where practical;
- architecture fitness tests are green;
- deterministic/headless integration tests remain green;
- representative scale/performance profiles remain green;
- canonical documentation/system pointers match final package ownership;
- temporary refactor workflows/diagnostics are removed;
- PR #132 receives explicit manual architecture acceptance before leaving Draft state.

No new world-generation feature stage starts before this gate.

## Accepted Continuum foundation before reset

The previous dense V12–V15 world-generation line remains retired. Continuum is the canonical large-world direction.

Accepted foundations include the staged work already present in `develop` around:

- deterministic addressable large-world sampling;
- bounded technical paging/cache behavior;
- cache/request/scale diagnostics;
- representative 10k/100k/1M-style scale evidence where implemented;
- multi-resolution/local-query/map foundations already merged before the reset.

The architecture reset may move these files, but must not silently change their accepted semantics.

## Continuum guardrails after reset

Later work must not reintroduce:

- giant full-world authoritative rasters;
- camera-driven authoritative fidelity;
- chunks/pages/tiles as natural geography or second truth;
- independent feature painters that create contradictory geography;
- V16/V17/V18 whole-generator lineages instead of replaceable owner-local algorithms;
- arbitrary solver constants masquerading as semantic Definitions;
- universal mutable `WorldCell` / `WorldFact` truth stores;
- global `generation/<domain>` or `physics/<domain>` trees that scatter semantic owners.

## Stable foundations that the reset must preserve

Existing accepted simulation work includes deterministic time/scheduling, authored Definitions, objective geometry/terrain foundations, movement/navigation/pathfinding/occupancy behavior, autonomous agents, finite Liquid/Water/Soil mechanics, environmental water processes and observer-only diagnostics.

The reset is allowed to change package/type ownership and remove obsolete facades. It is not allowed to change accepted behavior accidentally.

## Development rule

Every future stage is a green checkpoint:

```text
owner + invariant
      ↓
small coherent implementation
      ↓
focused correctness evidence
      ↓
architecture checks
      ↓
representative scale/performance evidence where relevant
      ↓
documentation reconciliation
      ↓
explicit acceptance
```

A stage is not complete if its semantics, ownership, algorithms or current status still require chat history to reconstruct.

See [Project Context](project-context.md), [Architecture](architecture.md), [ADR-025](decisions/025-owner-first-modular-simulation.md) and the [Continuum Development Plan](systems/world-generation/continuum-development-plan.md).

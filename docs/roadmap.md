# Roadmap

The Roadmap answers two questions: **what is currently accepted, and what is the next work that is allowed to begin?** Exact mechanics belong in `systems/`; global laws belong in `architecture.md`.

## Current checkpoint — Stage 5 in progress

PR #132 established the repository-wide ADR-026 architecture and PR #133 completed its final semantic cleanup, removing residual ambiguous/umbrella boundaries and adding bytecode-level dependency/cycle enforcement.

Accepted reset guarantees:

- `:simulation`, `:core` and `:lwjgl3` are the only code/Gradle modules;
- reusable capabilities such as Occupancy, Navigation, Pathfinding and Geometry are independent semantic units rather than Movement/Agent internals;
- mechanics contain workflow-specific orchestration only;
- lower-level world semantics do not depend on mechanics or agents;
- legacy `world/mechanics` plus retired `world/spatial`, `world/landscape` and generic `world/surface` roots are mechanically rejected;
- object Position is the explicit `world/space/position` authority/capability; generic placement is `world/space/placement`; authored material identity is `world/material`; cross-concept terrain mutation is `mechanics/terrainmutation`; sky exposure is `world/sky`;
- ArchUnit checks production bytecode for world-module cycles and forbidden dependency direction;
- the mandatory reuse test for new concepts lives in root `AGENTS.md`;
- architecture fitness tests, deterministic tests, measured JaCoCo coverage floors and representative scale profiles are CI gates;
- canonical documentation points at ADR-026 and the final package ownership;
- temporary migration/refactor workflows are removed before merge.

## Current world-generation work

Canonical Continuum Stages 0–4 are complete. **Stage 5 — Macro Ocean + Geophysical Skeleton is now in implementation in PR #135 and is not yet accepted.**

The Stage 5 semantic owner is the independent `world/geophysics` concept. It exposes a deterministic continuous macro-elevation skeleton; ocean/land is derived from the same elevation relative to a shared sea datum. Continuum remains neutral addressing/materialization/map infrastructure and does not own geography.

Stage 6 is not allowed to begin until Stage 5 passes its automated gates and the user manually accepts the F2 macro-geography result.

See [Stage 5 — Macro Ocean + Geophysical Skeleton](systems/world-generation/stage5-macro-geophysics.md) for the exact contract and boundaries.

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

See [Project Context](project-context.md), [Architecture](architecture.md), [ADR-026](decisions/026-semantic-capability-architecture.md) and the [Continuum Development Plan](systems/world-generation/continuum-development-plan.md).

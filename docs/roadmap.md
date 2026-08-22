# Roadmap

The Roadmap answers two questions: **what is currently accepted, and what is the next work that is allowed to begin?** Exact mechanics belong in `systems/`; global laws belong in `architecture.md`.

## Current checkpoint — semantic capability architecture accepted

PR #132 completes the repository-wide architecture reset defined by ADR-026. The repository now uses one authoritative `:simulation` Gradle module decomposed by independent semantic concepts and consumer-neutral capabilities.

Accepted reset guarantees:

- `:simulation`, `:core` and `:lwjgl3` are the only code/Gradle modules;
- reusable capabilities such as Occupancy, Navigation, Pathfinding and Geometry are independent semantic units rather than Movement/Agent internals;
- mechanics contain workflow-specific orchestration only;
- lower-level world semantics do not depend on mechanics or agents;
- legacy `world/mechanics` and forbidden generic technical roots are mechanically rejected;
- the mandatory reuse test for new concepts lives in root `AGENTS.md`;
- architecture fitness tests, deterministic tests, measured JaCoCo coverage floors and representative scale profiles are CI gates;
- canonical documentation points at ADR-026 and the final package ownership;
- temporary migration/refactor workflows are removed before merge.

## Next allowed world-generation work

Canonical Continuum Stages 0–4 are complete. The next stage is **Stage 5 — Macro Ocean + Geophysical Skeleton**. Stage 5 has not started. Its work begins only after PR #132 is merged into `develop`, using the semantic-capability laws as a hard boundary.

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

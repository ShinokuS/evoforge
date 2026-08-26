# Roadmap

The Roadmap answers two questions: **what is currently accepted, and what is the next work that is allowed to begin?** Exact mechanics belong in `systems/`; global laws belong in `architecture.md`.

## Current checkpoint — Stage 6 architecture reset

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

Canonical Continuum Stages 0–5 are complete. **Stage 5 — Macro Ocean + Geophysical Skeleton was manually accepted in PR #135.**

The Stage 5 semantic owner is the independent `world/geophysics` concept. It exposes a deterministic continuous macro-elevation skeleton; ocean/land is derived from the same elevation relative to a shared sea datum. Continuum remains neutral addressing/materialization/map infrastructure and does not own geography.

Stage 5 includes semantic authored controls for ocean prevalence, continental scale, landmass cohesion, fragmentation and macro variation; reproducible/custom world seeds; and `SUPERCONTINENT`, `BALANCED`, `ARCHIPELAGO` and `OCEANIC` inspection profiles over the same stable definition contract.

### Rejected Stage 6 experiment

PR #136 attempted to build Stage 6 primarily as a noise/refinement-driven continuous heightfield with coastline warps and tile-level LOD repair. Manual inspection rejected that direction.

The experiment failed the actual visual/product target:

- landmasses read as scalar-field blobs rather than convincing continental geography;
- mountains read as isolated bumps/mottled lesions rather than coherent ranges;
- zoom mostly enlarged or resampled the same morphology rather than revealing new geographic structure;
- map pan/zoom visibly oscillated between coarse/fine representations, pixelated, blurred and stalled;
- further tuning of noise amplitudes, coastline warps, palette and tile thresholds would optimize the wrong representation.

PR #136 is closed and must not be merged. Its branch is archive-only.

### Replacement Stage 6 direction

**Stage 6 remains the next checkpoint, but its implementation direction is reset by [ADR-027 — Hierarchical Geomorphic Geography](decisions/027-hierarchical-geomorphic-geography.md).**

The required product target is a country/continental-scale world capable of containing hundreds of Songs-of-Syx-scale landscape regions while computing/materializing only requested areas.

The replacement architecture is structure-first rather than texture-first:

- sparse deterministic continental/crustal blocks and ocean basins establish large geography;
- explicit boundary geometry creates shelves/coasts and later allows tectonic relationships;
- mountain geography is represented as long belt/ridge structures with width, orientation, passes, foothills and child ridges, not isolated radial bumps;
- plateaus, broad uplands and basins are sparse regional structures;
- each structural feature has stable identity and deterministic hierarchical children;
- a local bounding-box query refines only intersecting structures to the requested physical/detail level;
- coarse map views observe parent geography; closer views add deterministic child structures, so zoom reveals new geography rather than larger pixels;
- technical tiles/pages remain bounded cache infrastructure only;
- the map renderer is reset around a stable overscanned front/back surface: pan at constant zoom cannot change LOD, replacement coverage is built off-thread and swapped atomically, and checkerboard parent fallback is forbidden.

Stage 6 does **not** need to preserve the rejected PR #136 terrain algorithm. Only genuinely representation-neutral foundations may be reused: deterministic addressing, bounded caches, async primitives and diagnostics.

Stage 7 drainage and later rivers/lakes/climate remain blocked until the replacement Stage 6 geography and renderer are manually accepted.

See [Stage 5 — Macro Ocean + Geophysical Skeleton](systems/world-generation/stage5-macro-geophysics.md), [ADR-027](decisions/027-hierarchical-geomorphic-geography.md) and the [Continuum Development Plan](systems/world-generation/continuum-development-plan.md).

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

The new hierarchy is allowed to generate sparse structural descriptors at very large scales. That is not a full-world raster: descriptors remain semantic causes and child structure is refined/materialized only for requested areas.

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

For replacement Stage 6, “small coherent implementation” means a vertical slice that proves the new representation: convincing continental geometry + explicit mountain belts + stable multi-scale rendering. It does **not** mean incrementally tuning the rejected heightfield until it looks less bad.

A stage is not complete if its semantics, ownership, algorithms or current status still require chat history to reconstruct.

See [Project Context](project-context.md), [Architecture](architecture.md), [ADR-026](decisions/026-semantic-capability-architecture.md), [ADR-027](decisions/027-hierarchical-geomorphic-geography.md) and the [Continuum Development Plan](systems/world-generation/continuum-development-plan.md).

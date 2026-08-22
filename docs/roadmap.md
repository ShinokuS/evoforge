# Roadmap

The Roadmap answers two questions: **what is already real in EvoForge, and what is intentionally next?** Exact mechanic behavior belongs in [Systems](systems/); global rules belong in [Architecture](architecture.md).

## Current position

The dense V12–V15 world-generation line is retired. Continuum development now advances **strictly one stage at a time** according to the canonical [Continuum World Development Plan](systems/world-generation/continuum-development-plan.md).

Accepted Continuum checkpoints:

- **Stage 0** — clean Continuum baseline and legacy worldgen retirement;
- **Stage 1** — bounded technical paging/cache;
- **Stage 2** — automated scale/performance gate plus runnable F2 page/cache inspector.

Current work:

- **Stage 3 — Multi-Resolution Continuum** is implemented in PR #121 and awaits manual acceptance before merge.

No Stage 4+ work may begin until Stage 3 has been reviewed and explicitly accepted.

## Stage 3 scope

Stage 3 proves that the same logical world can be read directly at several nested sampling scales:

```text
L0 step 1
L1 step 2
L2 step 4
...
```

A coarse request keeps bounded sample work and does not generate exact cells first. Shared world coordinates must produce identical authoritative values at every level.

The F2 inspector exposes sampling resolution separately from presentation zoom (`PageDown/PageUp` versus `+/-/wheel`).

## Architecture guardrails

No later stage may silently reintroduce:

- giant full-world authoritative rasters;
- camera-driven simulation fidelity;
- independent feature painters for unrelated geography;
- V16/V17/V18 whole-generator lineages;
- arbitrary solver constants masquerading as semantic Definitions;
- universal mutable `WorldCell` / `WorldFact` truth stores.

## Stable non-worldgen foundations

The repository retains accepted deterministic simulation/runtime work outside the retired generator: scheduling/time foundations, Definitions, Terrain/Geometry/Navigation, occupancy/movement/pathfinding, autonomous agents, finite Water/Soil mechanics and observer-only diagnostics.

Genesis must eventually hand initial facts to those ordinary owners rather than remain a second runtime simulation.

## Development rule

Each stage is a green checkpoint. Applicable stages require correctness, determinism/order/seam checks, measured performance and scale evidence, visual diagnostics, documentation, and explicit manual acceptance where meaningful.

A stage is not complete if its semantics still require chat history to reconstruct.

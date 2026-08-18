# World package organization audit — 2026-08

This note records the Stage 0 package-structure cleanup performed after the canonical world-generation pipeline and calibrated V12 boundary were established.

## Goal

The `simulation.world` root had accumulated packages for durable domains, generation lifecycle phases, implementation helpers and developer tooling at the same level. The code was still typed and testable, but repository navigation no longer communicated ownership clearly.

The cleanup therefore follows one rule: **a package path should answer who owns the concept**. We do not reduce the root count by inventing a vague umbrella such as `core`, `common`, `systems` or `generation-everything`; that would hide dependencies rather than clarify them.

## Changes made

### Surface morphology belongs to Terrain

The former root package:

```text
world.surface
```

contained only elevation-derived topographic facts and their generator. It is now:

```text
world.terrain.surface
```

This makes the relationship explicit: morphology is a derived Terrain fact consumed by terrain-material and Soil preparation. It is not an independent world domain.

### Warmup belongs to Diagnostics

The former root package:

```text
world.warmup
```

contained only the deterministic generated-world diagnostic runner. It is now:

```text
world.diagnostics.warmup
```

Warmup advances an ordinary runtime to requested checkpoints and observes it; it owns no simulation law. Keeping it under diagnostics prevents tooling from appearing as an authoritative world subsystem.

### Tests mirror production ownership

The corresponding test packages were moved with their production domains instead of leaving historical root-level test folders behind.

## Boundaries deliberately kept at the root

Some apparently small packages remain top-level because their responsibility is genuinely cross-domain or a real lifecycle boundary:

- `genesis` — immutable generation provenance and authored generation intent;
- `atlas` — authoritative generated facts and generation orchestration;
- `preparation` — pure generated-world preparation before runtime;
- `materialization` — one-way conversion of prepared/generated facts into runtime landscape state;
- `bootstrap` — runtime composition/handoff;
- `spatial` — coordinate/index/bounds/orientation ownership;
- `scale` — explicit physical scale contract shared by environment, Soil, Water and runtime compilation;
- `navigation` — traversal/navigation semantics;
- `pathfinding` — route-search algorithms;
- `mechanics` — explicit reusable physical/action mechanics;
- `landscape`, `terrain`, `geology`, `environment`, `weather`, `climate`, `agent`, `object` — real world domains or currently stable domain contracts.

Moving these only to make the root visually shorter would create large import churn without improving ownership. They should be nested only when the architecture itself proves a stronger owner, not as cosmetic folder compression.

## Package creation law

A new direct child of `simulation.world` is allowed only when it represents a durable independent world responsibility or a deliberate lifecycle boundary. Otherwise it must be nested beneath its owner.

Examples:

```text
GOOD  world.terrain.surface
GOOD  world.diagnostics.warmup
GOOD  world.environment.precipitation
BAD   world.helpers
BAD   world.util
BAD   world.generatedStuff
BAD   world.surface   // when the content is only Terrain-derived morphology
```

This rule applies to production and tests. Package organization is reviewed during each future stage audit so the tree evolves with real consumers instead of accumulating speculative categories.

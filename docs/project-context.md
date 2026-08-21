# Project Context

This page is the fastest way to reconstruct **what EvoForge is, what is already true in code, what must not be broken, and what should happen next**. It is intended to be sufficient after a long break or in a new AI-assisted development session.

## EvoForge in one minute

EvoForge is a deterministic simulation of a persistent three-dimensional world. Complex behavior should emerge from explicit physical and behavioral rules rather than presentation tricks or content-specific hard-coding.

```text
authored semantic meaning
        ↓
deterministic domain logic / Genesis
        ↓
authoritative runtime owners
        ↓
observable world state
        ↓
visualizer and diagnostics
```

The renderer is an observer. It never decides what is physically true.

## Repository map

```text
simulation/   pure-Java authoritative simulation and Continuum world-generation code
core/         libGDX visualizer, scenarios and presentation adapters
lwjgl3/       desktop launcher
assets/       authored definitions and presentation data
docs/         canonical explanations, decisions, guides and journal
```

`simulation` must not depend on libGDX/presentation code.

## Global rules that must survive every stage

- one authoritative owner per mutable fact;
- narrow typed read/mutation capabilities between owners;
- observer/camera independence;
- deterministic replay from authoritative inputs;
- semantic Definitions are immutable authored meaning, not mutable objects;
- technical pages/chunks/caches are representation, never natural geography or a second truth;
- abstraction at real semantic seams, simple concrete code inside a seam;
- no universal framework without multiple real consumers;
- package/file structure mirrors ownership;
- generation creates initial facts and then hands them to ordinary runtime owners.

## World-generation reset

The previous V12–V15 dense generator has been intentionally retired, not preserved as a legacy alternative. Its World Atlas, bootstrap/preparation pipeline, terrain/mountain/lake/bathymetry generation implementations, generated climate/weather forcing and coupled stale tests were removed.

Do not reconstruct new work from the old normative V12–V15 pages or class names. Historical material in the Development Journal is context only.

The current world-generation architecture is **Continuum**.

## What exists now

The executable Continuum foundation is deliberately small:

```text
ContinuumWorldDomain
    logical large-world coordinates

addressable deterministic sampling
    same authoritative coordinate → same value
    independent of request order

ContinuumMaterializer
    materializes only a requested bounded window

ContinuumFoundationTest
    protects determinism, large coordinates and overlap equality
```

This foundation proves the direction but does not yet constitute the full Phase 0 large-world proof.

## Immediate next work

Continue with **Phase 0 — Foundation harness** in [Continuum World Development Plan](systems/world-generation/continuum-development-plan.md).

The next PR should add:

1. bounded page/window addressing and a cache with an explicit capacity/byte budget;
2. cache hit/miss/load/eviction/resident metrics;
3. deterministic eviction + reload tests;
4. 10k, 100k and 1M logical-domain scale proofs showing memory is tied to active pages, not world area;
5. a Continuum preview supporting pan/zoom without whole-world materialization;
6. visible page/cache/request diagnostics.

Do not begin real continents, terrain, rivers or lakes until this scaling proof is accepted.

## Definitions policy

Keep the useful concept of authored semantic Definitions, but do not preserve dead worldgen JSON merely because it once existed.

A world-generation definition is introduced only when a current semantic owner consumes it. Human-facing controls use normalized meaning, normally `0..1` or `-1..1`. Algorithm tuning constants, thresholds and physical solver coefficients live with the implementation unless they are genuinely part of content meaning.

## Fast recovery path

Read in this order:

```text
docs/project-context.md
docs/architecture.md
docs/roadmap.md
docs/systems/world-generation/overview.md
docs/systems/world-generation/continuum-development-plan.md
```

Then inspect:

```text
simulation/src/main/java/io/github/evoforge/simulation/world/continuum/
simulation/src/test/java/io/github/evoforge/simulation/world/continuum/
```

If later documentation conflicts with these files and executable tests, reconcile the docs in the same change rather than relying on old chat history.

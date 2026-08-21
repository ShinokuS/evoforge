# Roadmap

The Roadmap answers two questions: **what is already real in EvoForge, and what is intentionally next?** Exact mechanic behavior belongs in [Systems](systems/); global rules belong in [Architecture](architecture.md).

## Current position

EvoForge has deliberately retired the previous dense V12–V15 world-generation line. Its atlas/bootstrap/preparation/terrain/materialization/calibration/generated climate-weather generation layers and their stale regression suite are no longer the production baseline.

The current world-generation baseline is the small **Continuum foundation**:

- large logical coordinates without whole-world allocation;
- deterministic addressable sampling;
- bounded window materialization;
- equality of shared coordinates across overlapping requests;
- no dependency on the deleted V-numbered generator.

See [World Generation — Continuum](systems/world-generation/overview.md) and the canonical [Continuum World Development Plan](systems/world-generation/continuum-development-plan.md).

## Immediate next milestone — Continuum Phase 0

Phase 0 is the first executable large-world proof. It must prove the architecture before real geography is reintroduced.

Required scope:

- construct 10,000 × 10,000, 100,000 × 100,000 and 1,000,000 × 1,000,000 logical worlds without allocation proportional to area;
- materialize only bounded requested windows/pages;
- add an explicit bounded cache with deterministic eviction/rematerialization;
- expose cache/page counters and resident-memory evidence;
- prove query order and cache history cannot change generated values;
- provide a preview capable of pan/zoom over the logical world without full-world generation;
- display request/page/cache diagnostics in the GUI.

Acceptance is based on deterministic tests plus measured time/allocation/resident-memory evidence. A large logical address space that merely fits on one developer machine by allocating the whole raster fails this milestone.

## After Phase 0

The master plan then advances one independently meaningful concern at a time: compact structural geography, continental/oceanic structure, geological causes, coherent orography, drainage/depression topology, rivers/lakes, continuous terrain reconstruction, exact XYZ materialization, climate and later runtime handoff.

No later phase may silently reintroduce these rejected patterns:

- giant full-world authoritative rasters as the default representation;
- camera-driven simulation fidelity;
- feature painters that own unrelated geography;
- V16/V17/V18-style whole-generator lineages;
- definitions containing arbitrary algorithm tuning constants as if they were semantic content;
- a universal mutable `WorldCell` or `WorldFact` truth store.

## Stable non-worldgen foundations

The repository retains the accepted simulation/runtime work outside the retired generator: deterministic scheduling/time foundations, Definitions, Terrain/Geometry/Navigation, occupancy/movement/pathfinding, autonomous agents, finite Water/Soil mechanics and observer-only diagnostics.

World generation must hand initial facts to those ordinary owners rather than remain a second runtime simulation.

## Development rule

Each Continuum phase is a green checkpoint. Applicable stages require:

- semantic correctness tests;
- determinism/locality and seam tests;
- substitution tests at real algorithm boundaries;
- measured performance/allocation/resident-memory evidence;
- visual diagnostics as soon as a spatial result is meaningful;
- explicit manual acceptance for morphology;
- documentation updated in the same PR.

A phase is not complete if its current semantics still require chat history to reconstruct.

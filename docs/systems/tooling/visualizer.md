# Visualizer and Developer Inspection Tools

## Purpose

The Visualizer is an observer of the world. It may display simulation or Continuum diagnostics, but it never decides what is physically true.

## Core rule

Do not create a visual screen merely because a development stage has diagnostics.

Use the visualizer when there is something meaningful to inspect as a world or spatial process:

- move around the world;
- zoom in/out;
- inspect spatial layers;
- compare continuous and exact terrain;
- watch water, terrain or other runtime state evolve;
- later control/view simulation time on the real world.

Internal infrastructure such as scheduler queues, handle reuse, compaction counters or cache bookkeeping is primarily verified by tests and performance profiles. A dashboard of numbers is not a substitute for a world view.

## Current F2 screen

`F2` opens the spatial **Continuum Inspector**.

It remains the common world-oriented inspection surface while Continuum is developed. It supports spatial navigation and multi-resolution inspection of the current deterministic synthetic field. This field is diagnostic scaffolding, not geography.

Stage 2 — Infinite-Time Foundation does not replace this screen with a scheduler dashboard. Its time/sleep/compaction invariants are covered by automated tests and the scale profile.

## Future direction

As later stages create real world content, the same world-oriented inspection approach should gain useful layers and controls instead of being replaced by isolated stage dashboards.

Examples:

- ocean / land;
- continuous elevation;
- drainage / watersheds;
- rivers and lake levels;
- exact XYZ materialization;
- active/sleeping runtime water;
- simulation time controls when a mutable runtime world exists.

## Observer boundary

Camera, zoom and inspector controls are presentation only. They must not decide simulation activity, generation truth or physical fidelity.

Real user actions go through production command/domain paths; presentation does not mutate authoritative owners directly.

See [Stage 2 — Infinite-Time Foundation](../world-generation/stage2-infinite-time.md) and [Continuum Development Plan](../world-generation/continuum-development-plan.md).

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

## Current F2 screen — Stage 4

`F2` opens the **Stage 4 map / zoom proof** over a smooth deterministic synthetic Continuum field.

This is intentionally map-like rather than a page/cache dashboard. The synthetic field exists only to make pan, zoom, asynchronous refinement and fallback behavior visible; it is not geography.

Controls:

```text
Left mouse drag  move around the logical world
Mouse wheel      zoom around the cursor
Home             fit the whole world
G                toggle technical tile diagnostics
Esc              return to menu
```

By default technical tile borders are hidden.

With `G` enabled:

- green border = requested detail is ready;
- orange border = a coarser ready parent is temporarily filling that area;
- bounded CPU/GPU cache counts and async-job counts are shown.

The screen should not show blank holes while fine map tiles are being generated.

## Stage 4 presentation architecture

The map uses disposable derived tiles. Camera state decides only which representation is requested.

It does **not** decide:

- whether a region exists;
- what the Genesis world contains;
- whether simulation runs there;
- physical accuracy or simulation LOD.

CPU tile storage and render resources are independently bounded. Moving slightly should reuse overlapping tiles rather than regenerate the whole screen.

## Required world-inspector direction

Once real landscape appears, the main world inspector must grow into a practical successor to the useful parts of the old visualizer:

- **2D map view** for large-area inspection;
- **3D terrain view** for relief and vertical structure;
- free pan, zoom and navigation;
- clear display/settings controls rather than hidden debug hotkeys;
- switchable diagnostic layers appropriate to the current stage;
- coordinates and useful inspection information for the point/area being examined;
- later, simulation-time controls on the same real world view when mutable runtime state exists.

This is a presentation requirement only. 2D/3D mode, camera position, zoom and enabled layers must never change Genesis truth, simulation activity or physical accuracy.

The visualizer may evolve incrementally as new world data becomes available; we do not build fake landscape or premature controls merely to approximate the final UI before there is meaningful data to show.

## Future diagnostic layers

Examples as later stages create real world content:

- ocean / land;
- continuous elevation;
- slope / curvature;
- uplift / erosion / deposition;
- drainage / watersheds;
- depressions / spill points;
- rivers and lake levels;
- climate;
- geology / sediment / soil;
- exact XYZ materialization;
- active/sleeping runtime water;
- simulation time and runtime revisions.

## Observer boundary

Camera, zoom and inspector controls are presentation only. They must not decide simulation activity, generation truth or physical fidelity.

Real user actions go through production command/domain paths; presentation does not mutate authoritative owners directly.

See [Stage 4 — Map / Zoom Performance Proof](../world-generation/stage4-map-zoom.md) and [Continuum Development Plan](../world-generation/continuum-development-plan.md).

# Visualizer and Developer Inspection Tools

## In plain language

The Visualizer is EvoForge's **window into simulation and generated facts**. It may display Terrain, Water, agents, routes, Continuum representation and diagnostics, but it never decides what is physically true.

Camera position, drawing zoom, hidden layers and debug overlays are presentation only.

## Current status

There are two separate inspection paths:

- runtime scenarios built around ordinary production `SimulationRuntime` capabilities;
- the Continuum Inspector opened from the scenario menu with `F2`.

The retired V12–V15 generation preview remains removed.

## Continuum Inspector

The inspector reads production Continuum page/materialization/cache contracts. Its diagnostic scalar is explicitly **not geography**.

Current standard logical domain: 1,000,000 × 1,000,000.

It displays:

- technical page boundaries;
- requested 3×3 neighborhood;
- resident cache pages;
- pages evicted by the most recent move;
- focused page/world coordinate;
- seed;
- resident payload;
- cache hits/misses/loads/evictions;
- current Stage 3 sampling resolution.

### Two different kinds of zoom

They must not be confused.

**Presentation zoom** (`+`, `-`, mouse wheel) changes only pixels per drawn page. It does not change requested world truth.

**Sampling resolution** (`PageDown` coarser, `PageUp` finer) changes the bounded technical lattice used to read the same coordinate-addressed field:

```text
L0 step 1
L1 step 2
L2 step 4
...
```

Changing resolution preserves the logical focus coordinate. A coarse level covers more world space per page without materializing all exact cells under that page.

### Controls

```text
Arrows / WASD      move one page at current sampling level
Shift + move       move eight pages
PageDown           coarser sampling level
PageUp             finer sampling level
+ / - / wheel      presentation zoom only
Home               logical center
Esc                scenario menu
```

Critical law:

```text
presentation may change
requested representation may change
world truth may not
```

## Runtime observer boundary

`ZLevelVisualizer` reads simulation capabilities such as `SimulationView`, `SimulationTime`, `SimulationStepper` and presentation bindings. Real user actions go through explicit production command/domain paths; presentation does not mutate owners directly.

## Scenario model

Focused deterministic scenarios complement headless tests. They use ordinary production systems and never become alternative simulation truth.

## Water presentation

Water visuals read authoritative quantity/geometry. Optical depth, opacity and animation are presentation choices and cannot suppress hydraulic simulation work.

## Manual acceptance

Every visually meaningful generation stage requires automated correctness/determinism tests plus manual inspection through the visualizer. Performance is part of acceptance as well.

Stage 3 specifically requires checking that `PageDown/PageUp` changes level/step/page-world-span while the displayed logical focus stays fixed and cache work remains bounded.

## Invariants

- Visualizer never owns authoritative simulation/generation state.
- Camera/drawing zoom never changes semantics.
- Stage 3 sampling resolution is query representation, not simulation LOD.
- Continuum inspector consumes production Continuum contracts.
- Page/cache state remains technical representation only.
- Debug overlays never become hidden Navigation, Water, AI or generation truth.

## Current limitations

The Continuum inspector still uses a diagnostic scalar. Real geography overlays arrive only when their production stages exist.

## Code and tests

```text
core/.../visualizer/continuum/ContinuumInspectorModel.java
core/.../visualizer/screen/ContinuumInspectorScreen.java
core/.../visualizer/continuum/ContinuumInspectorModelTest.java
simulation/.../world/continuum/
```

See [World Generation](../world-generation/overview.md), [Continuum Technical Pages, Cache and Multi-Resolution Sampling](../world-generation/continuum-pages.md), [Continuum Development Plan](../world-generation/continuum-development-plan.md), and [ADR-024](../../decisions/024-continuum-large-world-architecture.md).

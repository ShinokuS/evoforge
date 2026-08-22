# Continuum Technical Pages, Cache and Multi-Resolution Sampling

## In plain language

A Continuum world can be enormous without keeping every coordinate in memory. A **page** is only a temporary rectangular batch of samples. A **resolution** says how far apart those samples are in world coordinates.

Neither concept is geography. Pages are not continents or regions, and resolution is not simulation fidelity.

## Current architecture

```text
coordinate-addressed scalar truth
        ↓
ContinuumMaterializer
        ↓
ContinuumPageLayout(resolution)
        ↓
bounded page windows
        ↓
ContinuumScalarPageCache
        ↓
observer-only Continuum Inspector
```

`ContinuumResolution` currently uses a nested power-of-two sampling lattice:

```text
L0 step 1
L1 step 2
L2 step 4
L3 step 8
...
```

This is a technical sampling hierarchy, not a statement that future terrain, climate or simulation must use these exact LOD levels.

## One world, several sampling scales

A coarse request does **not** generate a second world and does not generate exact detail first and downsample it.

For a deterministic coordinate-addressed field, a page at L10 directly asks for 256×256 samples with step 1024. It therefore covers roughly 262k × 262k world units while still materializing only 65,536 scalar values.

Because every coarse sample coordinate lies on the finer lattice, shared coordinates must return exactly the same value at every level.

```text
coarse coordinate (x,y)
        ==
that same exact world coordinate (x,y)
```

This nested-grid proof establishes the representation contract needed before real geographic structures exist. Later structural generators may add explicit parent constraints, but they must preserve the same law: refinement adds detail; it does not invent another reality.

## Resolution-aware page layout

`ContinuumPageLayout` accepts an optional `ContinuumResolution`. The original constructor remains exact L0.

At resolution `step`:

- page sample dimensions stay bounded;
- one page covers `pageSamples × step` world units per axis;
- page count falls as resolution becomes coarser;
- edge pages contain only the samples that lie inside the logical domain;
- payload bytes depend on sample count, not covered world area.

## Cache remains representation only

Each cache instance belongs to one page layout/resolution. Changing resolution in the inspector creates a fresh bounded representation cache while preserving the logical focus coordinate and authoritative field.

Eviction/rematerialization remains semantically invisible at coarse resolutions exactly as at L0.

## Performance proof

`ContinuumScaleResolutionProfileTest` runs in the existing `:simulation:scaleProfile` task.

On a 1,000,000 × 1,000,000 logical domain it materializes the same 256×256 page at L0, L5 and L10 and hard-checks that field work remains exactly 65,536 samples at every level even though covered world area grows dramatically.

This proves the important asymptotic rule for Stage 3:

```text
larger viewed world footprint
!=
exact-cell work proportional to footprint area
```

## Inspector

`F2` opens the Continuum inspector.

Controls:

```text
Arrows / WASD      move one page at current resolution
Shift + move       move eight pages
PageDown           coarser sampling level
PageUp             finer sampling level
+ / - / wheel      presentation zoom only
Home               logical center
Esc                scenario menu
```

The overlay displays:

- current resolution level;
- world-units-per-sample step;
- world span of one technical page;
- page counts at the current level;
- logical focus coordinate;
- requested/resident/evicted pages;
- cache hit/miss/load/eviction and payload metrics.

Changing sampling level preserves the same logical focus coordinate. Presentation zoom remains independent from sampling resolution.

## Verification

Headless tests prove:

- exact L0 behavior remains backwards compatible;
- coarse sample coordinates are nested/aligned;
- coarse and exact materializations agree at shared world coordinates;
- unrelated query order cannot change coarse results;
- coarse cache eviction/rematerialization is identical;
- page payload remains bounded while world span grows;
- the inspector preserves logical focus while switching levels;
- all resolution modes keep requested/resident work bounded.

## Current limitations

- scalar proof field only;
- nested sampling proves representation consistency, not real geographic parent constraints yet;
- one cache instance represents one resolution at a time;
- single-threaded cache ownership;
- no disk persistence;
- no geography overlay yet.

These are deliberate Stage 3 boundaries.

## Code and tests

```text
simulation/.../world/continuum/model/ContinuumResolution.java
simulation/.../world/continuum/page/ContinuumPageLayout.java
simulation/.../world/continuum/page/ContinuumScalarPageCache.java
simulation/.../world/continuum/ContinuumMultiResolutionTest.java
simulation/.../profile/ContinuumScaleResolutionProfileTest.java
core/.../visualizer/continuum/ContinuumInspectorModel.java
core/.../visualizer/screen/ContinuumInspectorScreen.java
core/.../visualizer/continuum/ContinuumInspectorModelTest.java
```

See [World Generation](overview.md), [Visualizer](../tooling/visualizer.md), [Continuum Development Plan](continuum-development-plan.md), and [ADR-024](../../decisions/024-continuum-large-world-architecture.md).

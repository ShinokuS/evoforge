# World Generation — Continuum

World generation is being rebuilt on the **Continuum** architecture. The retired dense V12–V15 generator and World Atlas pipeline are historical work, not the current architecture.

The canonical implementation plan is [Continuum World Development Plan](continuum-development-plan.md).

## Current position

The large-world foundation is now executable and inspectable:

- `ContinuumWorldDomain` represents large logical coordinates without whole-world allocation;
- coordinate-addressed deterministic fields are independent of request order;
- bounded pages and an explicit LRU page/byte cache keep residency independent of logical area;
- 10k / 100k / 1M scale profiles are automated in CI;
- eviction/rematerialization returns identical data;
- `F2` opens a bounded page/cache inspector;
- Stage 3 adds direct nested multi-resolution sampling without generating exact detail first.

Stage 3 is intentionally still **not geography**. It proves that the same logical world can be read at several scales while shared world coordinates remain identical.

## Stage 3 — Multi-Resolution Continuum

The current sampling hierarchy is technical and replaceable:

```text
L0: step 1
L1: step 2
L2: step 4
...
```

A page keeps a bounded number of samples while its covered world span grows with the selected level. A coarse query therefore asks the authoritative field directly for coarse lattice samples instead of materializing all exact cells and shrinking them afterward.

Critical invariant:

```text
same world coordinate + same authoritative inputs
=
same value regardless of which resolution/query path reached it
```

The inspector exposes resolution separately from presentation zoom. Camera zoom cannot change world truth; changing Stage 3 sampling resolution changes only which bounded representation is requested.

## Architectural laws

1. **Logical world size is not resident storage.** CPU/RAM follow requested/active work, not total world area.
2. **One continuous XYZ world, separate state owners.** Shared coordinates never imply one giant mutable `WorldCell`.
3. **Pages/chunks are technical only.** Natural geography may cross any number of paging boundaries.
4. **Camera is an observer.** Visibility and drawing zoom never change generation or simulation truth.
5. **Generation is deterministic and addressable.** Seed/version/semantic inputs/coordinates define generated facts; request order and thread order must not.
6. **Coarse representation is not another reality.** Refinement may add detail but must preserve higher-level constraints/shared facts.
7. **Genesis and runtime are separate.** Generation establishes initial facts; runtime owners control later mutation.
8. **Continuous/high-precision terrain precedes exact XYZ materialization.** Cell-scale `+1/-1 Z` noise is a quality failure for ordinary smooth terrain.
9. **Natural features are consequences of coherent processes.** Rivers, lakes, coasts and mountain systems are not independent decorative painters.
10. **Definitions remain semantic.** Authored controls express human meaning; solver constants stay inside implementations unless truly semantic.
11. **Replace modules, not generator versions.** Do not restart a V16/V17/V18 lineage.

## What Stage 3 does not do

Stage 3 introduces no continents, ocean, geology, mountains, rivers, lakes, climate or exact XYZ terrain. It establishes only the scale-aware query/materialization contract on the diagnostic proof field.

Real geographic stages must consume this foundation rather than invent their own full-world raster or camera-dependent LOD truth.

## Verification

Stage 3 acceptance requires:

- same shared-coordinate values across resolutions;
- order-independent requests;
- bounded coarse materialization work;
- coarse cache eviction/rematerialization equality;
- 1M logical-domain scale profile;
- visual inspection through the same production page/materializer contracts.

See [Continuum Technical Pages, Cache and Multi-Resolution Sampling](continuum-pages.md).

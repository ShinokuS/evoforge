# World Generation — Continuum

World generation is being rebuilt on the **Continuum** architecture. The retired dense V12–V15 generator, World Atlas pipeline and its terrain/mountain/lake/bathymetry runtime layers have been removed from production. They are historical work, not the current architecture.

The canonical implementation plan is [Continuum World Development Plan](continuum-development-plan.md).

## Current position

The cleanup/foundation milestone is complete enough to start the first executable Continuum phase:

- `ContinuumWorldDomain` represents a large logical address space using `long` coordinates without allocating the whole map;
- deterministic addressable sampling is independent of incidental request order;
- `ContinuumMaterializer` materializes only the requested bounded window;
- foundation tests prove that overlapping requests agree on shared coordinates;
- legacy runtime worldgen implementations and stale regression tests are gone.

This is **not** yet completion of Phase 0 from the master plan. Phase 0 additionally requires bounded paging/cache behavior, explicit 10k/100k/1M scale evidence, eviction/rematerialization proof, metrics and a navigable Continuum preview.

Current code entry points:

```text
simulation/src/main/java/io/github/evoforge/simulation/world/continuum/
simulation/src/test/java/io/github/evoforge/simulation/world/continuum/
```

## Architectural laws

1. **Logical world size is not resident storage.** CPU/RAM must follow requested/active work, not total world area.
2. **One continuous XYZ world, separate state owners.** Shared coordinates never imply one giant mutable `WorldCell`.
3. **Pages/chunks are technical only.** Natural geography may cross any number of paging boundaries.
4. **Camera is an observer.** Visibility, zoom and cache residency never change authoritative generation or simulation truth.
5. **Generation is deterministic and addressable.** Seed/version/semantic inputs/coordinates define generated facts; request order and thread order must not.
6. **Genesis and runtime are separate.** Generation establishes initial facts; ordinary runtime owners control later mutation.
7. **Continuous/high-precision terrain precedes exact XYZ materialization.** Cell-scale `+1/-1 Z` noise is a quality failure for ordinary smooth terrain.
8. **Natural features are consequences of coherent processes.** Rivers, lakes, coasts and mountain systems are not independent decorative painters.
9. **Definitions remain semantic.** Authored controls express human meaning with normalized ranges; solver constants stay in implementations unless they are true content semantics.
10. **Replace modules, not generator versions.** Do not restart a V16/V17/V18 lineage.

## Immediate next work — Continuum Phase 0

Build the large-world proof before introducing real geography:

- logical domains at 10k, 100k and 1M dimensions;
- bounded page/window materialization;
- explicit cache page/byte budget;
- deterministic cache eviction and reload;
- cache hit/miss/load/eviction/resident-memory metrics;
- proof that resident memory is independent of total logical world area for the same active window;
- a Continuum preview that can pan and zoom without generating the whole world;
- page/cache diagnostics in the visualizer.

Only after this proof is green should structural geography begin.

## Documentation status

The old V12–V15 normative system pages were removed together with their production owners. Historical reasoning may remain under `docs/journal/`, but it cannot override this page, the master plan, current code or executable tests.

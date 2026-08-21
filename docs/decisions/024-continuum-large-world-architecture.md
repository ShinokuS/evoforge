# 024 — Continuum large-world architecture

## Status

Proposed for the new large-world foundation. This decision deliberately precedes production implementation.

## Context

The first World Atlas generation line began with `fb58a43a6a6e9bd69b834c65c3b28697900b70f0`, immediately after the Genesis-only foundation `37e6e43c0461765e860f643ad6655b3cd5941aa6`.

That first Atlas made an important promise: `ElevationField` was a semantic read contract and its dense bounded array was only a hidden prototype representation. Later development preserved the read boundary, but generation increasingly materialized full-world rasters and accumulated compatibility revisions (`V1` … `V15`). The accepted 10,000 × 10,000 milestone proved that this design can be optimized far beyond its original scale, but it also exposed the architectural ceiling: resident memory, generation time and temporary working sets still grow primarily with world area, before runtime objects and deeper Z-space are considered.

The next foundation must make logical world size independent from permanently materialized cell state while preserving EvoForge's stronger laws:

- one authoritative owner per mutable fact;
- deterministic replay from explicit authoritative inputs;
- presentation and camera visibility never select simulation truth;
- sleeping/analytical progression is valid only when it preserves domain semantics;
- algorithms remain replaceable behind narrow typed seams;
- generation creates initial facts; runtime domains own subsequent mutation.

The historical clean conceptual reference is therefore `37e6e43c`: Genesis + stateless addressable randomness, before the first Atlas/elevation implementation. Development does **not** rewind the repository to that commit. New work branches from current `develop` so accepted movement, Water, Geometry, agent, scheduler and visualizer systems remain available. The old generator is treated as legacy implementation during migration, not as the design template for the new pipeline.

## Decision

Adopt **Continuum** as the large-world architecture.

A world is not defined by a permanently resident `width × height × z` cell array. Its authoritative state is composed from compact deterministic source facts plus sparse lived mutation:

```text
WorldGenesis
    +
immutable structural world facts
    +
sparse runtime mutations
    +
domain-owned simulation state
    =
authoritative world
```

Detailed cells, raster tiles, meshes, debug images and other local representations are materializations of that truth. They may be cached, evicted and rebuilt. Their residency must never define whether a location, feature or entity exists.

## 1. Logical space is not resident storage

`WorldBounds` describes the legal address space. It must not imply allocation proportional to every possible coordinate.

A logical world may be 10,000², 100,000², 1,000,000² or larger while only a bounded working set is resident. Increasing legal Z depth must likewise not require allocation of every `(x,y,z)` cell.

Production large-world code must not allocate `O(worldWidth × worldHeight)` or `O(worldWidth × worldHeight × worldDepth)` storage merely because those coordinates exist.

Exceptions require an explicit bounded materialization request or an explicitly documented streaming/out-of-core algorithm whose peak resident workspace is bounded independently from total logical world area.

## 2. Structural facts precede raster detail

Large-scale geography is authored as causal structural facts rather than as a finished full-resolution height raster.

Expected fact owners include independently meaningful concepts such as:

```text
continental / oceanic structure
geological provinces and boundaries
uplift / subsidence structure
mountain systems and ridge networks
drainage and watershed topology
depression / basin hierarchy
river and lake features
continuous terrain surface
bathymetric structure
subsurface geology / cave structure
```

This list is a development direction, not a mandatory god object. A fact is introduced only when a real stage and consumer require it.

A mountain is therefore not fundamentally a bright patch in a height array. A lake is not fundamentally a painted negative-Z mask. A river is not fundamentally a thin raster line. Those raster/cell representations are derived views of structural features.

## 3. Generated facts expose queries, not storage

Each domain exposes the narrowest semantic read contract its consumers require.

Examples of valid shapes include:

```text
SurfaceElevation.elevationAt(x, y)
TerrainRegionSource.materialize(request)
RidgeNetwork.query(area, minimumScale)
DrainageTopology.receiverOf(feature)
BasinHierarchy.query(area, minimumScale)
```

The exact names are decided by concrete consumers. There is no universal `WorldQuery`, `WorldGenerationContext`, `Map<Class<?>, Object>` or generic service locator.

Representations may be analytic fields, graphs, hierarchical spatial indexes, compressed structures, paged data or bounded rasters. Consumers cannot depend on the representation.

## 4. Materialization is disposable representation

When exact local cells are required, a domain materializer converts structural facts into a bounded requested representation.

```text
structural facts
      ↓
bounded request
      ↓
materializer
      ↓
materialized page / region
      ↓
optional bounded cache
```

A materialized page is not a second authoritative world. If it contains only immutable generated information, it must be reproducible bit-for-bit from its authoritative source inputs. Eviction followed by rematerialization must preserve semantic results.

Runtime changes are overlaid through domain-owned sparse deltas/state rather than by silently modifying the immutable generated source.

## 5. Residency never changes semantics

Loaded/unloaded, hot/cold, cached/evicted and visible/hidden are performance states only.

They may influence:

- whether a derived page is cached;
- which LOD representation is rendered;
- which immutable data page is resident;
- when asynchronous preparation is requested.

They must not influence:

- whether an object exists;
- which physical rules apply;
- whether a process advances;
- the authoritative result of a domain action;
- random outcomes;
- generated geography.

Camera movement and observation must be semantically invisible to the simulation.

## 6. Time is virtualized by causality, not by distance

Large-world simulation must avoid `for every entity every render/tick` work when no meaningful state transition occurs.

Domain mechanics may use:

- scheduled next transitions;
- exact analytical progression between transitions;
- event invalidation/rescheduling when an input changes;
- compact primitive/paged owner storage.

This is allowed only when it preserves the same authoritative domain semantics. There is no separate simplified distant simulation model.

An entity on the opposite side of the map follows the same laws as an observed entity. The camera does not activate or deactivate its life.

## 7. Z-space is sparse by construction

A surface world must not be represented as a solid dense `XYZ` volume.

The expected direction is layered and sparse representation, for example:

```text
surface / column facts
      +
implicit or interval subsurface structure
      +
sparse 3D bricks/pages where detailed topology exists
      +
runtime deltas for excavation/construction
```

Caves and other volumetric features should preferably have compact structural representations (graphs, implicit passages, strata/regions) and materialize detailed cells only where a consumer requires them.

## 8. Multi-resolution semantics are feature-aware

Global overview must not be produced by blindly averaging exact cells until important geography disappears.

A coarse query may use fewer terrain samples, but important structural features — major coasts, mountain systems, ridges, rivers and lakes — remain queryable at their meaningful scale.

The visualizer may choose a representation appropriate to the viewport, but all representations are projections of the same generated facts.

This is required both for quality and for correctness of developer acceptance: a 100,000-wide world must be inspectable without first materializing billions of cells.

## 9. Continuous terrain precedes discrete Z materialization

Large-scale terrain algorithms operate on a continuous or sufficiently precise fixed-point surface. Integer Z cells are a materialization representation, not the primary geomorphic model.

The new terrain path must explicitly prevent cell-scale quantization noise. In particular, a small-wavelength feature must not be allowed arbitrary large Z amplitude merely because a noise function produced it.

Terrain quality invariants will include measurable constraints for:

- isolated one-cell Z bands;
- one-cell terraces/layers;
- contour/support width;
- slope continuity;
- ridge/valley continuity;
- multi-resolution consistency.

Visual acceptance remains required because natural morphology cannot be reduced to one scalar metric.

## 10. Algorithms are composition, not versions

The new Continuum pipeline does **not** use a linear `V16 -> V17 -> V18` implementation lineage.

Each semantic fact has an independently replaceable algorithm boundary only where replacement is meaningful. Composition selects implementations explicitly.

Example:

```text
ContinentalStructureGenerator
        ↓
ContinentalStructure
        ↓
GeologicalStructureGenerator
        ↓
GeologicalStructure
        ↓
OrographyGenerator
        ↓
OrographicStructure
```

A future alternate `OrographyGenerator` replaces only the orography implementation. It does not require creating a new class that copies every previous stage or adding branches to unrelated consumers.

Adding a new real fact introduces its own typed owner and explicit dependency edges. It does not require editing a universal stage enum or central implementation switch.

## 11. Compatibility is provenance, not branching code everywhere

Persistence eventually needs to know how immutable generated source facts were authored. That requirement does not justify a revision switch inside every algorithm.

Continuum separates:

- semantic authored world intent;
- selected algorithm composition and immutable parameters;
- deterministic random contract;
- persisted/generated facts that must survive runtime evolution.

If reproducibility of a historical world requires preserving an old composition, compatibility belongs at the save/composition/provenance boundary or through retained immutable facts — not through a growing `switch (GenerationRevision)` inside new domain algorithms.

The first Continuum slices do not create a speculative compatibility framework. They record enough stable provenance to make future persistence decisions possible when the save format becomes a real consumer.

## 12. Determinism is addressable and order-independent

The useful part of the Genesis foundation is retained: random-looking generation is addressed by stable semantic identity rather than by one mutable RNG stream.

New algorithms must prove where applicable that:

- querying A then B equals querying B then A;
- tiled materialization equals equivalent untiled materialization;
- adjacent tiles agree exactly at shared semantic boundaries;
- cache eviction/rematerialization does not alter results;
- rendering/query order does not affect generated facts.

Random-purpose scopes belong to the algorithm that owns the random decision. Unrelated algorithms do not share mutable random state.

## 13. Performance is an architectural invariant

Performance is measured from the first executable slice, not after the world looks finished.

Every large-world PR that introduces spatial state or materialization must report relevant values such as:

- logical world dimensions;
- initialization/generation time;
- cold and warm region-materialization latency;
- peak resident memory;
- allocation traffic;
- resident cache size;
- number/size of structural features;
- deterministic checksum or equivalent semantic fingerprint.

The decisive scaling test is not merely “10k fits”. It is:

> Increasing logical coordinate space alone must not force resident terrain memory to grow quadratically with area or linearly with unused Z depth.

A bounded page cache receives an explicit memory budget. Cache size, not world size, owns resident detailed-terrain memory.

## 14. Visualization is first-class observability

Any new spatial fact that can be meaningfully shown should receive a presentation/debug adapter in the same accepted slice or the earliest slice where the fact is visually interpretable.

Expected overlays include, as they become real:

- structural regions and feature IDs;
- geological boundaries;
- uplift/subsidence;
- ridge networks and mountain systems;
- watersheds/drainage;
- basin hierarchy and spill points;
- lakes and river networks;
- continuous elevation/slope/curvature;
- Z quantization diagnostics;
- materialized page boundaries and residency;
- sparse deltas;
- cave/subsurface structures.

Presentation reads generated facts through capabilities and does not duplicate generation logic.

## 15. Development is green-checkpoint and replacement-safe

Continuum development follows a stronger form of the existing green-checkpoint discipline.

Every independently meaningful block is introduced in this order:

```text
semantic contract/fact
        ↓
small implementation
        ↓
headless correctness/property tests
        ↓
determinism/substitution tests
        ↓
performance evidence
        ↓
visual diagnostics when spatial
        ↓
manual acceptance when appearance is semantic
        ↓
merge
```

A PR should be small enough that one failed visual/performance hypothesis can be replaced without rewriting unrelated accepted blocks.

Abstraction is required at real semantic seams, not around every helper. Internal mathematics remains simple and concrete until another real implementation/consumer proves a stable seam.

## 16. Migration from the legacy generator

The accepted legacy V15 generator remains temporarily available only so existing scenarios/tools can run while Continuum is built.

New Continuum production packages must not depend on legacy V-numbered generation classes. Legacy may be exposed through a temporary composition adapter if an integration test needs coexistence, but dependency direction is one-way:

```text
existing application/composition
     ├── legacy generator (temporary)
     └── Continuum foundation (new)

Continuum domain code ─X→ legacy V* implementation
```

Cutover occurs only after the new path has independently passed quality, scale, materialization and runtime bootstrap gates. The old generator/revision routing is then deleted rather than indefinitely maintained beside the new path.

## Consequences

Positive:

- logical world size can grow without forcing full-world materialization;
- 100k+ worlds become a design target rather than an exceptional benchmark;
- runtime Z expansion no longer implies dense volumetric memory;
- geography can be causal and feature-aware at continental and local scales;
- generator algorithms can evolve independently instead of accumulating revision subclasses/switches;
- visualizer and simulation query one authoritative world model at different requested representations;
- performance regressions become architectural test failures rather than late optimization tasks.

Costs:

- structural geography and materialization contracts require more deliberate modeling than one height raster;
- some global analyses require streaming/out-of-core or hierarchical algorithms;
- deterministic page boundaries and multi-resolution consistency must be tested explicitly;
- persistence must eventually distinguish immutable generated source facts from sparse runtime mutation;
- migration temporarily keeps a legacy generator beside the new foundation.

These costs are intentional. They buy scale without weakening simulation truth.

## Rejected directions

### Keep optimizing full-world rasters

Rejected as the long-term architecture. The 10k milestone proves feasibility at that scale but memory/time still grow with total area and become untenable once large Z, objects and simulation state are added.

### Active chunks use full simulation; distant chunks use simplified simulation

Rejected because camera/player distance would select different authoritative laws.

### Dense voxel world

Rejected because unused Z depth would dominate memory and persistence.

### One universal ECS/world-generation framework

Rejected because EvoForge facts have explicit domain owners and real semantic dependencies should remain visible.

### One global mutable generation context

Rejected because it hides causal dependencies and makes independent replacement difficult.

### Continue numeric generation revisions as the primary development model

Rejected because implementation history becomes architecture. Continuum evolves by semantic composition and replacement of independent owners.

### Renderer-driven generation truth

Rejected because observation must never define existence or generation semantics.

## Historical reference

The historical boundary used for this reset is:

```text
37e6e43c0461765e860f643ad6655b3cd5941aa6
    Genesis + stateless RNG only

fb58a43a6a6e9bd69b834c65c3b28697900b70f0
    first World Atlas + dense elevation implementation
```

The first commit is a conceptual reference, not the branch base. Continuum development starts from current `develop` and preserves all accepted non-generator systems.

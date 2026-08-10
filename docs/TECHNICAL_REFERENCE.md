# EvoForge Technical Reference

This file describes the current implementation. It may change after ordinary pull requests without changing the semantic architecture in `ARCHITECTURE.md`.

Baseline: Java 21, libGDX presentation modules plus a pure-Java `simulation` module.

## 1. Modules

```text
core/        libGDX application layer
lwjgl3/      desktop launcher
simulation/  deterministic simulation/domain code without libGDX
assets/      definitions and presentation assets
```

The simulation module is the authoritative architecture target. Presentation must not become the owner of simulation state.

## 2. Implemented simulation areas

```text
io.github.evoforge.simulation
├── definition/
├── time/
└── world/
    ├── World
    ├── object/
    │   └── definition/
    ├── spatial/
    │   └── indexes/
    ├── landscape/
    │   ├── definition/
    │   └── terrain/
    │       └── storage/
    ├── mechanics/
    │   ├── physical/
    │   └── geometry/
    └── navigation/
```

Future packages are not created merely to reserve names.

## 3. Objects and identity

Implemented foundation includes:

- `ObjectId` with slot/generation identity semantics;
- `WorldObject` as a domain object;
- `ObjectRepository` for existence/identity;
- read-only object lookup;
- object creation infrastructure;
- object definitions compiled separately from mutable runtime state.

ObjectRepository is not used as a generic bag of mechanics.

## 4. Definitions

Definitions are composition-driven and compiled during bootstrap.

Current conventions:

- source keys use stable string form such as `namespace:name`;
- runtime systems use typed ids;
- runtime ids are not persistence identity;
- loaders resolve definitions in deterministic startup flow;
- mechanics own their own compiled definition data;
- adding content that uses existing mechanics should normally require data only.

Current assets include separate roots for object and landscape definitions.

## 5. Time and scheduling

Implemented:

- `SimulationClock`;
- Scheduler foundation.

Scheduler is infrastructure for activation/time ordering. Domain mechanics do not become scheduler task types in a central enum.

## 6. Object spatial system

Implemented discrete XYZ object positioning:

- `TransformState`;
- `TransformLookup`;
- `SpatialSystem`;
- `ObjectSpatialIndex`;
- `CellSpatialIndex`.

Spatial stores positions for WorldObjects only. Terrain does not enter CellSpatialIndex.

## 7. Landscape terrain

Core representation:

```text
XYZ -> LandscapeDefinitionId | absence
```

Implemented:

- `LandscapeDefinitionId`;
- `TerrainSystem`;
- `TerrainLookup`;
- `TerrainStorage` boundary;
- current `SparseTerrainStorage`.

`TerrainLookup.find(x,y,z)` returns null for absent terrain. `contains` is derived from that lookup.

The current sparse storage is an implementation, not a final chunk model.

## 8. Geometry

Package:

```text
world/mechanics/geometry/
```

Implemented:

- `Shape`;
- `FullShape`;
- `GeometryLookup`;
- `GeometryState`;
- `GeometrySystem`;
- `TransitionMask`;
- `TransitionPorts`;
- `TransitionComposition`.

### 8.1 Geometry ownership

`GeometrySystem` reads `TerrainLookup`.

For absent terrain:

```text
GeometryLookup.find(XYZ) -> null
```

For present terrain without override:

```text
GeometryLookup.find(XYZ) -> FullShape.INSTANCE
```

Only non-default Shape overrides are stored in GeometryState.

### 8.2 Shape API

Current public Shape contract:

```java
long transitionPorts(
        int relativeX,
        int relativeY,
        int relativeZ);

int transitionBlocks(
        int relativeX,
        int relativeY,
        int relativeZ);
```

`transitionBlocks` defaults to no blocks.

The relative coordinates describe the current navigation source position relative to the Shape terrain anchor.

### 8.3 TransitionMask

A structural step is one of the 26 non-center offsets in a `3x3x3` neighborhood.

`TransitionMask` maps those offsets to bits in an `int`. The center bit is excluded.

Important operations are primitive and allocation-free:

```text
TransitionMask.of(dx,dy,dz)
TransitionMask.contains(mask,dx,dy,dz)
```

### 8.4 TransitionPorts

Departure and arrival masks are packed into one `long` in two non-overlapping 27-bit regions.

Helpers expose:

```text
of(departures, arrivals)
departuresOnly(mask)
arrivalsOnly(mask)
departures(ports)
arrivals(ports)
```

### 8.5 Composition

Current structural composition:

```text
resolved = departures & arrivals & ~blocks
```

Navigation OR-accumulates contributions from all relevant Shape instances before calling `TransitionComposition.resolve`.

### 8.6 FullShape behavior

`FullShape.INSTANCE` is the default shape for present terrain.

Current behavior includes:

- eight horizontal departures from the space directly above the Full anchor;
- arrivals into the top standing position from neighboring top-plane source positions;
- horizontal side/corner blocking for occupied Full volume;
- direct vertical blocking when a transition would enter the Full volume from immediately below or above.

The implementation intentionally does not claim a universal continuous line-intersection model.

### 8.7 Shape extension validation

The Shape contract has been tested with a test-only ramp-like probe that models:

```text
lower ground -> internal lower position -> internal upper position -> upper ground
```

The probe uses the same public Shape contract and required no type-aware change in Navigation.

This validation exposed the missing direct vertical blocking case in FullShape, which was then fixed locally in FullShape.

There is still no production `RampShape` or orientation/content model.

## 9. Navigation

Package:

```text
world/navigation/
```

Implemented:

- `NavigationLookup`;
- `NavigationSystem`.

Public read boundary:

```java
int transitions(
        int x,
        int y,
        int z);
```

### 9.1 Resolver

For one source XYZ, Navigation examines at most the local `3x3x3` neighborhood.

For every Shape found there:

```text
relative source = source XYZ - Shape anchor XYZ
ports  |= shape.transitionPorts(relative source)
blocks |= shape.transitionBlocks(relative source)
```

Then:

```text
TransitionComposition.resolve(ports, blocks)
```

No concrete Shape type appears in Navigation logic.

### 9.2 Directed topology

Navigation edges are directed. A forward transition does not generate its reverse automatically.

A dedicated contract test fixes this behavior.

### 9.3 Current cache status

There is **no persistent Navigation cache** in the current implementation.

An earlier primitive open-addressing cache prototype was removed during architecture review because there was no Movement/Pathfinder workload yet to justify its representation, lifecycle or memory policy.

Current topology queries always see current Geometry on the next call and therefore require no manual Navigation invalidation.

Caching may return later only after representative workload measurement.

## 10. Navigation testing

Current test coverage includes:

- null dependency and stable lookup;
- no geometry -> no transitions;
- generic Shape composition without type knowledge;
- flat Full neighborhood -> eight horizontal transitions;
- missing destination support;
- side blocking and corner crossing;
- local `3x3x3` access only;
- local arithmetic protection against coordinate wrap at implementation boundaries;
- Terrain -> Geometry -> Navigation integration;
- terrain removal visible on the next query;
- geometry override visible on the next query;
- directed edge contract;
- seeded randomized comparison against a deliberately simpler reference resolver.

The randomized reference test mutates a small synthetic geometry world and compares production Navigation with independent explicit direction-by-direction resolution. Failure messages include the reproducible seed and mutation step.

## 11. Coordinate implementation note

Public coordinates currently use signed `int`.

Tests at `Integer.MIN_VALUE`/`Integer.MAX_VALUE` protect local arithmetic from accidental wrap in the current resolver. They do **not** define the valid dimensions of an EvoForge world.

World bounds and any packed internal coordinate key remain undecided.

## 12. Current known gaps

### Geometry override lifecycle

A non-default Geometry override may remain in GeometryState if terrain is removed and later placed again at the same XYZ. The policy for remove/re-place is not yet owned by a lifecycle/orchestration boundary.

Do not solve this by introducing a reverse TerrainSystem -> GeometrySystem dependency.

### Unloaded versus absent terrain

Current read contracts represent terrain absence with null. A future chunk/region model must distinguish true absence from not-loaded/not-generated state if those concepts exist.

### Diagnostics

`NavigationLookup.transitions` intentionally returns only a primitive mask. It does not explain why a direction is absent.

A future diagnostic/Inspector path should be able to expose departures, arrivals, blocks and contributing geometry without changing the hot read API.

### Movement and costs

Navigation currently represents structural topology only. Actor capabilities, occupancy, movement duration and path cost are not implemented.

### Caching

No cache policy is selected. Future profiling should decide whether topology reuse is best represented by no cache, chunk-local arrays, bounded maps or another derived structure.

## 13. Determinism work to enforce as systems appear

The stable architecture requires:

- explicit simulation RNG seed/state for authoritative randomness;
- stable tie-break ordering;
- no authoritative dependence on HashMap/HashSet iteration order;
- validation of background results before authoritative application.

There is not yet a general RNG service because no current mechanic requires authoritative randomness. It should be introduced with the first real random consumer rather than as unused infrastructure.

## 14. Current roadmap

```text
DONE  Object/Definition/Scheduler/Spatial foundation
DONE  Landscape terrain core
DONE  Geometry foundation and transition algebra
DONE  Local Navigation resolver
DONE  Shape contract validation with nontrivial topology probe
NOW   Architecture/test hardening after external review
NEXT  Control Backbone
LATER Scenario Harness -> Basic Movement -> Occupancy -> Pathfinder -> first agent vertical slice
```

Before Control Backbone begins, the architecture hardening pass should leave:

- stable directed Navigation semantics;
- reproducible randomized/reference tests;
- clear coordinate representation versus world-bounds contract;
- explicit determinism rules;
- a working scale envelope;
- architecture and technical documentation separated.

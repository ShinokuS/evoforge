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

`ObjectRepository` is not used as a generic bag of mechanics.

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

Spatial stores positions for WorldObjects only. Terrain does not enter `CellSpatialIndex`.

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

`TerrainLookup.find(x,y,z)` returns `null` for absent terrain. `contains` is derived from that lookup.

The current sparse storage is an implementation, not a final chunk model.

## 8. Geometry

Package:

```text
world/mechanics/geometry/
```

Implemented:

- `Shape`;
- `FullShape`;
- `RampShape`;
- `GeometryLookup`;
- `GeometryState`;
- `GeometrySystem`;
- `TransitionMask`;
- `TransitionPorts`;
- `TransitionComposition`;
- package-private `SolidCellBlocking` shared by solid terrain Shapes.

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

Only non-default Shape overrides are stored in `GeometryState`.

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

Relative coordinates describe the current Navigation source position relative to the Shape terrain coordinate.

Shape has no world lookup and receives no neighboring Shape information.

The current production structural Shapes (`FullShape` and all four primitive `RampShape` orientations) expose one supported navigation position at `anchor + (0,0,1)`. Their role convention is:

```text
departures -> originate from that supported position
arrivals   -> only confirm transitions ending at that supported position
```

This is tested as the current structural Shape role contract. It is not intended to forbid a future Shape with a genuinely different supported-position model; such a Shape would require an explicit contract/reviewer decision rather than a type-specific Navigation exception.

### 8.3 TransitionMask

A structural step is one of the 26 non-center offsets in a `3x3x3` direction neighborhood.

`TransitionMask` maps those offsets to bits in an `int`; the center bit is excluded from `ALL`.

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

`resolve` additionally masks the result by `TransitionMask.ALL`, so malformed raw port values cannot expose the center bit or any non-neighbor bit through the public Navigation mask.

For an external edge, one Shape can provide the departure and another independently provide the arrival. If either contribution is absent, the edge does not exist. No Shape asks another Shape whether that neighbor exists.

### 8.6 Solid-cell blocking

`FullShape` and `RampShape` both represent occupied solid terrain coordinates. Their common blocking behavior is implemented by package-private `SolidCellBlocking`.

The helper prevents Navigation from treating the occupied terrain coordinate as ordinary traversable space and blocks direct transitions into the solid cell from its local neighborhood. Keeping this rule in Geometry lets concrete solid Shapes reuse it without teaching Navigation about concrete Shape types.

### 8.7 FullShape behavior

`FullShape.INSTANCE` is the default shape for present terrain.

Current behavior includes:

- eight horizontal departure candidates from the supported position directly above the Full coordinate;
- four cardinal `dz=+1` departure candidates used only when another Shape supplies the matching arrival (for example the lower side of a Ramp);
- arrivals into the supported top position from neighboring same-level source positions;
- cardinal downward arrivals from a neighboring position one level higher, needed to independently confirm descent back onto the Full-supported position;
- strict same-level side/corner blocking;
- direct blocking of any one-step transition whose destination would enter the occupied Full coordinate, including vertical and diagonal-vertical entry.

The extra cardinal-up departure candidates do **not** create free Full-to-Full stairs: in a flat Full-only world no matching arrival exists for those candidate edges, so the resolved topology remains exactly eight horizontal transitions.

The implementation intentionally does not claim a universal continuous line-intersection model.

### 8.8 RampShape behavior

`RampShape` is the first production Shape that changes Z through ordinary structural Navigation.

It has four shared immutable orientations:

```text
RampShape.POSITIVE_X
RampShape.NEGATIVE_X
RampShape.POSITIVE_Y
RampShape.NEGATIVE_Y
```

The sign indicates the direction in which the ramp rises.

The first production model is intentionally primitive: the ramp is a solid terrain block with a linear bidirectional structural passage along one cardinal axis. It has no side entry and no XY-diagonal entry.

For `POSITIVE_Y` with terrain coordinate `(0,1,0)`:

```text
lower supported position = (0,0,0)
ramp supported position  = (0,1,1)
upper supported position = (0,2,1)
```

When the corresponding neighboring Shapes exist, the resolved structural edges are:

```text
(0,0,0) <-> (0,1,1) <-> (0,2,1)
```

Entering from the lower side changes both Y and Z in one immediate-neighbor transition:

```text
lower -> ramp = (0,+1,+1)
ramp -> lower = (0,-1,-1)
```

The upper connection is horizontal at the raised level:

```text
ramp -> upper = (0,+1,0)
upper -> ramp = (0,-1,0)
```

`POSITIVE_X`, `NEGATIVE_X` and `NEGATIVE_Y` are rotations/sign reversals of the same topology.

Ramp does not own or assert the existence of either neighboring surface. Its ports are role-separated:

- Ramp arrivals confirm valid entry onto its own supported position;
- Ramp departures describe valid exits from its own supported position;
- the neighboring Shape must independently provide the other role for an external edge.

Therefore removing the upper platform removes the upper connection, and removing the lower supporting Shape removes both ascent from that side and descent into the missing lower position. Navigation does not reinterpret either case as falling.

A Ramp can also offer a cardinal `dz=+1` departure toward the next Ramp. A directly consecutive Ramp supplies the matching arrival, allowing a continuous multi-level slope without an artificial Full cell between the ramps. If the next Shape is instead a raised Full platform, only the horizontal upper connection is resolved.

Ramp uses `SolidCellBlocking`, so its terrain anchor is not itself a navigation position and cannot be entered through the solid body.

No generic orientation framework, fractional height model, continuous slope geometry or side movement has been introduced.

### 8.9 Shape extension result

The important extension property still holds:

```text
new Shape implementation
    -> existing Shape contract
    -> existing Transition algebra
    -> generic NavigationSystem
```

Production Ramp required a generic refinement of the resolver's local Geometry read envelope, but no concrete Shape type appears in Navigation and no Ramp-specific branch was added there.

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

Structural transition directions remain the 26 immediate neighbors:

```text
dx, dy, dz in [-1,1]
excluding (0,0,0)
```

For one source XYZ, Navigation currently examines Geometry in the source-relative read envelope:

```text
dx in [-1,1]
dy in [-1,1]
dz in [-2,1]
```

This is at most 36 Geometry lookups.

The additional lower Z layer is not a longer movement edge. It is required so that for a one-step transition with `dz=-1`, the Shape whose terrain anchor supports the destination position can still contribute the matching arrival. Under the current structural Shape model, a supported position is one cell above its terrain anchor; therefore a destination one Z below the source can be supported by an anchor two Z below the source.

For every Shape found in the read envelope:

```text
relative source = source XYZ - Shape terrain coordinate
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

Bidirectional Full and Ramp behavior comes from the required roles being independently present for both directed edges.

### 9.3 Current cache status

There is **no persistent Navigation cache** in the current implementation.

An earlier primitive open-addressing cache prototype was removed during architecture review because there was no Movement/Pathfinder workload yet to justify its representation, lifecycle or memory policy.

Current topology queries always see current Geometry on the next call and therefore require no manual Navigation invalidation.

Caching may return later only after representative workload measurement.

## 10. Navigation and geometry testing

Current coverage includes:

- null dependency and stable lookup;
- no geometry -> no transitions;
- generic Shape composition without type knowledge;
- flat Full neighborhood -> exactly eight resolved horizontal transitions;
- missing flat destination support;
- strict side blocking and corner crossing;
- direct Full blocking for vertical and diagonal-vertical entry;
- local transition-and-lower-support read envelope (`dx/dy [-1,1]`, `dz [-2,1]`, at most 36 lookups);
- local arithmetic protection against coordinate wrap at implementation boundaries;
- Terrain -> Geometry -> Navigation integration;
- terrain removal visible on the next query;
- geometry override visible on the next query;
- directed edge contract;
- `RampShape` topology for all four orientations;
- no side or XY-diagonal Ramp entry;
- Ramp solid terrain coordinate is not navigable;
- missing upper Shape -> no upper Ramp connection;
- missing lower Shape -> neither ascent onto Ramp nor descent into the missing lower position;
- real lower -> ramp -> upper traversal through Geometry + Navigation;
- reverse Ramp traversal;
- directly consecutive ramps connecting successive Z levels;
- Full blocking of a Ramp ascent when the destination terrain cell is occupied;
- production structural Shape role-contract sweep for `FullShape` and all four Ramp orientations;
- occupied-terrain navigation sweep in the Ramp hardening scenario;
- center-bit sanitization in `TransitionComposition`;
- seeded randomized comparison against a deliberately simpler reference resolver.

The randomized reference test samples:

- synthetic table-driven Shapes;
- `FullShape.INSTANCE`;
- all four production `RampShape` instances.

Its mutation radius extends beyond Navigation locality, so distant changes are also checked for non-influence. Failure messages include reproducible seed, mutation step and source XYZ.

## 11. Coordinate implementation note

Public coordinates currently use signed `int`.

Tests at `Integer.MIN_VALUE`/`Integer.MAX_VALUE` protect local arithmetic from accidental wrap in the current resolver. They do **not** define the valid dimensions of an EvoForge world.

World bounds and any packed internal coordinate key remain undecided.

## 12. Current known gaps

### Geometry override lifecycle

A non-default Geometry override may remain in `GeometryState` if terrain is removed and later placed again at the same XYZ. The policy for remove/re-place is not yet owned by a lifecycle/orchestration boundary.

Do not solve this by introducing a reverse `TerrainSystem -> GeometrySystem` dependency.

### Unloaded versus absent terrain

Current read contracts represent terrain absence with `null`. A future chunk/region model must distinguish true absence from not-loaded/not-generated state if those concepts exist.

### Diagnostics

`NavigationLookup.transitions` intentionally returns only a primitive mask. It does not explain why a direction is absent.

A future diagnostic/Inspector path may expose departures, arrivals, blocks and contributing geometry if a real Movement/Pathfinder debugging need appears. It is not part of the current hot read contract.

### Movement and costs

Navigation currently represents structural topology only. Actor capabilities, occupancy, movement duration and path cost are not implemented.

### Falling

Production vertical topology exists through `RampShape`, but falling is not represented by Navigation.

A missing neighboring Shape produces no ordinary structural edge. Whether falling is later modeled as an involuntary Movement process, a separate traversal rule, or another mechanism is intentionally unresolved until Basic Movement is designed. Pathfinder must not gain free-fall routes merely because vertical coordinates exist.

### Richer ramp semantics

Current Ramp behavior is deliberately narrow:

- one cardinal axis;
- two-way linear passage;
- no side entry;
- no XY-diagonal entry;
- no fractional surface state;
- no general stair/orientation framework.

These may be expanded only when a real consumer needs them.

### Caching

No cache policy is selected. Future profiling should decide whether topology reuse is best represented by no cache, chunk-local arrays, bounded maps or another derived structure.

## 13. Determinism work to enforce as systems appear

The stable architecture requires:

- explicit simulation RNG seed/state for authoritative randomness;
- stable tie-break ordering;
- no authoritative dependence on `HashMap`/`HashSet` iteration order;
- validation of background results before authoritative application.

There is not yet a general RNG service because no current mechanic requires authoritative randomness. It should be introduced with the first real random consumer rather than as unused infrastructure.

## 14. Performance watch points

Current Geometry/Terrain sparse implementations use object-keyed maps. `GeometryState.find` and `SparseTerrainStorage.find` may allocate temporary cell keys depending on JVM escape analysis.

Do not replace them preemptively. When Pathfinder creates a representative Navigation workload, measure lookup allocation and throughput first; this is a known profiling target.

The current resolver performs at most 36 local Geometry lookups per source query. This is a deliberate correctness envelope derived from the current supported-position/arrival contract, not a performance target that should be reduced by weakening topology semantics.

## 15. Current roadmap

```text
DONE  Object/Definition/Scheduler/Spatial foundation
DONE  Landscape terrain core
DONE  Geometry foundation and transition algebra
DONE  Local directed Navigation resolver
DONE  Architecture/test hardening after external review
DONE  Production primitive RampShape with real Z transitions
NOW   Final Ramp/Navigation hardening and documentation alignment
NEXT  Control Backbone
LATER Scenario Harness -> Basic Movement -> Occupancy -> Pathfinder -> first agent vertical slice
```

Before Basic Movement begins, falling ownership must be decided explicitly. Before Pathfinder optimization begins, Navigation/Terrain/Geometry lookup cost should be measured under representative load.

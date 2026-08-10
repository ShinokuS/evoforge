# EvoForge Architecture Contract

Status: living architectural contract.

This document contains the stable boundaries and invariants of EvoForge. Exact class lists, current implementations, test files, pull requests and temporary optimization choices belong in `TECHNICAL_REFERENCE.md`.

## 1. Status vocabulary

- **FIXED** — semantic contract. Internal implementation may change without changing users.
- **WORKING** — current design direction. It may be revised when a real vertical slice supplies better evidence.
- **DEFERRED** — intentionally undecided. Existing contracts must leave room for the later decision.

## 2. Project model

EvoForge is a deterministic emergent simulation built around:

- an OO domain model;
- composition-driven immutable definitions;
- specialized authoritative state owners;
- event/scheduler-driven execution instead of per-object `update(dt)`;
- discrete three-dimensional world coordinates;
- a common Controller/Command boundary for Player, AI, scripts and scenarios;
- local structural navigation with replaceable pathfinding and movement policies;
- headless scenario and invariant tests;
- selective data-oriented optimization only after workload evidence exists.

EvoForge is not a pure ECS, a universal physics engine, a giant `WorldCell` model, or a design where every object executes every tick.

## 3. Core invariants [FIXED]

| ID | Invariant |
|---|---|
| I-01 | Every individual runtime object has stable `ObjectId` identity. |
| I-02 | `ObjectRepository` owns identity/existence only; mechanics do not accumulate there. |
| I-03 | Definitions are immutable runtime descriptions compiled from composition-driven source data. |
| I-04 | Every mutable authoritative property has exactly one owner. |
| I-05 | Systems depend on narrow read contracts rather than mutable internals of other systems. |
| I-06 | Normal gameplay impossibility is a structured domain rejection, not a JVM exception. |
| I-07 | Events describe facts after authoritative mutation; they are not hidden commands. |
| I-08 | Authoritative simulation mutation occurs on the simulation thread unless this contract is explicitly revised. |
| I-09 | Scheduler controls time/order of activation and does not know domain semantics. |
| I-10 | Public semantic contracts must survive replacement of internal storage/algorithms. |
| I-11 | Hot paths avoid unnecessary scans, allocations, boxing and temporary collections once the path is proven hot. |
| I-12 | New fundamental systems arrive with headless correctness tests and a diagnostic strategy. |

## 4. World coordinates [FIXED REPRESENTATION / DEFERRED BOUNDS]

Authoritative world positions are represented as:

```text
(int x, int y, int z)
```

`int` is the public coordinate representation. It is **not** a promise that every value from `Integer.MIN_VALUE` through `Integer.MAX_VALUE` is a valid world coordinate.

Valid world bounds are a separate world/storage policy and remain **DEFERRED** until region/chunk/world-generation requirements are concrete.

Consequences:

- local algorithms must not silently wrap coordinates when operating near their supported bounds;
- future internal packed keys are allowed if a later bounds policy makes them useful;
- choosing packed/chunk storage later must not require changing normal coordinate consumers from `int x, int y, int z`.

## 5. Object spatial ownership [FIXED]

`SpatialSystem` owns positions of `WorldObject` instances only.

```text
ObjectId -> XYZ
```

`ObjectSpatialIndex` contains only indexes derived from object position.

Landscape, terrain, water, temperature and other environmental state do not become `WorldObject` instances and do not enter object spatial indexes merely because they also use XYZ addresses.

Shared XYZ is an address, not a shared owner of all cell state.

## 6. Landscape and terrain [FIXED]

Base landscape content is stored separately:

```text
XYZ -> LandscapeDefinitionId | absence
```

Absence is not a definition such as `core:open`.

`TerrainSystem` owns terrain mutation. Concrete terrain storage is replaceable.

A new environmental mechanic normally adds a new specialized state owner rather than fields to a universal landscape cell.

## 7. Geometry [FIXED]

Geometry is a separate mechanic over present terrain. It does not own material identity.

Presence without a geometry override means `FullShape.INSTANCE`. Sparse geometry state stores only non-default Shape overrides.

`Shape` is an open declarative local-topology contract:

- no enum of all shapes;
- no central Shape catalog required for runtime composition;
- no `instanceof`/`switch` on concrete Shape inside Navigation;
- Shape does not query World, neighbors, Navigation, ObjectId or pathfinding;
- Shape receives only the current source position relative to its own terrain anchor.

A new Shape that fits the contract is added as a new implementation plus tests, without modifying `NavigationSystem` or existing Shape implementations.

## 8. Structural transition algebra [FIXED]

A structural transition connects one source XYZ to exactly one of its 26 immediate three-dimensional neighbors.

Therefore a single structural edge may change X, Y and Z simultaneously, as long as every coordinate delta is in `[-1, 1]` and the total delta is not `(0,0,0)`.

Shape contributes three independent facts for local directions:

```text
departures
arrivals
blocks
```

Multiple Shape contributions are composed generically:

```text
resolved = departures & arrivals & ~blocks
```

The public resolved mask is always restricted to the 26 valid neighbor directions.

Contributions are accumulated by OR before resolution. Composition therefore does not depend on concrete Shape type or processing order.

Departure and arrival are independent roles. A Shape may provide one role for an external connection, while another Shape supplies the other role. A Shape may provide both roles for an edge that belongs entirely to its own local topology.

## 9. Navigation topology [FIXED]

Navigation exposes structural adjacency only.

```java
int transitions(int x, int y, int z)
```

The result is a 26-bit neighbor mask.

Navigation:

- reads only Geometry;
- does not know concrete Shape types;
- does not know ObjectId;
- does not know mover abilities;
- does not assign path cost;
- does not perform pathfinding;
- does not mutate world state.

For one source XYZ, the base resolver reads only the local `3x3x3` geometry neighborhood.

Structural topology is genuinely three-dimensional: a Shape may expose elevation-changing neighbor edges without Navigation learning any Shape-specific rule.

### 9.1 Directed graph [FIXED]

Structural navigation is a **directed graph**.

If `transitions(A)` contains direction `d`, it does not imply that `transitions(A + d)` contains `-d`.

Symmetric movement emerges only when both directed edges are independently supported. Shapes may expose bidirectional or asymmetric topology as their semantics require.

### 9.2 Caching [DEFERRED IMPLEMENTATION]

There is currently no persistent Navigation cache contract.

Potential future implementations include:

- no cache;
- bounded cache;
- chunk-local topology;
- region-derived topology;
- another representation justified by profiling.

Any cache is derived state and must remain invisible behind the stable Navigation read contract. Cache lifecycle and invalidation are designed together with the workload and world-region lifecycle that actually require them.

## 10. Movement, traversal and pathfinding boundaries [FIXED BOUNDARIES / WORKING DETAILS]

Navigation answers structural adjacency.

Movement decides whether and how a concrete actor performs a transition. Actor capabilities, occupancy, speed and movement semantics do not belong in Shape merely to make basic Navigation work.

`SpatialSystem` applies an already-authorized object position mutation; it does not decide whether terrain/path/collision permits movement.

Pathfinding is a replaceable consumer of Navigation. A*, Dijkstra, hierarchical search, flow fields or other algorithms are implementation choices, not project-wide architecture.

Transition/path costs remain **DEFERRED** until the first real Pathfinder/Movement consumer demonstrates what information is required.

Involuntary movement such as falling is not yet assigned to Navigation or Movement semantics. That ownership remains **DEFERRED** until Basic Movement is designed; it must not be inferred merely from the existence of vertical Shape edges.

## 11. Determinism [FIXED PRINCIPLE]

For the same initial authoritative state, same submitted command sequence and same simulation random seed/state, EvoForge must produce the same authoritative simulation result within the supported runtime contract.

Rules:

1. Authoritative randomness comes from explicitly owned simulation RNG state with reproducible seed/state. `Math.random()` and `ThreadLocalRandom` are not authoritative randomness sources.
2. Authoritative behavior must not depend on unspecified iteration order of `HashMap`, `HashSet` or equivalent containers.
3. When several valid choices exist and order affects the result, the system uses an explicit stable tie-break rule such as sequence number, stable id or defined ordering.
4. Background workers may compute read-only results but never directly mutate the authoritative World. Returned work must be validated before application.
5. Floating-point values are not globally forbidden, but authoritative branching must not accidentally depend on unstable iteration/reduction order. A stricter cross-platform bit-identical numeric policy is **DEFERRED** until a mechanic requires it.

## 12. Control boundary [FIXED PRINCIPLE / WORKING IMPLEMENTATION]

Player, AI, scripts and scenarios converge on the same external control path:

```text
Controller -> Command -> handler/action -> authoritative systems
```

A Command is intent. A continuing Action is runtime process state. Normal rejection is structured data.

No player-only shortcut may mutate mechanics internals directly.

## 13. Performance model [FIXED PRINCIPLE]

Optimization priority:

1. do not perform unnecessary work;
2. bound search by locality/indexes;
3. reuse derived results when a real workload benefits;
4. remove hot-path allocations/boxing;
5. introduce specialized primitive/DOD structures only for stable measured hot paths;
6. consider SIMD/parallelism only after the previous steps and profiling.

A custom low-level structure is not justified merely because a future workload might exist.

## 14. Working scale envelope [WORKING]

The current design envelope used to reject obviously non-scalable architecture is:

```text
total persistent objects:       ~1,000,000
positioned world objects:       ~100,000+
simultaneously active agents:   ~10,000
```

These are architectural scale targets, not FPS or latency promises.

The design must therefore avoid mandatory per-tick O(total objects) work and mandatory global scans for common gameplay operations.

Exact loaded terrain-cell counts, region sizes and chunk dimensions remain **DEFERRED** until the chunk/world-generation model exists.

Scale numbers may be revised when representative scenarios and benchmarks exist; changing them does not silently redefine the semantic ownership contracts above.

## 15. Extension rules [FIXED]

### Existing mechanic, new content

Add definition data only when the existing aspect/mechanic combination already expresses the content.

### New object mechanic

Add a specialized definition compiler/store if needed, a runtime owner/system, tests and explicit bootstrap registration. Do not add a giant central runtime state map.

### New landscape mechanic

Add its own owner/system instead of expanding Terrain into a universal environment structure.

### New Shape

Add a new Shape implementation and topology/composition tests. Do not modify Navigation to recognize the type.

### New spatial query

If it depends only on object position, add an appropriate specialized object spatial index. If it depends on domain mechanics, it belongs to that mechanic.

### New Pathfinder or AI algorithm

Add a replacement implementation behind the existing semantic boundary rather than changing world ownership.

## 16. Explicitly deferred decisions

The architecture intentionally does not yet fix:

- exact world coordinate bounds;
- chunk/region dimensions and terrain packing;
- unloaded/not-generated versus absent terrain semantics;
- world generation;
- water/temperature/weather simulation details;
- occupancy representation and collision precision;
- mover-specific capability model;
- richer ramp/stair topology beyond the current primitive cardinal ramp;
- involuntary falling semantics;
- Navigation caching and cache lifecycle;
- pathfinding algorithm, hierarchy and path cache;
- path cost representation;
- background pathfinding revision/snapshot mechanism;
- full object lifecycle orchestration;
- persistence format and region save boundaries;
- final EventBus implementation;
- multithreading architecture beyond the single authoritative mutation thread;
- exact AI planner family;
- final renderer/Z-level UX.

A deferred choice is successful only if it can later be implemented without destroying the fixed ownership and semantic boundaries above.

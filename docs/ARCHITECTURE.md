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
| I-13 | Command crosses the external-intent boundary; continuing internal processes and internal state producers use narrow domain APIs directly rather than turning Command into internal RPC. |
| I-14 | Generic Control routes and observes commands but does not depend on world-domain types; world domains do not depend on Control. |
| I-15 | Structural Navigation decides edge existence; actor-independent TransitionCost prices an already-valid directed edge; Movement converts that price into timed execution. |
| I-16 | Timed Movement does not create a second authoritative position: Spatial remains at the source until completion-time revalidation authorizes `SpatialSystem.move`. |
| I-17 | Scheduler process identity/routing is infrastructure; domain Action/process identity and state remain owned by the domain mechanic. |

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

A timed Movement Action does not own an interpolated or alternate authoritative coordinate. Until completion succeeds, Spatial remains at the action source; after completion succeeds, Spatial atomically owns the destination.

## 6. Landscape and terrain [FIXED]

Base landscape content is stored separately:

```text
XYZ -> LandscapeDefinitionId | absence
```

Absence is not a definition such as `core:open`.

`TerrainSystem` owns terrain storage and terrain-specific mutation invariants. Concrete terrain storage is replaceable. Normal conflicts caused by current terrain state are structured results; invalid definitions or other broken programming/configuration inputs remain exceptions.

Terrain and Geometry remain separate authoritative concerns. `TerrainSystem` must not depend on `GeometrySystem` merely to coordinate lifecycle.

The public coordinated landscape write capability is `LandscapeMutations`. It owns the semantic operation when one logical landscape mutation must keep multiple owners coherent.

Current terrain lifecycle policy is:

```text
placeTerrain
    -> create terrain only when the position is empty
    -> clear any stale geometry override
    -> present terrain therefore resolves to default FullShape

replaceTerrain
    -> change the definition of existing terrain
    -> preserve the existing geometry override

removeTerrain
    -> remove existing terrain
    -> remove its geometry override
```

Therefore a non-default Shape does not survive terrain removal and later re-placement at the same XYZ. Shape belongs to the lifetime of the terrain cell, not permanently to the coordinate.

Landscape definitions may provide mechanic-specific immutable data such as actor-independent `traversal.cost`. The terrain cell still stores only `LandscapeDefinitionId`; traversal configuration is compiled into a mechanic-owned definition store.

Internal producers such as future world generation, erosion or continuing Actions may call the narrow landscape/domain write capability directly. They are not required to manufacture Commands. Write-capabilities are supplied explicitly during bootstrap/composition and should remain narrowly held and reviewable.

A new environmental mechanic normally adds a new specialized state owner rather than fields to a universal landscape cell.

## 7. Geometry [FIXED]

Geometry is a separate mechanic over present terrain. It does not own material identity.

Presence without a geometry override means `FullShape.INSTANCE`. Sparse geometry state stores only non-default Shape overrides.

`Shape` is an open declarative local-geometry contract:

- no enum of all shapes;
- no central Shape catalog required for runtime composition;
- no `instanceof`/`switch` on concrete Shape inside Navigation or TransitionCost calculation;
- Shape does not query World, neighbors, Navigation, ObjectId or pathfinding;
- Shape receives only the current source position relative to its own terrain anchor plus a local direction when a directed traversal characteristic is requested.

Shape owns two related but distinct local contributions:

```text
structural topology roles
    -> departures / arrivals / blocks

intrinsic traversal geometry
    -> departureTraversalFactor / arrivalTraversalFactor
```

Traversal factors follow the **same departure/arrival role ownership and relative-coordinate law** as topology. A Shape contributes only its own source-side departure factor or destination-side arrival factor; it does not calculate the neighboring Shape's contribution.

Current traversal factors use fixed-point scale `1000 = 1.0`. `0` means that the Shape does not own the requested traversal role. Current `FullShape` and cardinal `RampShape` use neutral factors for the roles their topology exposes; no arbitrary additional ramp penalty is part of the current contract.

A new Shape that fits the contract is added as a new implementation plus tests, without modifying `NavigationSystem`, `TransitionCostCalculator` or existing Shape implementations merely to recognize its concrete type.

## 8. Structural transition algebra [FIXED ALGEBRA / WORKING SHAPE MODEL]

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

Departure and arrival are independent roles. For an external connection, one Shape may declare that a transition can depart from its supported surface while another independently confirms arrival at its own supported surface. Missing confirmation from either side means that structural edge does not exist. Shapes do not query one another to make this decision.

The **current production structural Shape model** (`FullShape` and the primitive cardinal `RampShape`) uses one supported navigation position at:

```text
anchor + (0, 0, 1)
```

Within this current model:

- departures originate only from that supported position;
- arrivals only confirm directions whose destination is that supported position;
- a Shape does not assert the existence of a neighboring Shape or foreign supported surface;
- occupied solid terrain coordinates are not ordinary navigation positions.

The same supported-position relationship is used by current TransitionCost support-owner lookup. For a directed edge `A -> B`, source support is `A - (0,0,1)` and destination support is `B - (0,0,1)`; the source Shape is queried for departure role and the destination Shape for arrival role.

This one-supported-position rule is **WORKING**, not a permanent restriction on every future Shape. If a real future Shape requires multiple supported positions or a different local representation, its contract, Navigation read envelope and TransitionCost support-owner lookup must be revised together rather than bypassed with type-specific logic.

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
- does not assign transition/path cost;
- does not perform pathfinding;
- does not mutate world state.

Transition **distance** and Geometry **read distance** are different concepts. Structural edges remain limited to the 26 immediate neighbors:

```text
dx, dy, dz in [-1, 1]
```

For one source XYZ, the current resolver reads Geometry at source-relative offsets:

```text
dx in [-1, 1]
dy in [-1, 1]
dz in [-2, 1]
```

This is at most `3 * 3 * 4 = 36` local Geometry lookups. The extra lower Z layer is required by the current one-supported-position Shape model: for a one-step transition with `dz = -1`, the Shape supporting the destination position may have its terrain anchor one additional cell below that destination. Reading that anchor lets it contribute the destination-side arrival without either Shape learning about its neighbor or Navigation learning a concrete Shape type.

The read envelope is therefore derived from the current Shape role contract, not from path length. If the structural Shape model changes, the required local read envelope must be re-derived and tested with it.

Structural topology is genuinely three-dimensional: a Shape may expose elevation-changing neighbor edges without Navigation learning any Shape-specific rule.

### 9.1 Directed graph [FIXED]

Structural navigation is a **directed graph**.

If `transitions(A)` contains direction `d`, it does not imply that `transitions(A + d)` contains `-d`.

Symmetric movement emerges only when both directed edges are independently supported. Shapes may expose bidirectional or asymmetric topology as their semantics require.

TransitionCost is likewise directed: different departure/arrival factors may make `cost(A -> B)` differ from `cost(B -> A)` even when both structural edges exist.

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

The current semantic chain is:

```text
Navigation
    -> does directed adjacent structural edge A -> B exist?

TransitionCost
    -> what is the actor-independent intrinsic price of that valid edge?

MovementRate + Movement timing state
    -> how many simulation ticks does this mover require?

MovementAction
    -> start, sleep, completion-time revalidation, Spatial commit or interruption

Pathfinder (future)
    -> choose among valid edges using the SAME TransitionCost semantics
```

### 10.1 Timed adjacent Movement [FIXED CURRENT SEMANTICS]

`MoveStepCommand` starts one adjacent timed attempt; acceptance does not immediately mutate Spatial.

Movement validates object capability, placement, adjacency and Navigation before starting an Action. At most one ordinary Movement Action may be active per object.

While the Action is active:

```text
authoritative Spatial position = source
```

The Action schedules one future completion through a narrow `ProcessScheduler`. On completion, Movement revalidates that the object still exists, is still at the recorded source and that Navigation still exposes the directed edge. Only then may it call `SpatialSystem.move`; otherwise the Action is interrupted and removed without changing position.

Between start and scheduled completion the current Action is dormant. It does not subscribe to terrain/geometry mutation notifications yet; changed topology is observed at completion-time revalidation.

Every ordinary Movement transition lasts at least one simulation tick. There is no authoritative fractional/interpolated position between cells.

### 10.2 Movement capability and timing [FIXED CURRENT SEMANTICS]

Ordinary self-propelled Movement capability is definition-backed:

```text
ObjectDefinitionId -> MovementRate
```

`MovementRate` is a positive integer in traversal-cost units per simulation tick. Absence of the movement aspect means the current ordinary movement capability is unavailable.

Transition-cost units are converted to ticks using deterministic per-object fractional carry. The carry persists across separate adjacent steps so repeated rounding does not systematically distort fast movers or diagonal travel.

Current timed Movement is independent of wall-clock/render FPS. Presentation speed may control how quickly simulation ticks are advanced in real time; it does not redefine `MovementRate`, `TransitionCost` or simulation-tick ordering.

### 10.3 Actor-independent TransitionCost [FIXED CURRENT MODEL]

TransitionCost is calculated only after Navigation has established a valid adjacent directed edge.

For edge `A -> B` with direction `d`, the conceptual model is:

```text
localA = surfaceCost(A) * departureFactor(shapeA, d)
localB = surfaceCost(B) * arrivalFactor(shapeB, d)

TransitionCost(A -> B)
    = lengthFactor(d)
      * average(localA, localB)
```

The current model uses both supporting landscape cells.

Ownership:

- `LandscapeDefinitionId` mechanic data provides positive actor-independent `SurfaceTraversalCost`;
- source Shape provides only its directed departure factor;
- destination Shape provides only its directed arrival factor;
- grid direction provides cardinal/double-diagonal/triple-diagonal length;
- Movement does not branch on concrete Shape type;
- `MovementRate` is applied after TransitionCost and does not redefine the edge price.

Authoritative cost arithmetic is fixed-point integer arithmetic. Current neutral scales are `1000` for surface cost, Shape factor and grid-length scale; current grid lengths are `1000`, `1414`, `1732`. The combined TransitionCost is rounded deterministically once at its output boundary, then Movement timing carry handles cost-to-tick remainder separately.

The current TransitionCost is **actor-independent**. Different movers with different rates see the same intrinsic edge ranking. Actor/surface interactions such as wheels versus stairs or swamp affinity remain **DEFERRED** until a real capability consumer requires them.

### 10.4 Scheduler/process boundary [FIXED]

Scheduler knows activation time, handler and opaque process id; it does not own domain Action state.

Domain process identity (for example `MovementActionId`) is distinct from infrastructure `TaskHandle`.

A timed mechanic normally receives a narrow `ProcessScheduler` already bound to its registered handler instead of raw authority over `Scheduler + HandlerId + SimulationClock`.

The current production simulation step is:

```text
clock.advance()
Scheduler.dispatchDue(clock.tick())
```

owned by `SimulationStepper`. Scenario fixtures and future presentation drive this production contract rather than inventing their own tick semantics.

### 10.5 Occupancy and pathfinding [DEFERRED DETAILS]

Occupancy remains separate from structural Navigation. Current Movement does not reserve destinations, so multi-agent conflict semantics are not yet fixed.

Pathfinding is a replaceable consumer of Navigation and `TransitionCost`. A*, Dijkstra, hierarchical search, flow fields or other algorithms are implementation choices, not project-wide architecture.

Future Pathfinder must not maintain a second independent edge-price model that disagrees with authoritative Movement.

Early Movement cancellation, actor-specific surface affinity, multi-step `MoveTo`, climbing/swimming/flying overlays and involuntary falling remain deferred until real consumers establish their requirements.

## 11. Determinism [FIXED PRINCIPLE]

For the same initial authoritative state, same submitted command sequence and same simulation random seed/state, EvoForge must produce the same authoritative simulation result within the supported runtime contract.

Rules:

1. Authoritative randomness comes from explicitly owned simulation RNG state with reproducible seed/state. `Math.random()` and `ThreadLocalRandom` are not authoritative randomness sources.
2. Authoritative behavior must not depend on unspecified iteration order of `HashMap`, `HashSet` or equivalent containers.
3. When several valid choices exist and order affects the result, the system uses an explicit stable tie-break rule such as sequence number, stable id or defined ordering.
4. Background workers may compute read-only results but never directly mutate the authoritative World. Returned work must be validated before application.
5. Floating-point values are not globally forbidden, but authoritative branching must not accidentally depend on unstable iteration/reduction order. Current Movement/TransitionCost arithmetic deliberately uses integer/fixed-point arithmetic; a stricter project-wide cross-platform bit-identical numeric policy remains **DEFERRED** until another mechanic requires it.
6. Advancing the same number of production simulation ticks through different caller batching must not change authoritative results; presentation FPS is not simulation semantics.

## 12. Control boundary [FIXED PRINCIPLE / WORKING DELIVERY]

Player, AI, scripts, scenarios and other external controllers converge on the same command path:

```text
external intent -> Command -> delivery -> dispatcher -> handler -> authoritative domain APIs
```

A Command is immutable intent. A continuing Action/process is runtime state and is not represented as a stream of internal Commands merely because it later mutates systems.

Command is therefore an **external-intent boundary**, not a universal internal RPC mechanism. Internal state producers and already accepted processes use the narrow domain APIs of authoritative owners directly.

Normal world-state impossibility is structured data. Invalid programming/bootstrap/configuration state remains exceptional.

All operation outcomes expose a minimal neutral observation floor:

```text
accepted
namespaced result code
```

Namespaced codes use forms such as:

```text
terrain:position_occupied
movement:already_moving
movement:transition_unavailable
```

There is no global enum of every project rejection reason. Concrete domains may expose richer typed results while generic Control sees only the common observation floor.

Generic Control has no world-domain knowledge. The dependency law is:

```text
simulation.control.core  -X-> world.*
simulation.control.sync  -X-> world.*
world.*                   -X-> simulation.control.*
```

Concrete adapters under `simulation/control/<use-case>/` may depend on the narrow domain APIs required by that use-case. Commands are grouped by intent/use-case rather than by whichever single system happens to be mutated.

The current delivery is synchronous: submission dispatches and executes the handler immediately. This does **not** require every accepted domain operation to finish immediately. For example, accepted `MoveStepCommand` synchronously starts a Movement Action and returns while Spatial remains at the source; Scheduler later resumes the domain Action directly, without routing continuation through Control.

For deterministic callers, submitted command order is the deterministic order of calls.

A future queued/asynchronous delivery may reuse the same Command/Handler/Dispatcher contracts, but it must explicitly define queue order, flush point and state-visibility semantics. Changing delivery policy is not assumed to preserve within-tick visibility automatically.

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

Timed Movement follows the event-driven model: an active step schedules completion instead of requiring every mover to execute an `update` on every tick.

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

For example, a new ordinary landscape material may define another positive `traversal.cost` without changing Movement or TransitionCost code.

### New object mechanic

Add a specialized definition compiler/store if needed, a runtime owner/system, tests and explicit bootstrap registration. Do not add a giant central runtime state map.

### New landscape mechanic

Add its own owner/system instead of expanding Terrain into a universal environment structure.

### New Shape

Add a new Shape implementation and topology/composition tests. Do not modify Navigation or TransitionCostCalculator to recognize the concrete type. If intrinsic geometry requires a non-neutral traversal factor, override only the Shape-owned departure/arrival contribution under the same role law.

If the Shape no longer fits the current one-supported-position structural model, revise the general Shape contract, resolver envelope and cost support-owner lookup explicitly rather than adding a concrete-type exception.

### New spatial query

If it depends only on object position, add an appropriate specialized object spatial index. If it depends on domain mechanics, it belongs to that mechanic.

### New Pathfinder or AI algorithm

Add a replacement implementation behind the existing semantic boundary rather than changing world ownership. Pathfinder consumes Navigation and the shared TransitionCost semantics; it does not become an authoritative Movement mutator.

### New timed mechanic

Keep domain process state in the domain. Register one scheduled handler for the process family and give the start system a narrow bound scheduling capability. Do not add a global Scheduler switch or universal Action framework merely because multiple mechanics use time.

### New Command

Add a concrete immutable Command, typed CommandResult and one handler under the appropriate `control/<use-case>/` area. The handler may depend on narrow domain APIs; generic Control must not learn the new domain type. Do not create a Command for an internal mutation merely to route calls between systems.

## 16. Explicitly deferred decisions

The architecture intentionally does not yet fix:

- exact world coordinate bounds;
- chunk/region dimensions and terrain packing;
- unloaded/not-generated versus absent terrain semantics;
- world generation algorithms and persistence integration;
- water/temperature/weather simulation details;
- occupancy/reservation representation and collision precision;
- richer mover-specific capability model and actor-specific surface affinity;
- early Movement cancellation/reactive wake-up semantics;
- multi-step `MoveTo`/route-execution lifecycle;
- richer ramp/stair topology beyond the current primitive cardinal ramp;
- involuntary falling semantics;
- Navigation caching and cache lifecycle;
- pathfinding algorithm, hierarchy and path cache;
- background pathfinding revision/snapshot mechanism;
- full object lifecycle orchestration;
- persistence format and region save boundaries;
- final EventBus implementation;
- queued/asynchronous command batching and within-tick visibility policy;
- multithreading architecture beyond the single authoritative mutation thread;
- exact AI planner family;
- final renderer/Z-level UX and art pipeline.

A deferred choice is successful only if it can later be implemented without destroying the fixed ownership and semantic boundaries above.

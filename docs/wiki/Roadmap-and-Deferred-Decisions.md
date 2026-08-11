# Roadmap and Deferred Decisions

EvoForge intentionally separates completed architectural foundations from decisions that should wait for a real consumer. A deferred detail is not an invitation to design speculative infrastructure early, but several major gameplay milestones are already required parts of the project.

## Current sequence

```text
DONE  Object / Definition / Scheduler / Spatial foundation
DONE  Landscape terrain core
DONE  Geometry foundation and transition algebra
DONE  Directed structural Navigation
DONE  Production cardinal RampShape
DONE  Final geometry/navigation hardening and documentation
DONE  Control Backbone core + first PlaceTerrain vertical slice
DONE  Test-only Scenario fixture: arrange -> start -> submit/read
DONE  Timed Basic Movement: one adjacent structural transition
DONE  first production SimulationStepper + Scheduler process binding
NEXT  TransitionCost model: terrain + Shape traversal + grid length
      minimal visualization / Z-level debug view
      Occupancy
      Pathfinder
      first agent vertical slice
      World generation
```

The sequence can change when a real dependency requires it, but these named milestones are planned work rather than optional ideas. Their internal architecture should still be introduced by the first consumer that proves each requirement.

## Control Backbone

The current narrow control foundation establishes one external-intent path for Player, AI, scripts, scenarios and future adapters:

```text
external intent
    ↓
Command
    ↓
delivery
    ↓
CommandDispatcher
    ↓
handler
    ↓
authoritative domain APIs
    ↓
structured result
```

The first delivery is synchronous. `PlaceTerrainCommand` and `MoveStepCommand` are concrete vertical slices.

Command is not an internal RPC requirement. Once intent has been accepted, internal processes such as continuing Movement Actions, future world generation or erosion may work directly through narrow domain write APIs.

Queued/asynchronous delivery remains deferred. It may reuse the same Command/Handler/Dispatcher contracts but must define deterministic ordering, flush point and within-tick state visibility explicitly.

## Scenario fixture

The scenario layer remains intentionally a **test-only deterministic fixture**, not a simulation runtime.

It separates two phases:

```text
ScenarioBuilder
    -> arrange a small hand-authored world through controlled write capabilities
    -> register test definitions
    -> create/place test objects
    -> start()

ScenarioHarness
    -> submit production Commands
    -> advance the production SimulationStepper
    -> observe read-only Object / Transform / Terrain / Geometry / Navigation state
```

`start()` closes the arrange phase. The running harness does not expose raw authoritative mutators.

Movement created the first real timed Scheduler consumer, so the harness now exposes `advance()` and `advanceTicks(n)`. Those methods do not invent test-only time semantics: they delegate to the production `SimulationStepper`.

Scenario worlds remain small and hand-authored so tests know the expected answer in advance. Procedurally generated worlds serve different purposes such as scale, robustness and later gameplay/world-generation validation.

## Timed Basic Movement

The first Movement slice is now concrete and deliberately narrow: an object with a compiled `movement.rate` capability may start one adjacent structural transition through `MoveStepCommand`. Pathfinder is not involved.

The lifecycle is:

```text
MoveStepCommand
    ↓
validate object capability / placement / adjacency / Navigation
    ↓
create MovementAction
    ↓
schedule completion after deterministic duration
    ↓
object remains authoritatively at source
    ↓
completion-time revalidation
    ↓
SpatialSystem.move(...) or interrupt
    ↓
remove active MovementAction
```

Movement Actions exist only while active; completed/interrupted history is not retained inside Movement.

The first duration model uses neutral grid transition length (`1`, `sqrt(2)`, `sqrt(3)` represented in fixed-point) divided by `MovementRate`. Fractional timing is preserved with deterministic per-object carry rather than per-step ceiling, and every movement transition takes at least one simulation tick.

`MoveStepResult` and domain `MovementStartResult` use the existing structured result floor. Unknown/stale trusted `ObjectId` values remain programming/configuration errors rather than normal domain rejection.

### Known gaps of the first slice

- no Occupancy or destination reservation: multiple objects can currently target the same cell;
- no early movement cancellation: Actions end only when their scheduled completion runs;
- no terrain/Shape-specific transition cost yet;
- no Pathfinder or multi-step `MoveTo`;
- no continuous/interpolated authoritative position between cells.

These are explicit boundaries, not accidental hidden behavior.

## Timed process integration

Movement is the first production consumer of the general timed-process pattern:

```text
domain system starts process
    ↓
domain-owned process state
    ↓
ProcessScheduler.scheduleAfter(delay, processId)
    ↓
BoundProcessScheduler binds one HandlerId
    ↓
Scheduler
    ↓
domain process processor resumes processId
```

The Scheduler knows only **when**, **which handler**, and **which process id**. The domain owns what that process means.

`MovementActionId` is not `TaskHandle`. One registered Movement handler services all Movement Actions; future timed mechanics should follow the same narrow binding pattern rather than add a central Scheduler switch or universal Action framework.

## Production simulation step

`SimulationStepper` now owns the first production definition of one simulation tick:

```text
clock.advance()
Scheduler.dispatchDue(clock.tick())
```

One tick performs one Scheduler snapshot batch. Work scheduled during a handler for the same tick is not recursively drained in that batch. Movement never schedules zero-duration completion, so it does not depend on same-tick recursive execution.

Scenario and future presentation code drive this production contract rather than define their own ordering. Tests assert that `advanceTicks(n)` is equivalent to invoking the production step `n` times individually.

## TransitionCost model — next

The next Movement milestone replaces neutral grid length as the only transition cost with the already-agreed directed `TransitionCost` model.

The design constraints are:

```text
Navigation decides POSSIBILITY
TransitionCost decides PRICE
MovementRate converts PRICE to TIME
Pathfinder later consumes the SAME PRICE
```

The first cost model should combine both cells of `A -> B`, not choose an arbitrary destination-only terrain value. Landscape definitions provide base surface cost; Shape contributes its own traversal characteristic under the same departure/arrival role law used by structural topology; grid direction contributes cardinal/double-diagonal/triple-diagonal length.

Movement must not contain `instanceof RampShape` or a growing switch over concrete Shapes. A new Shape owns only its local directed traversal contribution.

Actor-specific surface affinity (for example a swamp creature preferring mud while a human prefers road) is deliberately deferred. The first TransitionCost remains actor-independent; `MovementRate` changes overall speed but not route ranking.

## Minimal visualization

A first visual/debug view is a required milestone after the TransitionCost slice, not a final renderer project.

Its purpose is to make the already-existing spatial/navigation/movement behavior observable by a human. The initial scope should stay small:

```text
render one Z level
switch visible Z level
show terrain / ramp geometry
show object positions
click or inspect a cell
show structural transition mask / basic diagnostics
watch discrete cell-to-cell movement
```

The first view does **not** need smooth movement interpolation. An object may remain displayed in its source cell until its Movement Action commits the destination, so faster objects simply change cells on earlier simulation ticks.

This is intended as a development instrument. Final rendering architecture, art pipeline and polished Z-level UX remain later concerns.

## Occupancy

Occupancy is intentionally separate from structural terrain topology. Navigation can say that two positions are structurally adjacent even when another object temporarily occupies the destination.

The exact occupancy/reservation representation remains deferred until the first real multi-agent Movement scenario proves the required semantics.

## Pathfinder

Pathfinding is a required later milestone, but it comes after Basic Movement, TransitionCost and Occupancy because movement itself defines the contracts it must consume.

Pathfinding will consume Navigation rather than define terrain topology and will use the same directed TransitionCost used by authoritative Movement. Its API should be shaped by real movement needs: whether callers need a full route or next step, how unreachable or partial paths are represented, and how dynamic occupancy participates.

The first Pathfinder will also provide the first representative workload for measuring Navigation/Geometry/Terrain lookup throughput and allocation behavior.

Only then should the project decide whether topology caching, packed coordinate keys, chunk-local arrays, hierarchical search or other low-level optimizations are justified.

## First agent vertical slice

Once Movement and Pathfinder exist, the first agent slice can connect an actual object/controller intent to repeated movement through real structural navigation.

That slice should prove the end-to-end path before broader AI planner families are designed.

## World generation

World generation is a required project milestone, but it is **not** the test fixture for Movement or Pathfinder correctness.

Scenario tests use hand-authored worlds because the expected topology and result are known exactly. Procedural generation serves different goals:

```text
create larger playable worlds
deterministic generation from explicit seeds
exercise scale and robustness
become the first real authoritative-randomness consumer
force concrete chunk/region/generated-state decisions when needed
```

The exact generator design, noise model, biomes, caves, region dimensions and persistence boundaries remain deferred until this phase. A minimal visualizer should exist first so generated worlds have an immediate human observer.

## Deferred world-generation details

The following remain intentionally open until generation/streaming/persistence becomes current:

```text
exact valid world coordinate bounds
chunk and region dimensions
terrain packing
unloaded vs absent vs not-generated semantics
RNG service and seed ownership details
region save boundaries
persistence format
```

These choices are interconnected and should be designed together when a real generation or persistence consumer requires them.

## Deferred movement decisions

```text
full actor capability model
actor-specific terrain/surface affinity
final occupancy/reservation representation
early cancelled MovementAction semantics
involuntary falling
climbing/jumping/swimming/flying overlays
```

Falling deserves special care: ordinary Navigation currently never treats empty space as a valid structural edge. If falling is introduced, it should be an explicit involuntary mechanic/process rather than a hidden interpretation of missing terrain.

## Deferred Navigation decisions

```text
cache policy
cache invalidation lifecycle
diagnostic explanation API beyond real Movement/Pathfinder needs
hierarchical pathfinding
path cache
background pathfinding snapshot/revision model
```

The current primitive `int transitions(x,y,z)` contract remains intentionally small. It should not be enlarged before a consumer demonstrates that another semantic query belongs at the same boundary.

## Deferred geometry decisions

Current `FullShape` and four cardinal `RampShape` orientations are sufficient for the present vertical slice. The project does not currently need:

```text
diagonal ramps
fractional surface heights
continuous slope geometry
multi-standing-position Shapes
stairs framework
bridge-specific Shape types
general orientation framework
```

If a future geometry genuinely needs multiple standing positions, the Shape role law and Navigation read-window derivation must be reconsidered together rather than patched with one-off exceptions.

## Landscape lifecycle decision

The former geometry-override lifecycle gap is resolved by the coordinated `LandscapeMutations` boundary:

```text
placeTerrain   -> clear stale override
replaceTerrain -> preserve override
removeTerrain  -> clear override
```

`TerrainSystem` still does not depend on `GeometrySystem`; `LandscapeSystem` coordinates both owners from above.

## Deferred simulation infrastructure

```text
final EventBus implementation
full object lifecycle orchestration
queued/asynchronous command batching and within-tick visibility policy
multithreading beyond one authoritative mutation thread
full renderer / final Z-level UX
final RNG architecture beyond the first real random consumer
AI planner family
```

These are acknowledged later requirements, not current implementation tasks. They should not block the required intermediate milestones listed above.

## How to decide when a deferred detail becomes current

A deferred detail should move into active design when at least one of these is true:

```text
a production consumer cannot proceed without it
a correctness test proves the current contract insufficient
a representative workload measures a real performance problem
a vertical slice exposes an ownership ambiguity
persistence/network/tooling requires a stable external representation
```

“Could be useful later” is not enough. A named milestone may already be required while its internal details remain legitimately deferred until that milestone has a real consumer.

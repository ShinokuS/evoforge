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
NEXT  Basic Movement: one adjacent structural step
      first production simulation-step owner + Action/Scheduler consumer
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

The first delivery is synchronous and the first concrete vertical slice is `PlaceTerrainCommand`.

Command is not an internal RPC requirement. Once intent has been accepted, internal processes such as future world generation, erosion or continuing Actions may work directly through narrow domain write APIs.

Queued/asynchronous delivery remains deferred. It may reuse the same Command/Handler/Dispatcher contracts but must define deterministic ordering, flush point and within-tick state visibility explicitly.

## Scenario fixture

The first scenario layer is intentionally a **test-only deterministic fixture**, not a simulation runtime.

It separates two phases:

```text
ScenarioBuilder
    -> arrange a small hand-authored world through controlled write capabilities
    -> register test definitions
    -> start()

ScenarioHarness
    -> submit production Commands
    -> observe read-only Terrain / Geometry / Navigation state
```

`start()` closes the arrange phase. The running harness does not expose raw authoritative mutators.

The first fixture deliberately has **no `advanceTicks()` and no Scheduler orchestration**. No production mechanic currently schedules work, so defining tick/dispatch semantics here would be speculative and would incorrectly make test code the owner of the simulation-step contract.

When Movement creates the first real timed Action/Scheduler consumer, a small production owner of the simulation step should define clock advancement and phase ordering. The harness may then drive that production API rather than define its own interpretation of a tick.

Scenario worlds are small and hand-authored so tests know the expected answer in advance. Procedurally generated worlds serve different purposes such as scale, robustness and later gameplay/world-generation validation.

## Basic Movement

Movement is the next gameplay milestone and the first consumer of structural Navigation.

The first slice should be deliberately small: a concrete actor attempts one adjacent structural transition through the Command boundary. It does **not** require Pathfinder.

Movement will establish real requirements for:

```text
actor movement capability
Spatial integration
MoveCommand / result semantics
transition revalidation
first Action if movement spans simulation time
first Scheduler consumer
production simulation-step ordering
movement diagnostics when a transition is rejected
```

Shape and Navigation should continue to describe structural topology rather than absorb actor-specific movement policy.

## Production simulation step and timed Actions

The project already has `SimulationClock` and `Scheduler`, but no production mechanic currently schedules work.

If Basic Movement requires duration, Movement becomes the first consumer that justifies a production simulation-step owner. That owner — name intentionally deferred until the role is concrete — should be the single place that defines phase order such as clock advancement and Scheduler dispatch.

The Scenario fixture and future GUI must drive that production contract; neither should independently define what one simulation tick means.

The exact policy for same-tick rescheduling remains deferred until the first timed Action demonstrates which behavior is required.

## Minimal visualization

A first visual/debug view is a required milestone after Movement, not a final renderer project.

Its purpose is to make the already-existing spatial and navigation behavior observable by a human. The initial scope should stay small, for example:

```text
render one Z level
switch visible Z level
show terrain / ramp geometry
show object positions
click or inspect a cell
show structural transition mask / basic diagnostics
watch an actor move
```

This is intended as a development instrument. Final rendering architecture, art pipeline and polished Z-level UX remain later concerns.

## Occupancy

Occupancy is intentionally separate from structural terrain topology. Navigation can say that two positions are structurally adjacent even when another object temporarily occupies the destination.

The exact occupancy representation remains deferred until Movement establishes the queries and mutation semantics it actually needs.

## Pathfinder

Pathfinding is a required later milestone, but it comes after Basic Movement because movement itself is its first real consumer.

Pathfinding will consume Navigation rather than define terrain topology. Its API should be shaped by real movement needs: whether callers need a full route or next step, how unreachable or partial paths are represented, and later how dynamic occupancy/costs participate.

The first Pathfinder will also provide the first representative workload for measuring Navigation/Geometry/Terrain lookup throughput and allocation behavior.

Only then should the project decide whether topology caching, packed coordinate keys, chunk-local arrays, hierarchical search or other low-level optimizations are justified.

## First agent vertical slice

Once Basic Movement and Pathfinder exist, the first agent slice can connect an actual object/controller intent to repeated movement through real structural navigation.

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
final occupancy representation
movement duration semantics beyond the first real Action
transition/path costs
involuntary falling
climbing/jumping/swimming/flying overlays
```

Falling deserves special care: ordinary Navigation currently never treats empty space as a valid structural edge. If falling is introduced, it should be an explicit involuntary mechanic/process rather than a hidden interpretation of missing terrain.

## Deferred Navigation decisions

```text
cache policy
cache invalidation lifecycle
path cost API
diagnostic explanation API beyond the first real Movement need
hierarchical pathfinding
path cache
background pathfinding snapshot/revision model
```

The current primitive `int transitions(x,y,z)` contract is intentionally small. It should not be enlarged before a consumer demonstrates that another semantic query belongs at the same boundary.

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

The former geometry-override lifecycle gap is now resolved by the coordinated `LandscapeMutations` boundary:

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

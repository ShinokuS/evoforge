# Roadmap and Deferred Decisions

EvoForge intentionally separates completed architectural foundations from decisions that should wait for a real consumer. A deferred item is not an invitation to design speculative infrastructure early.

## Current sequence

```text
DONE  Object / Definition / Scheduler / Spatial foundation
DONE  Landscape terrain core
DONE  Geometry foundation and transition algebra
DONE  Directed structural Navigation
DONE  Production cardinal RampShape
DONE  Final geometry/navigation hardening and documentation
NOW   Control Backbone core + first PlaceTerrain vertical slice
NEXT  Scenario Harness
      Basic Movement
      Occupancy
      Pathfinder
      first agent vertical slice
```

The sequence can change when a real dependency requires it, but new infrastructure should normally be introduced by the first consumer that proves its requirements.

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

## Scenario Harness

A headless scenario layer will allow deterministic vertical slices to arrange world state, submit commands, advance simulation time, and assert results.

Its purpose is not to become a second simulation framework. It should exercise the same production control and system boundaries used by the eventual game.

## Basic Movement

Movement will be the first consumer of structural Navigation. It will decide whether and how a concrete actor performs an already-described structural edge.

Questions that belong here include actor capability checks, movement timing, and command/action semantics. Shape should not absorb those concerns merely because a mover uses geometry.

## Occupancy

Occupancy is intentionally separate from structural terrain topology. Navigation can say that two positions are structurally adjacent even when another object temporarily occupies the destination.

The exact occupancy representation is deferred until Movement establishes what queries are actually required.

## Pathfinder

Pathfinding will consume Navigation rather than define terrain topology. The first Pathfinder will also provide the first representative workload for measuring Navigation/Geometry/Terrain lookup throughput and allocation behavior.

Only then should the project decide whether topology caching, packed coordinate keys, chunk-local arrays, or other low-level optimizations are justified.

## Deferred world decisions

The following remain intentionally open:

```text
exact valid world coordinate bounds
chunk and region dimensions
terrain packing
unloaded vs absent vs not-generated semantics
world generation
region save boundaries
persistence format
```

These choices are interconnected and should be designed together when streaming/generation/persistence becomes a real phase.

## Deferred movement decisions

```text
actor capability model
occupancy representation
movement duration semantics
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
diagnostic explanation API
pathfinding algorithm
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
placeTerrain  -> clear stale override
replaceTerrain -> preserve override
removeTerrain -> clear override
```

`TerrainSystem` still does not depend on `GeometrySystem`; `LandscapeSystem` coordinates both owners from above.

## Deferred simulation infrastructure

```text
final EventBus implementation
full object lifecycle orchestration
queued/asynchronous command batching and within-tick visibility policy
multithreading beyond one authoritative mutation thread
final RNG service before a real random consumer exists
AI planner family
renderer / Z-level UX
```

These are acknowledged requirements, not current implementation tasks.

## How to decide when a deferred item becomes current

A deferred item should move into active design when at least one of these is true:

```text
a production consumer cannot proceed without it
a correctness test proves the current contract insufficient
a representative workload measures a real performance problem
a vertical slice exposes an ownership ambiguity
persistence/network/tooling requires a stable external representation
```

“Could be useful later” is not enough.

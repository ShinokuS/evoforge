# Roadmap and Deferred Decisions

EvoForge deliberately separates **implemented foundations**, **the next concrete consumers**, and **ideas that are known but not justified in code yet**.

A deferred item belongs in documentation, not in dormant infrastructure. It becomes current when a real consumer, correctness requirement, persistence boundary, or measured performance problem requires it.

## Current sequence

```text
DONE  Object / Definition / Scheduler / Spatial foundation
DONE  Landscape terrain core
DONE  Geometry foundation and transition algebra
DONE  Directed structural Navigation
DONE  Production cardinal RampShape + hardening
DONE  Control Backbone + PlaceTerrain vertical slice
DONE  Production SimulationAssembly / SimulationRuntime / SimulationView
DONE  Test-only Scenario fixture
DONE  Timed adjacent Movement
DONE  SimulationStepper + Scheduler process binding
DONE  TransitionCost: terrain + Shape roles + grid length
DONE  Minimal live Z-level Visualizer
ACTIVE Procedural full-top-down landscape / Z readability
NEXT  Occupancy
      Pathfinder
      observable Action completion/outcome
      first agent / Cow vertical slice
      deterministic World Generation
      representative profiling / optimization
```

The exact internal design of a future milestone is still introduced by its first real consumer. A named milestone being planned does not justify implementing its possible sub-systems early.

## Current presentation direction

Landscape visual search is frozen for the current development phase.

The canonical development presentation is:

```text
full top-down
logical simulation cell = 1 x 1
native procedural visual cell = 16 x 16 pixels
simulation topology -> procedural presentation rules -> generated atlas
```

The generated landscape is presentation-only. Simulation contains no pixels, palettes, sprite ids or rendering rules.

The current procedural visual language covers grass/earth surface variation, cell-aligned edges/corners, Z-readable exposed faces and all four cardinal Ramp orientations. The acceptance scene includes a second elevation so the renderer cannot accidentally special-case one plateau height.

See [Z-level Visualizer and Procedural Landscape](Visualizer.md) for the implemented contract.

## Immediate next milestone: Occupancy

Occupancy remains intentionally separate from structural Navigation:

```text
Navigation      is a structural transition possible?
TransitionCost what does that transition intrinsically cost?
Occupancy       is the relevant space free/claimed/reserved now?
Movement        can this concrete actor start/finish the move?
```

The exact reservation model is still open. The first real multi-agent conflict scenario should decide whether a moving actor reserves destination, continues to occupy source, claims both temporarily, or resolves conflicts only at completion.

Do not put temporary object occupancy into Navigation topology.

## Pathfinder

Pathfinder follows Occupancy so its first production contract can account for both structural topology and the dynamic availability policy that Movement actually uses.

It must consume:

```text
NavigationLookup
TransitionCostLookup
future Occupancy read contract
```

It must not invent a second terrain-cost model. Route cost and authoritative Movement cost must remain based on the same `TransitionCost` semantics.

The first Pathfinder is also expected to become the first representative high-volume consumer of Terrain/Geometry/Navigation/TransitionCost reads. That workload should drive optimization decisions rather than speculation.

## Observable Action completion

Before the first AI consumer repeatedly chains movement steps, the result of an in-flight Movement action needs to become observable.

Today an invalidated Movement action can disappear after completion-time revalidation without leaving an outcome for an agent to reason about. The future contract should expose success/failure reason to the real consumer without prematurely introducing a universal Action framework or global EventBus.

## First agent / Cow vertical slice

The first agent slice should remain narrow:

```text
simple need/goal
    ↓
choose target
    ↓
Pathfinder
    ↓
next adjacent edge
    ↓
existing timed Movement
    ↓
observe outcome
    ↓
reconsider / continue
```

`MoveTo` should not blindly execute an immutable whole path through a changing world. The agent should be able to reconsider after authoritative movement outcomes and world changes.

Do not select a broad AI-planner family before this vertical slice proves the actual decision-loop requirements.

## Deterministic World Generation

World generation comes after the first observable agent slice rather than serving as the correctness fixture for Movement or Pathfinder.

Scenario tests remain hand-authored because their expected topology is known exactly. Generated worlds instead serve:

```text
larger playable worlds
seeded robustness testing
scale testing
first authoritative RNG consumer
pressure on chunk/region/load-state decisions
```

Still deferred until this milestone:

- noise/height/biome algorithms;
- caves and underground generation;
- chunk and region dimensions;
- unloaded vs absent vs not-generated state;
- authoritative RNG ownership and stream policy;
- generation/persistence boundaries;
- world-coordinate limits and packed-coordinate representation.

These decisions should be designed together when generation actually needs them.

## Deferred presentation decisions

The following are recorded but intentionally absent from current code:

- additional procedural materials: dirt, stone, sand, snow, water;
- priority/layered transitions between multiple terrain materials;
- alternate procedural palettes or visual styles;
- larger anchored sprites for trees, creatures, buildings and equipment;
- procedural character/object generation beyond the first gameplay consumer;
- external or hand-authored visual packs behind the same presentation boundary;
- dual-grid / marching-squares resolver for a future visual pack that genuinely requires it;
- multiple contextual lower Z levels, stronger Z-fog, roofs and cutaway modes;
- richer shadow/compositing passes;
- generated-atlas/export tooling for standalone art review;
- visual tile caches, dirty regions and chunk render storage before profiling proves a need.

The current cell-aligned renderer is not a claim that every future visual pack must use the same autotiling algorithm. It is simply the first real consumer and preserves exact alignment with simulation cells.

## Deferred Movement decisions

Current timed Movement remains intentionally narrow. Deferred items include:

- final occupancy/reservation semantics;
- early cancellation;
- reactive wake-up on terrain/geometry mutation;
- actor-specific terrain affinity and locomotion modes;
- involuntary falling;
- climbing, jumping, swimming and flying;
- multi-step `MoveTo` ownership and route lifecycle;
- continuous presentation interpolation.

Falling must remain an explicit involuntary mechanic/process. Empty space is not silently converted into an ordinary Navigation edge.

## Deferred Navigation / pathfinding infrastructure

Do not add until a representative consumer proves need:

- persistent Navigation cache;
- cache invalidation lifecycle;
- path cache;
- hierarchical pathfinding;
- packed topology storage;
- background pathfinding and snapshot/revision protocol;
- rich Navigation explanation objects on the hot lookup path.

The current primitive transition-mask read boundary stays small until a real debugging or Pathfinder consumer demonstrates a missing semantic query.

## Deferred geometry decisions

`FullShape` and four cardinal `RampShape` orientations are sufficient for current mechanics.

Still deferred:

- diagonal ramps;
- fractional surface heights;
- continuous slopes;
- stairs framework distinct from current Ramp semantics;
- bridges/suspended support;
- multi-standing-position Shapes;
- generalized orientation framework.

If a third real Shape violates the current one-supported-position assumption, revisit Shape support ownership, Navigation and TransitionCost together instead of patching one subsystem.

## Deferred Control / runtime infrastructure

Only synchronous external command submission exists today.

Still deferred:

- queued/asynchronous gateway;
- within-tick command flush semantics;
- multithreaded authoritative mutation;
- general EventBus;
- universal Action framework;
- networking/persistence-facing command representation.

A future queued gateway must explicitly preserve or redefine deterministic ordering and within-tick state visibility. Transport semantics are not allowed to silently change simulation semantics.

## Performance watch points

Current code intentionally keeps simple sparse lookup paths and computes visual topology for visible cells on demand.

Measure before changing representation:

```text
Terrain / Geometry sparse lookup allocation
Navigation local resolver throughput
TransitionCost lookup throughput
Pathfinder expansion cost
active Movement/Scheduler scale
procedural landscape frame cost
```

Potential optimizations such as packed coordinates, chunk-local arrays, caches, dirty visual regions or specialized DOD storage become current only after a representative workload demonstrates a real bottleneck.

## Decision rule

A deferred item becomes active design when at least one condition is true:

```text
a production consumer cannot proceed without it
an invariant/correctness test proves the current contract insufficient
a representative workload measures a real performance problem
persistence/network/tooling requires a stable external representation
a vertical slice exposes an ownership ambiguity
```

“Could be useful later” is not sufficient.

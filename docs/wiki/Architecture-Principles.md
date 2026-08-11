# Architecture Principles

This page explains the rules that constrain EvoForge architecture. These are more important than any particular class layout because implementations can be replaced while ownership and semantic boundaries should survive.

## One authoritative owner per mutable fact

Every mutable authoritative property has exactly one owner.

Examples:

```text
object existence        -> ObjectRepository
object XYZ               -> SpatialSystem / TransformState
terrain content at XYZ   -> TerrainSystem
geometry override        -> GeometrySystem / GeometryState
simulation time          -> SimulationClock
scheduled activation     -> Scheduler
```

A read consumer can combine data from several owners, but it must not become a second source of truth.

This prevents a common failure mode where the same concept exists in several mutable places and every mutation must keep them synchronized manually.

## Narrow read and write capabilities

Systems expose narrow contracts instead of mutable internals.

Read examples include:

```text
ObjectLookup
TransformLookup
TerrainLookup
GeometryLookup
NavigationLookup
```

A consumer should depend on the smallest semantic contract it needs. The same rule applies to mutation: write capability is supplied explicitly and should remain narrowly held and reviewable.

`LandscapeMutations` is the first coordinated write capability. It sits above Terrain and Geometry when one logical landscape operation must preserve both owners' lifecycle semantics, without making either owner depend back on the other.

## Shared coordinates are addresses, not ownership

Objects, terrain, weather, water, temperature, geometry, and future mechanics may all refer to the same `(x,y,z)` coordinate. That does not justify a universal mutable `Cell` containing fields for every mechanic.

The preferred pattern is:

```text
XYZ -> terrain owner
XYZ -> temperature owner
XYZ -> water owner
ObjectId -> XYZ object position owner
```

A composite query can assemble a view when a consumer needs one.

## Definitions are immutable composition

Persistent content identity is expressed with stable string keys such as `namespace:name`. Source definitions are composition-driven and compiled during bootstrap into typed runtime ids and mechanic-owned immutable data.

Runtime numeric ids are implementation references, not persistence identity. Save formats should retain stable keys and rebuild runtime ids during load/bootstrap.

## Object identity is stable

Every individual runtime object receives a stable `ObjectId`. The current implementation uses slot + generation semantics so a stale id cannot silently resolve to a later object that reused the same slot.

`ObjectRepository` owns identity and existence only. Mechanics do not accumulate there merely because every object has an id.

## Scheduler controls time, not semantics

The scheduler answers *when* a registered handler runs. It does not contain an enum of gameplay mechanics and does not become the owner of the task's domain meaning.

This avoids the `object.update(dt)` model in which inactive objects still consume CPU and every object is coupled to the global frame cadence.

## Commands express external intent

Player input, AI, scripts, scenarios and other external controllers converge on one control path:

```text
external controller
    ↓
Command
    ↓
delivery / dispatcher
    ↓
handler
    ↓
authoritative domain APIs
```

A Command crosses the external-intent boundary. It is not a universal internal RPC mechanism.

Once intent has been accepted, continuing Actions/processes and internal producers such as future world generation or erosion can call narrow domain APIs directly. This keeps ownership visible instead of routing every internal mutation through a command bus.

Normal world-state impossibility is a structured result, not a JVM exception. Invalid programming/bootstrap/configuration input remains exceptional.

All command results share a tiny observable floor:

```text
accepted
namespaced result code
```

Examples are `terrain:position_occupied` and a future `movement:blocked`. There is no global enum of every rejection reason.

## Generic Control routes, domains decide

The generic Control core knows how to register, dispatch and observe commands, but not what terrain, movement or construction mean.

The dependency law is:

```text
simulation.control.core  -X-> world.*
simulation.control.sync  -X-> world.*
world.*                   -X-> simulation.control.*
```

Concrete use-case handlers under `control/<use-case>/` may depend on narrow domain APIs. The reverse dependency is forbidden.

The current synchronous gateway executes immediately, so a successful mutation is visible before `submit` returns. A future queued/asynchronous gateway can reuse the same core, but its ordering and within-tick visibility must be specified explicitly.

## Events are facts after mutation

An event says that something authoritative already happened. It is not a disguised command or a request to perform hidden mutation.

This distinction matters for determinism, debugging, replay, and later asynchronous observers.

## One authoritative mutation thread

The current contract assumes one authoritative simulation mutation thread. Background work may eventually calculate read-only results, but those results must be validated before application and workers must not mutate the World directly.

This keeps ordering deterministic while still leaving room for later parallel computation.

## Open behavior, closed central dispatch

When a domain needs extensibility, prefer an open interface implemented by new content types rather than a central switch over all types.

Geometry demonstrates this:

```text
new Shape implementation
    ↓
existing Shape contract
    ↓
existing transition algebra
    ↓
existing NavigationSystem
```

Navigation does not recognize `FullShape`, `RampShape`, or future shape classes by type.

The same discipline applies to Control: adding a concrete command does not add a domain switch to `CommandDispatcher`; it registers one exact command class with one handler.

## Do not invent abstractions before a consumer exists

EvoForge deliberately defers decisions such as:

```text
navigation cache representation
path cost API
pathfinding algorithm
actor capability model
falling semantics
chunk size
world bounds
queued command batching semantics
multithreading model
```

A deferred decision is not missing architecture. It is a boundary intentionally left open until requirements can be measured or demonstrated by a vertical slice.

## Performance order

Optimization follows this order:

1. remove unnecessary work;
2. bound work using locality and indexes;
3. reuse derived work when evidence shows benefit;
4. remove hot-path allocations and boxing;
5. introduce specialized primitive or data-oriented storage for proven hot paths;
6. consider parallelism or SIMD only after profiling justifies them.

A custom low-level structure is not automatically better than a clear object representation. It must solve a measured problem.

## Test architectural laws, not only examples

Unit tests should cover concrete behavior, but the highest-value tests express laws that every valid implementation must obey: stale ObjectIds remain dead, terrain absence means no geometry, Shape composition is order-independent, terrain bodies are not ordinary navigation positions, structural edges never escape the 26-neighbor transition space, generic Control never depends on world domains, and world domains never depend on Control.

Property/reference tests are preferred when a simple independent implementation can validate a more optimized resolver over many deterministic mutations.

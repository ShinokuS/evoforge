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

## Narrow read contracts

Systems expose narrow read interfaces instead of exposing mutable internals.

Examples include:

```text
ObjectLookup
TransformLookup
TerrainLookup
GeometryLookup
NavigationLookup
```

A consumer should depend on the smallest semantic contract it needs. This keeps replacement of internal storage possible and makes dependency direction visible.

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

## Commands express intent

Player input, AI, scripts, tests, and scenarios should converge on the same control path:

```text
Controller
    ↓
Command
    ↓
handler / action
    ↓
authoritative systems
```

A command is intent. Normal gameplay impossibility is represented as structured rejection, not as a JVM exception. Exceptions remain for programming/configuration contract violations.

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

Unit tests should cover concrete behavior, but the highest-value tests express laws that every valid implementation must obey: stale ObjectIds remain dead, terrain absence means no geometry, Shape composition is order-independent, terrain bodies are not ordinary navigation positions, and structural edges never escape the 26-neighbor transition space.

Property/reference tests are preferred when a simple independent implementation can validate a more optimized resolver over many deterministic mutations.

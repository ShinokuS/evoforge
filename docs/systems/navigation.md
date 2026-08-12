# Navigation

## Purpose

Answer one structural question: **from this XYZ standing position, which immediate XYZ neighbors are structurally connected by current geometry?**

It deliberately does not answer whether a specific actor can use the edge, how expensive it is, whether another object occupies the destination or how to reach a distant goal.

## Public read contract

```java
int transitions(int x, int y, int z)
```

The result is a `TransitionMask` containing zero or more of the 26 immediate 3D neighbor directions.

## Dependency boundary

```text
Navigation
    ↓
GeometryLookup
    ↓
Shape
```

Navigation does not know:

- `FullShape`, `RampShape` or future concrete Shape types;
- ObjectId / WorldObject / actor abilities;
- terrain material identity;
- dynamic occupancy/reservations;
- TransitionCost;
- pathfinding algorithms;
- falling;
- rendering.

Concrete Shape recognition inside Navigation is an architectural violation.

## Resolver algebra

For one source position, Navigation reads Shapes in the current bounded geometry window. For each Shape anchor it computes the source relative to that anchor and accumulates:

```text
ports  |= shape.transitionPorts(relativeSource)
blocks |= shape.transitionBlocks(relativeSource)
```

Generic composition then resolves departures, arrivals and blocks. Missing destination support naturally means no arrival contribution and therefore no edge; Navigation does not need a concrete “destination Shape exists” test.

## Directed topology

Navigation is a directed graph. An edge bit from A to B does not imply the reverse bit exists from B to A. Every source query resolves independently.

Symmetric Full movement and bidirectional Ramp traversal are therefore results of compatible contributions in both directions rather than an enforced graph symmetry rule. Future geometry may intentionally be asymmetric.

## Movement locality versus read locality

These are different concepts.

Every structural transition is one immediate neighbor:

```text
dx ∈ [-1, 1]
dy ∈ [-1, 1]
dz ∈ [-1, 1]
not (0, 0, 0)
```

The current Shape-anchor read envelope around the standing source is:

```text
dx ∈ [-1, 1]
dy ∈ [-1, 1]
dz ∈ [-2, 1]
```

The extra lower read is required by the current supported-position role model: a destination standing position one Z below the source may be supported by terrain another Z below that destination. Reading an anchor at `sourceZ - 2` therefore still resolves an edge whose actual movement delta is only `dz = -1`.

This asymmetric read envelope follows from Shape roles; it is not a Ramp-specific exception and it does not create long-range movement.

## Solid obstruction

Shape `transitionBlocks` may remove otherwise compatible departure/arrival pairs. Solid terrain volume therefore remains non-navigable without Navigation knowing a “solid block” class.

## Relationship to cost and Movement

```text
Navigation
    → structural edge A → B exists?

TransitionCost
    → intrinsic actor-independent price of that valid edge?

Movement
    → can this actor start it and how long does it take?
```

Movement checks Navigation before requesting cost and asks Navigation again at scheduled completion before Spatial commit. World mutation can therefore invalidate a sleeping movement without Movement maintaining its own topology.

Shape traversal factors are ignored by Navigation. They follow the same role law but are consumed only by TransitionCost after structural validity is established.

## Occupancy is separate

Temporary object occupancy must never be converted into structural topology. A structurally valid edge may be temporarily unavailable while still existing in Navigation.

## No falling

Empty lower space does not become an ordinary navigation transition. If no destination-supporting Shape contributes an arrival, the edge is absent.

Future falling is an involuntary mechanic/process, not a reinterpretation of empty space as standard structural adjacency.

## No persistent topology cache yet

Navigation currently derives topology from current Geometry for each query. There is no persistent Navigation cache or invalidation lifecycle.

Future Pathfinder will be the first representative high-volume consumer. Any cache/chunk representation must be justified by that measured workload and remain behind the same `NavigationLookup` semantics.

## Boundary arithmetic

Local coordinate arithmetic is guarded against integer wrap. These guards are implementation safety, not a declaration that the world supports the entire signed-int coordinate range.

## Diagnostics and tests

The visualizer F2 overlay draws the authoritative transition mask for the selected standing cell rather than reimplementing Navigation.

Tests cover local algebra, terrain/geometry integration, directed edges, Ramp topology, mutation visibility, seeded randomized comparison against an independent reference resolver, integer-boundary arithmetic and Movement completion revalidation when a formerly valid edge disappears.

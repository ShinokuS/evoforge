# Navigation

## Purpose

Answer one structural question: **from this XYZ standing position, which immediate XYZ neighbors are structurally connected by current geometry?**

Navigation deliberately does not answer whether a specific actor can use the edge, how expensive it is, whether another object occupies the destination or how to reach a distant goal.

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

- concrete `FullShape` / `RampShape` classes;
- ObjectId / WorldObject / actor abilities;
- terrain material identity;
- Water depth or mover wading profiles;
- dynamic Occupancy/reservations;
- TransitionCost;
- Pathfinding algorithms;
- falling;
- rendering.

Concrete Shape recognition inside Navigation is an architectural violation.

## Resolver algebra

For one standing source, Navigation reads Shapes in its current local anchor envelope. For each Shape anchor it computes the source relative to that anchor and accumulates:

```text
ports  |= shape.transitionPorts(relativeSource)
blocks |= shape.transitionBlocks(relativeSource)
```

Generic composition resolves compatible departure/arrival roles and then applies blocks. Missing destination support naturally produces no arrival contribution and therefore no edge; Navigation does not need a concrete "destination Shape exists" test.

## Directed topology

Navigation is a directed graph. An edge A -> B does not imply B -> A. Every source query resolves independently.

Symmetric ordinary movement and bidirectional Ramp traversal are results of compatible contributions in both directions rather than an enforced graph-symmetry rule.

## Movement locality versus read locality

Every structural transition is one immediate neighbor:

```text
dx in [-1, 1]
dy in [-1, 1]
dz in [-1, 1]
not (0, 0, 0)
```

The current Shape-anchor read envelope around the standing source is:

```text
dx in [-1, 1]
dy in [-1, 1]
dz in [-2, 1]
```

The extra lower read follows from the supported-position role model: a destination one Z below the source may itself be supported by a terrain anchor another Z below. Read locality is therefore wider than movement locality without creating long-range edges.

## Relationship to cost, mover constraints and Movement

```text
Navigation
    -> structural edge exists?

TransitionCost
    -> actor-independent intrinsic price?

MoverTraversalConstraint
    -> may this mover use it under current dynamic facts?

Occupancy
    -> can relevant space be claimed now?

Movement
    -> start/complete the timed physical edge
```

Current Water wading is a mover-specific dynamic constraint. It filters advisory planning and Movement execution without becoming Navigation topology.

Movement asks Navigation at start and scheduled completion. World mutation can therefore invalidate a sleeping Movement action without Movement maintaining its own topology cache.

## No ordinary falling edge

Empty lower space does not become a Navigation transition merely because gravity could act there. If no destination-supporting Shape contributes an arrival, the edge is absent.

Future falling is an involuntary mechanic/process, not reinterpretation of empty space as ordinary walkable adjacency.

## Current cache boundary

Navigation itself currently derives transition masks from current Geometry on each query; it owns no persistent topology cache.

Pathfinding is already a representative high-volume consumer. Its `PathHierarchyIndex` is **Pathfinding-owned derived acceleration state**, not a Navigation cache and not a second topology owner. Exact search continues to consume authoritative `NavigationLookup`.

If profiling later proves repeated Navigation resolution itself to be the bottleneck, a cache may be introduced behind the same read contract with explicit invalidation semantics. The current hierarchy is not evidence that Navigation should duplicate its own cached graph preemptively.

## World bounds

Configured finite world containment is supplied by the shared `WorldGeometryLookup`. Coordinates outside `WorldBounds` resolve as `FullShape`, so Navigation naturally sees closed boundary geometry through the same Shape algebra.

Navigation contains no separate map-edge clamp. An unbounded runtime remains valid when no bounds are configured.

Local coordinate arithmetic is guarded against integer wrap. Those guards are implementation safety, not a declaration that every signed-int coordinate is usable in every runtime.

## Diagnostics and tests

The visualizer's **Transitions** debug option reads the authoritative transition mask for the selected standing cell; `F2` remains the keyboard shortcut. Presentation does not reconstruct Navigation.

Tests cover role algebra, terrain/Geometry integration, directed edges, Ramp topology, mutation visibility, randomized comparison against an independent reference resolver, integer-boundary arithmetic, finite-world containment and Movement completion revalidation when a formerly valid edge disappears.

# Navigation

Navigation exposes structural adjacency of world positions. It answers one question:

> From this XYZ source, which immediate XYZ neighbors are structurally connected by current geometry?

It deliberately does not answer whether a specific actor can traverse the edge, how expensive the edge is, whether another object occupies the destination, or how to find a path to a distant target.

## Public contract

```java
public interface NavigationLookup {
    int transitions(int x, int y, int z);
}
```

The result is a `TransitionMask` containing zero or more of the 26 immediate XYZ neighbor directions.

## Dependency boundary

`NavigationSystem` depends only on `GeometryLookup`.

```text
NavigationSystem
    ↓
GeometryLookup
    ↓
Shape
```

Navigation does not know:

```text
FullShape
RampShape
ObjectId
WorldObject
MovementRate
actor abilities
occupancy
TransitionCost
pathfinding algorithm
falling
terrain material identity
```

Concrete Shape checks inside Navigation are an architectural violation.

## Resolver algorithm

For one source position, Navigation reads every Shape in the current generic geometry read window. For each Shape anchor it computes:

```text
relative source = source XYZ - Shape anchor XYZ
```

and accumulates:

```text
ports  |= shape.transitionPorts(relative source)
blocks |= shape.transitionBlocks(relative source)
```

Finally:

```text
TransitionComposition.resolve(ports, blocks)
```

produces the public mask.

## Movement locality versus read locality

These are different concepts and must not be confused.

A structural edge is always one of the 26 immediate neighbors:

```text
movement delta:
    dx ∈ [-1,1]
    dy ∈ [-1,1]
    dz ∈ [-1,1]
```

The resolver read window is currently larger downward:

```text
shape-anchor offset from source:
    dx ∈ [-1,1]
    dy ∈ [-1,1]
    dz ∈ [-2,1]
```

This does not permit longer movement. It allows Navigation to hear a Shape whose terrain anchor is below the standing position that an edge ends on.

## Why `dz = -2` is necessary

Consider a Ramp standing position `B` descending diagonally to a lower Full standing position `A`:

```text
B -> A = (0,-1,-1)
```

The Full terrain cell supporting `A` is one coordinate below `A`. Relative to source `B`, that Full anchor can therefore be two Z levels below the source.

```text
B source       z = 2
A standing     z = 1
Full anchor    z = 0
```

Navigation must read that Full so it can contribute the arrival bit for `B -> A`. If it only scanned `z-1 .. z+1`, the reverse ramp edge would disappear even though the movement itself is still only one neighbor step.

The current asymmetric `[-2,+1]` Z read range follows from the current Shape role model; it is not a type-specific Ramp exception.

## Directed topology

Navigation is a directed graph.

```text
transitions(A) contains d
```

does not imply:

```text
transitions(A + d) contains -d
```

Every source query is resolved independently. Symmetric Full movement and bidirectional Ramp traversal emerge because both directions separately receive valid contributions.

This is important because geometry may intentionally be asymmetric.

## Missing destinations

There is no explicit generic test such as:

```java
if (geometryAtDestination == null) reject;
```

Instead the destination-supporting Shape is responsible for providing the arrival contribution. If no Shape supports the destination, no arrival exists and the edge naturally disappears through the transition algebra.

This principle prevents edges into empty space without coupling Navigation to concrete geometry semantics.

## Solid obstruction

Navigation also accumulates `transitionBlocks` from local geometry. A Shape may therefore invalidate an otherwise well-formed departure/arrival pair when a solid body obstructs that direction.

This is how ordinary terrain bodies remain non-navigable without Navigation needing to understand “solid block” types.

## Relationship to TransitionCost and Movement

The current runtime chain keeps three questions separate:

```text
Navigation
    -> does structural edge A -> B exist?

TransitionCostLookup
    -> what is the actor-independent intrinsic price of that valid edge?

MovementSystem
    -> can this object start it, and how many ticks does its MovementRate require?
```

`MovementSystem` always checks Navigation first. Only if the directed bit exists does it request a `TransitionCost`.

TransitionCost therefore cannot create an edge by assigning a low cost, and Navigation cannot make an edge expensive by encoding material or actor rules.

At scheduled Movement completion, `MovementActionProcessor` asks Navigation again before committing `SpatialSystem.move`. This is how a terrain/geometry mutation during a sleeping action prevents a stale movement commit.

Future Pathfinder should enumerate candidate structural edges from Navigation and price them with the **same** `TransitionCostLookup` used by authoritative Movement. It must not duplicate topology or maintain a second edge-price model.

See [Movement System](Movement-System.md) for the full timing/cost lifecycle.

## Traversal factors are not Navigation cost

`Shape` now also exposes `departureTraversalFactor` / `arrivalTraversalFactor`, but Navigation deliberately ignores those values.

They follow the same local role law as transition ports so topology and cost agree on which Shape owns which side of a directed edge. `TransitionCostCalculator`, not Navigation, consumes the factors after the edge is valid.

This keeps structural connectivity independent from numeric movement price.

## No falling

An absent structural edge is simply absent. Navigation does not reinterpret a missing floor as permission to fall.

For example:

```text
Ramp -> empty lower space
```

has no normal navigation edge because no lower Shape supplies the required arrival.

If falling is added later, it must be modeled as a separate involuntary mechanic/process rather than by treating empty space as ordinary structural adjacency.

## No persistent cache

Current Navigation calculates topology from current Geometry on every query. There is no persistent topology cache and therefore no cache invalidation contract.

This is deliberate. Timed Movement now provides a real correctness consumer, but Pathfinder will provide the first representative high-volume topology workload. Caching should be designed only after that workload is measured and reuse is shown to be worthwhile.

Possible future implementations remain open:

```text
no cache
bounded query cache
chunk-local derived topology
region-derived topology
another measured representation
```

The public `NavigationLookup` contract should survive those internal changes.

## Boundary arithmetic

The resolver protects local coordinate arithmetic from integer wrap at `Integer.MIN_VALUE` and `Integer.MAX_VALUE`. Those tests are implementation-safety checks, not a declaration that the world supports the full signed-int coordinate range.

## Testing

Navigation currently has several complementary test layers:

- local resolver unit tests;
- integration with Geometry and Terrain;
- directed-edge contract tests;
- Ramp integration and hardening scenarios;
- mutation visibility tests;
- seeded randomized comparison against an independent reference resolver;
- integer-boundary arithmetic tests;
- Movement completion revalidation after a formerly valid edge disappears.

The Shape role-contract suite also checks that traversal-factor ownership stays aligned with the same departure/arrival roles used by Navigation.

See [Testing Strategy](Testing-Strategy.md) for the complete testing model.

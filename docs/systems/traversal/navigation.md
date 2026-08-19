# Navigation

## In plain language

Navigation answers only the **structural adjacency** question:

> From this standing cell, which immediately neighboring standing cells are physically connected by the current Terrain geometry?

It does not know who is moving, whether Water is too deep, whether a destination is occupied, how expensive the edge is, or how to reach a far-away goal.

This narrowness is important: a ramp, bridge-like future Shape or other geometry should be able to expose the same neutral structural facts without Navigation containing class-specific rules.

## Current status

The public read contract returns a `TransitionMask` of zero or more of the **26 immediate 3D neighbor directions** around one standing coordinate.

```java
int transitions(int x, int y, int z)
```

Navigation derives this result from current `GeometryLookup`/`Shape` facts on demand. It does not currently own a persistent exact-topology cache.

## Dependency boundary

```text
Navigation
    ↓
GeometryLookup
    ↓
Shape transition facts
```

Navigation intentionally does not depend on:

- concrete `FullShape`/`RampShape` classes;
- actors or object definitions;
- terrain material identity;
- Water depth/wading;
- Occupancy/reservations;
- transition pricing;
- Pathfinding;
- rendering.

If adding a new Shape requires `if shape instanceof NewShape` in Navigation, the Shape contract or composition is wrong.

## Directed graph semantics

Navigation is a **directed graph**.

```text
A -> B exists
```

does not automatically imply:

```text
B -> A exists
```

Both directions are resolved independently from Shape contributions. Symmetric flat movement and bidirectional ramp traversal happen because compatible geometry contributes the corresponding roles in both queries—not because Navigation force-adds the reverse edge.

## Transition algebra

For one source standing coordinate, Navigation evaluates relevant Shape anchors in its local read envelope. Each Shape is queried with source position relative to that anchor and contributes structural role masks.

Conceptually:

```text
ports  |= shape.transitionPorts(relativeSource)
blocks |= shape.transitionBlocks(relativeSource)
```

The Shape contract distinguishes departure/arrival role ownership; generic resolution composes compatible roles and then removes blocked directions.

A destination with no supporting Shape simply contributes no compatible arrival role. Navigation does not need a separate concrete “destination exists” branch.

## Movement locality

Every Navigation edge is one immediate 3D neighbor:

```text
dx ∈ {-1,0,+1}
dy ∈ {-1,0,+1}
dz ∈ {-1,0,+1}
(dx,dy,dz) != (0,0,0)
```

That gives 26 possible direction vectors.

This is **edge distance**, not the same as how far Navigation may need to read supporting Shape anchors.

## Shape read locality

With the current supported-standing-position convention `S=(0,0,1)`, arrival support for a destination below the source can require reading a Terrain anchor another cell lower.

Current structural Shape-anchor read envelope around the standing source is:

```text
X: [-1,+1]
Y: [-1,+1]
Z: [-2,+1]
```

This wider read radius does not create long-range transitions; all emitted edges are still immediate neighbors.

## Surface continuity

Current full-cell/ramp topology uses neutral top-surface boundary geometry to reject false same-level cardinal joins.

For example, two side-by-side parallel ramps can join if their world-space boundary surface lines match, while a sloping ramp side does not automatically join an unrelated flat block merely because both occupy neighboring anchors.

Navigation consumes the neutral surface-continuity fact from Geometry. It does not branch on ramp orientation/class itself.

## Where other traversal decisions happen

The full execution chain is deliberately split:

```text
Navigation
  structural edge exists?
       ↓
TransitionCost
  actor-independent intrinsic price?
       ↓
MoverTraversalConstraint
  can this mover use it under current environment?
       ↓
Occupancy
  can required destination space be claimed now?
       ↓
Movement
  start/complete the timed edge
```

### Water example

Deep Water does not delete a Navigation edge. A Cow may be unable to wade through it while a future swimmer could still use the same structural space under different locomotion rules.

That is why Water wading is a mover constraint, not Navigation topology.

## No ordinary falling edges

Empty space below a standing position does not become an ordinary Navigation edge merely because gravity could make an object fall.

Without destination support/arrival contribution, the structural walking edge is absent.

Future falling is an involuntary physical process/mechanic, not “walking to an unsupported lower cell”.

## Mutation visibility and revisions

Navigation derives current masks from current Geometry. Movement re-asks Navigation at both edge start and completion.

Therefore a Terrain/Shape change can invalidate a sleeping movement without Movement storing a duplicate topology graph.

Landscape traversal changes expose a monotonic revision used by Pathfinding to detect stale suspended searches/caches. That revision is traversal observation metadata, not Navigation-owned alternate truth.

## Pathfinding hierarchy is not Navigation ownership

Pathfinding currently owns a `PathHierarchyIndex` acceleration cache over coarse 3D clusters.

```text
NavigationLookup = structural truth
PathHierarchyIndex = derived Pathfinding acceleration
```

A negative coarse connectivity result can prove no structural route crosses the required cluster graph; a positive result still delegates to exact cell-level search.

Do not add a second Navigation cache just because Pathfinding has a derived cache. Add one only if profiling proves Navigation resolution itself is the bottleneck and invalidation semantics remain exact.

## Finite world bounds

Navigation has no special `if x == mapEdge` code.

When `WorldBounds` are configured, shared `WorldGeometryLookup` returns `FullShape` outside the box, so ordinary Shape algebra naturally produces no route through the physical boundary.

Without configured bounds, the runtime retains unbounded coordinate semantics.

Generated/unloaded streaming state is a different future concern and must not be silently treated as open empty space.

## Invariants

- Navigation contains only structural geometry topology.
- Edges are directed and immediate-neighbor only.
- Generic resolution depends on neutral Shape facts, not concrete Shape classes.
- Actor/environment restrictions do not mutate structural topology.
- Occupancy/cost/path search remain separate concerns.
- Current Geometry changes are visible on the next Navigation query.
- Finite containment enters through Geometry, not duplicated map-edge checks.

## Current limitations

Navigation currently does not model:

- falling;
- jumping/climbing/swimming/flying support semantics;
- multi-cell actor footprints;
- moving platforms/dynamic continuous collision;
- loaded/unloaded streaming topology;
- actor-specific locomotion topology.

Those should extend or compose the smallest real structural contract when a concrete consumer exists.

## Code and tests

Primary implementation lives under the Navigation/geometry-related world packages and consumes `Shape` contracts from `world.mechanics.geometry`.

Tests cover role algebra, directionality, ramps/surface continuity, terrain mutation visibility, randomized reference comparison, integer-boundary safety, finite-world closure and Movement completion invalidation.

The visualizer's transition debug mode reads the authoritative `TransitionMask`; it does not reconstruct topology.

## Sources

**Internal EvoForge design.** The directed Shape-role algebra is project-specific.

See [Geometry](../foundations/geometry.md), [Transition Cost](traversal-cost.md), [Movement](movement.md), [Pathfinding](pathfinding.md), and [ADR-002](../../decisions/002-shape-transition-algebra.md).

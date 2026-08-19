# Transition Cost

## In plain language

Once Navigation says that two neighboring standing cells are structurally connected, Transition Cost answers: **how expensive is that edge in the world itself?**

The price is actor-independent. A rough surface can cost more than a normal one; diagonal movement covers more geometric distance than cardinal movement. A fast actor and a slow actor still agree on the same intrinsic edge price—MovementRate later converts that price into time.

Transition Cost never creates an edge and never decides whether a specific mover can wade through Water.

## Current status

The current calculator prices one already-valid directed adjacent edge from:

```text
source surface traversal cost
source Shape departure factor
destination surface traversal cost
destination Shape arrival factor
3D grid transition length
```

The same `TransitionCostLookup` is consumed by Movement and Pathfinding.

## Position in traversal pipeline

```text
Navigation
  does A -> B structurally exist?
       ↓
TransitionCost
  what is its intrinsic price?
       ↓
Pathfinding
  which route minimizes total intrinsic price?
       ↓
MovementRate
  how much simulation time does this actor need for that accepted price?
```

Dynamic mover restrictions and Occupancy are separate.

## Exact cost model

For a valid adjacent transition `A -> B`, define:

```text
surfaceA = source Terrain SurfaceTraversalCost
surfaceB = destination Terrain SurfaceTraversalCost

departureFactor = source Shape's factor for this departure role
arrivalFactor   = destination Shape's factor for this arrival role
```

Local contributions are conceptually:

```text
localA = surfaceA * departureFactor
localB = surfaceB * arrivalFactor
```

Then the edge cost is the geometric transition length multiplied by the average of the two local endpoint contributions, with the fixed-point neutral scales divided out.

Conceptually:

```text
TransitionCost(A -> B)
    = lengthFactor(direction)
      * average(localA, localB)
```

Production calculation uses positive integer arithmetic and deterministic final rounding rather than repeatedly rounding each intermediate factor.

Because both support cells contribute, an interior cell on path:

```text
A -> B -> C
```

contributes approximately one full surface price across its incoming/outgoing halves when Shape factors/length are neutral/cardinal.

## Fixed-point scales

Current neutral scales:

```text
surface traversal baseline = 1000
Shape traversal factor     = 1000 = neutral 1.0
Grid length scale          = 1000 = cardinal unit
```

3D immediate-neighbor geometric lengths:

```text
1 changed axis  -> 1000  ≈ 1
2 changed axes  -> 1414  ≈ sqrt(2)
3 changed axes  -> 1732  ≈ sqrt(3)
```

Authoritative cost calculation uses integers, not floating point.

## Surface-cost ownership

Base `SurfaceTraversalCost` belongs to the Landscape definition supporting the standing coordinate.

Therefore adding a new ordinary material with a different intrinsic surface cost is normally **data-only**. Transition Cost does not gain:

```text
if sand ...
if granite ...
```

branches.

If a valid priced edge uses supporting Terrain whose definition lacks required traversal data, that is broken content/bootstrap configuration and fails loudly rather than inventing a fallback cost.

## Shape-factor ownership

Shape traversal factors use the same local departure/arrival ownership idea as structural ports.

```text
ShapeTraversalFactor.NONE    = 0     // Shape does not own queried role
ShapeTraversalFactor.NEUTRAL = 1000  // owned role has no intrinsic multiplier
```

A Shape sees only its own local role; it does not inspect neighboring material/Shape and calculate the whole edge.

Current full/ramp Shapes use neutral factors. Ramp transitions already change the actual 3D displacement vector, so their Z component influences grid length. EvoForge does not add a universal arbitrary uphill/downhill surcharge.

A future Shape may expose a real intrinsic factor without modifying the generic calculator.

## Actor independence

The calculator receives no:

- `ObjectId`;
- MovementRate;
- species/content class;
- Water-wading tolerance;
- AI preference.

This is deliberate.

Two actors may execute the same intrinsic edge at different speeds because their `MovementRate` differs. One actor may be forbidden by current Water while another is allowed. Neither case changes the underlying structural edge price.

Future actor-specific terrain affinity should be a separate mover capability/policy if real gameplay needs it; it should not corrupt the universal transition cost.

## Directionality

Costs are directed because source departure and destination arrival contributions are independent.

Current common Terrain/Shape combinations often produce symmetric values, but the contract allows a future asymmetric local Shape factor without redesigning all consumers.

## Pathfinding lower bound

Exact A* needs a guaranteed positive lower bound on any traversable edge.

`TransitionCostLowerBoundCalculator` combines:

- the minimum registered surface traversal cost;
- the conservative minimum Shape traversal factor;
- the minimum immediate grid length.

The resulting `minimumEdgeCostUnits()` is used by Pathfinding's Chebyshev heuristic:

```text
h(n) = minimumEdgeCost * max(|dx|,|dy|,|dz|)
```

If a future Shape can contribute a lower factor, its Geometry lower-bound contract must reflect it; otherwise the heuristic could overestimate and A* optimality would be invalid.

## Invariants

- Calculate cost only for an already-valid adjacent Navigation edge.
- Source and destination supporting surfaces both contribute.
- Generic cost logic never branches on concrete Shape/material names.
- Required definition data has no hidden fallback.
- Movement and Pathfinding consume the same intrinsic price.
- MovementRate converts price to time but does not redefine price.
- Dynamic mover/environment constraints do not mutate TransitionCost.
- Lower-bound computation remains conservative as new Shapes/materials are added.

## Current limitations

The current model does not yet contain:

- actor-specific terrain preference/skill;
- fatigue/load effects;
- wet/muddy dynamic surface cost;
- crowding cost;
- directional wind/current cost;
- separate energy versus time costs.

Those are distinct future mechanics/queries and should not be packed into one universal scalar until concrete consumers prove the needed model.

## Code and tests

Primary implementation lives under:

```text
simulation/.../world/mechanics/traversal/
```

Tests cover endpoint averaging, grid lengths, Shape-factor role ownership, missing-definition failure, lower-bound validity and Pathfinding optimality against zero-heuristic reference search.

## Sources

**Internal EvoForge design.** The fixed-point endpoint/Shape/grid pricing model is project-specific.

The square-root length approximations are ordinary discrete Euclidean neighbor lengths; Pathfinding's use of the resulting lower bound is documented with A* sources in [Pathfinding](pathfinding.md).

See [Definitions](../foundations/definitions.md), [Geometry](../foundations/geometry.md), [Navigation](navigation.md), [Movement](movement.md), and [References](../../references.md).

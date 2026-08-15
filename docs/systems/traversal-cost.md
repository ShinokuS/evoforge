# Transition Cost

## Purpose

Price an already structurally valid directed edge using the same actor-independent model consumed by Movement and Pathfinding.

## Ordering

```text
Navigation      does A -> B structurally exist?
TransitionCost  what is the intrinsic price of that valid edge?
Pathfinding     which disposable route has the lowest intrinsic total price?
Movement        how long does this actor take to execute one accepted edge price?
```

TransitionCost never creates or authorizes an edge.

## Inputs

For one directed adjacent transition `A -> B`, the calculator combines:

- source terrain `SurfaceTraversalCost`;
- source Shape departure traversal factor;
- destination terrain `SurfaceTraversalCost`;
- destination Shape arrival traversal factor;
- geometric `GridTransitionLength`.

Conceptually:

```text
localA = surfaceCost(A) * departureFactor(shapeA, direction)
localB = surfaceCost(B) * arrivalFactor(shapeB, direction)

TransitionCost(A -> B)
    = lengthFactor(direction)
      * average(localA, localB)
```

The implementation evaluates the fixed-point expression with positive integer arithmetic and performs deterministic final rounding rather than repeatedly rounding intermediate terms.

Both supported cells contribute. With neutral Shape factors and cardinal length, a path `A -> B -> C` makes interior cell B contribute one full surface price across its two neighboring edges.

## Fixed-point scales

Current neutral scales are:

```text
surface traversal baseline = 1000
Shape traversal factor     = 1000 = 1.0
Grid length scale          = 1000
```

Grid length values are:

```text
one changed axis    = 1000   ~= 1
two changed axes    = 1414   ~= sqrt(2)
three changed axes  = 1732   ~= sqrt(3)
```

Authoritative cost calculation does not use floating point.

## Surface cost ownership

Base surface price belongs to landscape definition data (`traversal.cost`), not to Shape and not to the actor. New terrain materials normally change definition data only; they do not add cases to Movement, TransitionCost or Pathfinding.

Missing traversal data for support terrain participating in a valid structural edge is broken configuration and fails loudly rather than silently inventing a fallback price.

## Shape traversal factors

Shape traversal characteristics follow the same source/departure and destination/arrival ownership law as structural transition ports.

A Shape contributes only its own local factor. It does not inspect the neighbor or calculate the other side of the edge.

`ShapeTraversalFactor.NONE = 0` means the Shape does not own the queried role. `NEUTRAL = 1000` means no intrinsic multiplier beyond surface/grid cost.

Current `FullShape` and cardinal `RampShape` use neutral factors for the roles their topology exposes. Ramp topology already changes actual displacement, including Z where relevant, so grid length accounts for that geometry; there is no arbitrary uphill/downhill surcharge.

A future Shape may override a real intrinsic local geometry factor without changing the generic calculator.

## Actor independence

TransitionCost receives no `ObjectId`, MovementRate, species or locomotion mode. Different actors agree on intrinsic structural edge price; MovementRate changes execution time.

Current Water-wading restrictions demonstrate the complementary actor/environment boundary: they may filter a route or reject Movement under current Water, but they do not rewrite the actor-independent TransitionCost table.

Future actor/surface affinity may be another separate mover capability if a real consumer requires it.

## Directionality

Costs are directed because departure and arrival contributions are independent. This leaves room for asymmetric geometry while keeping the consumer contract unchanged.

## Lower bound for Pathfinding

`TransitionCostLowerBoundCalculator` derives a conservative positive global lower bound from registered landscape traversal costs and minimum Shape traversal factors. Pathfinding uses that bound to build an admissible heuristic; it does not maintain a second pricing model.

If a new Shape can contribute a factor below the current minimum, its Geometry-side lower-bound contract must reflect that so A* remains admissible.

## Invariants

- calculate cost only for an already-valid adjacent Navigation edge;
- source and destination support are both represented;
- no central cost logic branches on concrete Shape type;
- required definition data has no hidden fallback;
- Pathfinding consumes this same `TransitionCostLookup` rather than another price table;
- MovementRate converts intrinsic cost to actor time but never redefines edge price;
- dynamic mover restrictions remain separate from the actor-independent cost model.

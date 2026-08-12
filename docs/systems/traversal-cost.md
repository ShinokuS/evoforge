# Transition Cost

## Purpose

Price an already structurally valid directed edge using the same actor-independent model consumed by Movement and future Pathfinder.

## Ordering

```text
Navigation      does A → B structurally exist?
TransitionCost  what is the intrinsic price of that valid edge?
Movement        how long does this actor take to execute that price?
```

TransitionCost never creates or authorizes an edge.

## Inputs

For one directed adjacent transition `A → B`, the calculator combines:

- source terrain `SurfaceTraversalCost`;
- source Shape departure traversal factor;
- destination terrain `SurfaceTraversalCost`;
- destination Shape arrival traversal factor;
- geometric `GridTransitionLength`.

Conceptually:

```text
localA = surfaceCost(A) × departureFactor(shapeA, direction)
localB = surfaceCost(B) × arrivalFactor(shapeB, direction)

TransitionCost(A → B)
    = lengthFactor(direction)
      × average(localA, localB)
```

The implementation evaluates the fixed-point expression with positive integer arithmetic and performs one deterministic half-up rounding at the final transition-cost boundary rather than repeatedly rounding intermediate terms.

Both supported cells contribute. With neutral Shape factors and cardinal length, a path `A → B → C` makes interior cell B contribute one full surface price across its two neighboring edges.

## Fixed-point scales

Current neutral scales are:

```text
surface traversal baseline = 1000
Shape traversal factor     = 1000 = 1.0
Grid length scale          = 1000
```

Grid length values are:

```text
one changed axis    = 1000   ≈ 1
two changed axes    = 1414   ≈ √2
three changed axes  = 1732   ≈ √3
```

Authoritative cost calculation does not use floating point.

## Surface cost ownership

Base surface price belongs to landscape definition data (`traversal.cost`), not to Shape and not to the actor. New terrain materials normally change definition data only; they do not add cases to Movement or TransitionCost.

Missing traversal data for support terrain participating in a valid structural edge is broken configuration and fails loudly rather than silently inventing a fallback price.

## Shape traversal factors

Shape traversal characteristics follow the same source/departure and destination/arrival ownership law as structural transition ports.

A Shape contributes only its own local factor. It does not inspect the neighbor or calculate the other side of the edge.

`ShapeTraversalFactor.NONE = 0` means the Shape does not own the queried role. `NEUTRAL = 1000` means no intrinsic multiplier beyond surface/grid cost.

Current `FullShape` and cardinal `RampShape` use neutral factors for the roles their topology exposes. Ramp topology already changes the actual displacement (including Z where relevant), so grid length accounts for that geometry; no arbitrary uphill/downhill surcharge exists today.

A future Shape may override a real intrinsic local geometry factor without changing the generic calculator.

## Actor independence

TransitionCost receives no `ObjectId`, MovementRate, species or locomotion mode. Different actors currently agree on intrinsic edge price; MovementRate only changes execution time.

Future actor/surface affinity is a separate capability interaction and must not be smuggled into the current actor-independent contract.

## Directionality

Costs are directed because departure and arrival contributions are independent. This leaves room for asymmetric future geometry while keeping the consumer contract unchanged.

## Invariants

- calculate cost only for an already-valid adjacent Navigation edge;
- source and destination support are both represented;
- no central cost logic branches on concrete Shape type;
- required definition data has no hidden fallback;
- future Pathfinder must consume this same `TransitionCostLookup` rather than maintain another price table;
- MovementRate converts cost to time but never redefines edge price.

# Water Traversal

## In plain language

Water Traversal lets the **same structural path** be usable by one mover and unusable by another because of current Water depth.

A shallow puddle may be walkable for a Cow; a deeper cell may be too deep. Navigation still describes the structural ground connection. Water remains finite liquid. The mover's definition says how much Water it can wade through.

This keeps “where a path physically exists” separate from “whether this particular actor can use it right now”.

## Current status

The implemented slice models terrestrial **wading passability** end-to-end:

- mover definition can opt into a maximum Water depth;
- current finite Water + Geometry convert volume into local depth;
- MoveTo can avoid already-too-deep destinations while planning;
- Movement checks the exact same semantic constraint at edge start;
- Movement checks it again at completion if Water changed during travel.

Not implemented here: swimming, boats, drowning, current forces or shallow-Water speed penalties.

## Ownership

```text
Navigation
  structural edge exists

Liquid/Water
  finite current Water quantity

Geometry / CellSpace
  free-space shape and volume -> depth conversion

WaterWadingProfile
  mover-definition tolerance

WaterWadingConstraint
  current mover/environment passability

Pathfinding / MoveTo
  advisory route filtering

Movement
  authoritative edge start + commit
```

Water never edits Navigation topology just because one mover dislikes the depth.

## Definition-driven tolerance

A mover can define:

```json
{
  "aspects": {
    "waterWading": {
      "maxDepth": 250000
    }
  }
}
```

or equivalent assembly configuration.

`maxDepth` uses normalized `CellSpace` local-height units:

```text
0         = only dry destinations
1_000_000 = one full standing-cell height
```

No `waterWading` aspect means Water-neutral traversal under the current contract. There is no hidden species-name fallback.

This is explicitly a terrestrial wading capability. Swimming/water-surface locomotion requires different support/topology semantics and should not be represented as an enormous `maxDepth` hack.

## Water amount is not automatically depth

Free Water is stored as finite volume. Geometry may have nontrivial internal free-space shape, so equal volume can correspond to different heights.

The constraint therefore uses:

```text
current Water volume at destination
        ↓
CellSpace.surfaceHeight(destinationShape, volume)
        ↓
local Water surface/depth height
```

For a normal empty/full free standing cell this behaves intuitively. For a ramp-shaped cell, the `freeVolumeBelow(h)` profile changes the volume-to-height relationship.

This keeps fluid depth calculation in neutral Geometry rather than putting Shape-specific branches inside Water Traversal.

## More than one cell deep

If the destination standing cell is filled to full local height and the cell above also contains Water, the destination is treated as deeper than one cell.

If Geometry changes so existing Water temporarily exceeds the new capacity/profile, traversal is conservative: the destination is considered too deep until hydraulic redistribution resolves the state.

## Destination-only rule

The current constraint checks the **destination** depth.

It does not reject an actor merely because its current source cell has become deeper than its tolerance.

That means rising Water does not mechanically trap an actor forever:

```text
actor stands in now-overdeep Water
        ↓
neighbor destination is shallower/dry
        ↓
leaving edge can still be allowed
```

This is intentional current wading semantics.

## Authoritative Movement checks

The same production `WaterWadingConstraint` instance is supplied to both concrete Movement boundaries:

```text
MovementSystem.startStep(...)
        ↓
check destination depth now

... simulation time passes ...

MovementActionProcessor completion
        ↓
check destination depth again
```

Consequences:

- if Water is already too deep, the edge never starts;
- if Water rises during the timed edge, final Spatial commit is rejected and the actor remains at source;
- if planning predicted an allowed edge but the world changes, execution remains safe.

Compatibility/test Movement composition can use `ALLOW_ALL` when no dynamic mover constraint is configured.

## MoveTo advisory filtering

MoveTo owns a narrow query-constraint provider extension rather than importing Water directly.

Production composition is:

```text
Water + Geometry + mover definition
           ↓
WaterWadingConstraint
           ↓
MoverTraversalQueryConstraintProvider
           ↓
MoveToQueryConstraintProvider
           ↓
PathQuery.constraint
```

If a caller already supplied another `PathTransitionConstraint`, mover Water restriction is composed with it rather than replacing it.

Pathfinding can therefore avoid currently-too-deep destinations when a dry/shallow detour exists.

The returned route is still disposable; Movement rechecks each real edge.

## Why raw Water changes do not invalidate Navigation

Hydraulic simulation may adjust Water volumes frequently. Many tiny volume changes do not cross the mover's semantic wading threshold.

EvoForge therefore does not increment Landscape/Navigation traversal revision for every Water change.

Current production MoveTo search runs to a terminal computational result without simulation-time advancement between search chunks, so it reads one current Water state for that planning episode and immediately begins execution.

If path search later truly spans authoritative ticks, invalidation should be threshold/region-aware through the query constraint's revision semantics—not “all paths stale whenever any Water cell changes anywhere”.

## Finite bounds

Water Traversal has no special map-edge rule.

Shared `WorldGeometryLookup` closes space outside configured `WorldBounds`, so Navigation/Movement/liquid systems naturally agree on containment.

## Invariants

- Water quantity and Navigation topology remain separate facts.
- Wading tolerance is definition data, not a content-name switch.
- Volume is converted to depth through neutral Geometry.
- Only destination Water depth is constrained by current wading semantics.
- Planning and execution share one semantic rule.
- Advisory planning never replaces authoritative Movement revalidation.
- Raw Water micro-changes do not become structural traversal revision churn.

## Current limitations

Deliberately absent:

- shallow-Water speed/cost penalty;
- swimming;
- water-surface/boat Navigation;
- current-force/slip/knockback;
- drowning/breathing;
- actor body-volume interaction;
- global fluid-based path invalidation;
- temporal fluid forecasting/planning.

## Code and tests

Primary code lives with mover traversal/Water-wading integration and is composed in `SimulationAssembly` into Movement and MoveTo query adapters.

Coverage includes depth thresholds, missing-profile Water-neutral behavior, >1-cell detection, escape from overdeep source Water, ramp/Shape depth conversion, Movement start and completion rejection, composed path constraints and rain-created detour cases.

## Sources

**Internal EvoForge design.** This is a deliberately simple finite-Water wading mechanic, not a biomechanical swimming/drag model.

See [Water](../environment/water.md), [Geometry](../foundations/geometry.md), [Movement](movement.md), [Pathfinding](pathfinding.md), and [Definitions](../foundations/definitions.md).

# Water Traversal

## Purpose

Let current finite Water influence terrestrial movement without turning Water into Navigation state or invalidating pathfinding on every hydraulic micro-change.

The first slice models **wading passability only**. It deliberately does not model swimming, boats, drowning, current forces or shallow-water speed penalties.

## Ownership

The relevant facts remain separate:

```text
Navigation              -> geometric edge exists
Water                    -> finite liquid quantity
Geometry                 -> free-space height profile
WaterWadingProfile       -> mover definition tolerance
MoverTraversalConstraint -> may this mover use this edge now?
Movement                 -> authoritative edge execution
Pathfinding              -> disposable advisory route
```

Water does not publish Navigation edges and raw Water changes do not increment `TraversalRevisionLookup`.

## Definition-driven capability

An object definition may opt into terrestrial Water restrictions with:

```json
{
  "aspects": {
    "waterWading": {
      "maxDepth": 250000
    }
  }
}
```

`maxDepth` uses normalized `CellSpace` height units:

```text
0         = dry destinations only
1_000_000 = one full standing cell of Water
```

The profile is explicit. A mover with no `waterWading` aspect keeps the previous Water-neutral traversal behavior; there is no hidden species/material fallback.

This slice intentionally represents terrestrial wading rather than a universal fluid-locomotion profile. Swimming and waterborne movement need different Navigation/support semantics and should be introduced by their first real consumers instead of overloading `maxDepth` now.

## Destination depth

`WaterWadingConstraint` reads Water at the destination standing coordinate and converts its finite volume into a local surface height through neutral `CellSpace.surfaceHeight(...)` geometry.

Therefore the rule is not `water amount == depth`: a future/nontrivial Shape may distribute free volume differently over height.

If the destination standing cell is full and the cell above also contains Water, the destination is classified as deeper than one cell. Existing Water that exceeds newly changed Geometry capacity is also classified conservatively as too deep until hydraulic flow relocates it.

Only the **destination** is constrained. A mover already standing in water deeper than its tolerance may still leave for a shallower or dry destination. This avoids mechanically trapping an actor merely because Water rose around it.

## Planning versus execution

The same `MoverTraversalConstraint` is used at three points:

```text
MoveTo PathQuery
      -> advisory filtering

MovementSystem.startStep
      -> authoritative start validation

MovementActionProcessor.complete
      -> authoritative commit revalidation
```

A route therefore avoids water that is already too deep when it is planned. If Water rises after planning, the next real edge is rejected. If Water rises while a timed edge is in progress, commit is rejected and the mover remains at the source.

Pathfinding remains advice; Movement remains authority.

## Revisions and high-churn Water

Mover/environment constraints are intentionally **not** folded into the landscape traversal revision. Hydraulic relaxation can change Water every tick, while a mover's semantic result may remain unchanged throughout those micro-changes.

`MoveToSystem` composes Water/mover permission into its query-local `PathTransitionConstraint` but preserves only an explicit caller-provided constraint revision. Current production MoveTo path searches run synchronously on the simulation thread, then every executed edge is revalidated live.

If path search later becomes genuinely resumable across simulation ticks, the next step should be a local/semantic invalidation mechanism for relevant water-depth threshold crossings, not a global raw-Water revision.

## Deliberately absent

The current slice does not implement:

- shallow-water movement cost or speed penalties;
- swimming locomotion;
- boat/water-surface Navigation;
- current force, slipping or knockback;
- drowning or breathing;
- mover height/body-volume collision with Water;
- path cache invalidation from raw Water amount changes;
- world-bound containment for Water.

The sandbox still has no authoritative finite world bounds. Tests/scenarios that need retained surface Water should provide containing Terrain until world generation owns world limits.

## Tests

Headless tests cover:

- mover-specific shallow/deep thresholds;
- Water-neutral behavior when the optional aspect is absent;
- deeper-than-one-cell detection;
- escape from an already over-deep source;
- generic Shape free-space depth without concrete Shape checks;
- definition compiler validation/freezing;
- Movement rejection before scheduling;
- Movement commit revalidation after environmental change;
- MoveTo query composition without replacing caller revision semantics.

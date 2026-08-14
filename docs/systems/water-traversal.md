# Water Traversal

## Purpose

Let current finite Water influence terrestrial movement without turning Water into Navigation state or invalidating pathfinding on every hydraulic micro-change.

The current slice models mover-specific **wading passability** end to end: current Water informs advisory MoveTo planning and is revalidated authoritatively when a real Movement edge starts and commits. Swimming, boats, drowning, current forces and shallow-water speed penalties remain outside this slice.

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

Production scenario composition exposes the same fact through:

```java
assembly.waterWading(definitionId, 250_000);
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

Therefore the rule is not `water amount == depth`: a nontrivial Shape may distribute free volume differently over height.

If the destination standing cell is full and the cell above also contains Water, the destination is classified as deeper than one cell. Existing Water that exceeds newly changed Geometry capacity is also classified conservatively as too deep until hydraulic flow relocates it.

Only the **destination** is constrained. A mover already standing in water deeper than its tolerance may still leave for a shallower or dry destination. This avoids mechanically trapping an actor merely because Water rose around it.

## Authoritative execution

The same `WaterWadingConstraint` instance is checked at both execution boundaries:

```text
MovementSystem.startStep
      -> authoritative start validation

MovementActionProcessor.complete
      -> authoritative commit revalidation
```

If Water is already too deep, the edge is never scheduled. If Water rises while a timed edge is in progress, commit is rejected and the mover remains at the source.

The existing constructors preserve `ALLOW_ALL`, so movers/systems that do not opt into a dynamic traversal constraint keep previous behavior exactly.

## Advisory planning

`MoveToSystem` remains a Movement-domain orchestrator and deliberately does not import the Traversal or Water domains. Instead it owns the narrow movement-local `MoveToQueryConstraintProvider` extension point.

Production composition adapts the live mover constraint through `MoverTraversalQueryConstraintProvider`:

```text
Water + Geometry + mover definition
            |
            v
WaterWadingConstraint
            |
            v
MoverTraversalQueryConstraintProvider
            |
            v
MoveToQueryConstraintProvider
            |
            v
PathQuery.constraint
```

The adapter composes the mover-specific restriction with any existing caller-provided `PathTransitionConstraint`. Existing search/visibility constraints are therefore preserved rather than replaced.

A route planned while a destination is already too deep avoids that destination when an alternative path exists. The route is still disposable advice: every selected edge passes through the authoritative Movement checks above.

## Revisions and high-churn Water

Mover/environment facts are intentionally **not** folded into the landscape traversal revision. Hydraulic relaxation can change Water every tick while a mover's semantic passability result may remain unchanged across many micro-changes.

The query adapter preserves only the caller's explicit constraint revision. Current production MoveTo searches run synchronously on the simulation thread, so current Water is read while that query is evaluated and the resulting route is immediately handed to Movement.

If path search later becomes genuinely resumable across simulation ticks, invalidation should be local and semantic: relevant water-depth threshold crossings should stale affected work, not every raw Water quantity update across the world.

## Runtime composition

`SimulationAssembly` owns the definition table and creates one `WaterWadingConstraint` when a simulation starts. The same instance is supplied to:

```text
MovementSystem
MovementActionProcessor
MoverTraversalQueryConstraintProvider -> MoveToSystem
```

This keeps planning and execution on one semantic rule without moving Water state into Navigation or duplicating threshold logic.

## Deliberately absent

The current slice does not implement:

- shallow-water movement cost or speed penalties;
- swimming locomotion;
- boat/water-surface Navigation;
- current force, slipping or knockback;
- drowning or breathing;
- mover height/body-volume collision with Water;
- global path cache invalidation from raw Water amount changes;
- world-bound containment for Water.

The sandbox still has no authoritative finite world bounds. Tests/scenarios that need retained surface Water should provide containing Terrain or limit the scenario before runoff can escape into ungenerated space until world generation owns world limits.

## Tests

Headless and production integration tests cover:

- mover-specific shallow/deep thresholds;
- Water-neutral behavior when the optional aspect is absent;
- deeper-than-one-cell detection;
- escape from an already over-deep source;
- generic Shape free-space depth without concrete Shape checks;
- definition compiler validation/freezing;
- Movement rejection before scheduling;
- Movement commit revalidation after environmental change;
- query-provider composition without a Traversal import in `MoveToSystem`;
- preservation of caller query revision;
- a rain-created deep Water cell being avoided by MoveTo when a dry detour exists;
- Water arriving during a timed edge preventing the authoritative commit.

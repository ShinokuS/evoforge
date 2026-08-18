# Water Traversal

## Purpose

Let current finite Water influence terrestrial movement without turning Water into Navigation topology or invalidating pathfinding on every hydraulic micro-change.

The current slice models mover-specific **wading passability** end to end: current Water informs advisory MoveTo planning and is revalidated authoritatively when a real Movement edge starts and commits. Swimming, boats, drowning, current forces and shallow-Water speed penalties remain outside this slice.

## Ownership

```text
Navigation              structural edge exists
Water                   finite liquid quantity
Geometry                free-space height profile
WaterWadingProfile      mover-definition tolerance
MoverTraversalConstraint may this mover use this edge now?
Movement                authoritative edge execution
Pathfinding             disposable advisory route
```

Water does not publish Navigation edges and raw Water changes do not increment the landscape traversal revision.

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

Scenario/runtime composition exposes the same fact through:

```java
assembly.waterWading(definitionId, 250_000);
```

`maxDepth` uses normalized `CellSpace` height units:

```text
0         dry destinations only
1_000_000 one full standing cell of Water
```

The profile is explicit. A mover with no `waterWading` aspect keeps Water-neutral traversal behavior; there is no hidden species/material fallback.

This capability represents terrestrial wading, not universal fluid locomotion. Swimming and waterborne movement require different support/Navigation semantics and should be introduced by real consumers rather than overloading `maxDepth`.

## Destination depth

`WaterWadingConstraint` reads Water at the destination standing coordinate and converts finite volume into local surface height through neutral `CellSpace.surfaceHeight(...)` Geometry.

Therefore `Water amount` is not blindly treated as depth: nontrivial Shape free-space profiles can map equal volume to different heights.

If the destination standing cell is full and the cell above also contains Water, the destination is treated as deeper than one cell. Water that temporarily exceeds newly changed Geometry capacity is classified conservatively as too deep until hydraulic redistribution resolves it.

Only the **destination** is constrained. A mover already standing in Water deeper than its tolerance may still leave toward a shallower/dry destination instead of becoming mechanically trapped by rising Water.

## Authoritative execution

The same `WaterWadingConstraint` instance is checked at both concrete Movement boundaries:

```text
MovementSystem.startStep
        ↓
authoritative start validation

MovementActionProcessor.complete
        ↓
authoritative commit revalidation
```

If Water is already too deep, the edge is not scheduled. If Water rises while a timed edge is in progress, commit is rejected and Spatial remains at the source.

The compatibility/default Movement constructors preserve `ALLOW_ALL`, so tests or runtimes that do not opt into a dynamic traversal constraint retain earlier behavior.

## Advisory MoveTo planning

`MoveToSystem` remains a Movement-domain orchestrator and deliberately does not import Water/Traversal internals. It owns the narrow `MoveToQueryConstraintProvider` extension point.

Production composition adapts the same live mover constraint through `MoverTraversalQueryConstraintProvider`:

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

The adapter composes the mover restriction with any caller-provided `PathTransitionConstraint`; it does not replace an existing query constraint.

A route planned while a destination is already too deep can therefore avoid it when an alternative exists. The route remains disposable advice: every chosen edge still passes through authoritative Movement start/commit checks.

## Revisions and high-churn Water

Mover/environment facts are intentionally not folded into the landscape traversal revision. Hydraulic relaxation may change Water every simulation tick while a mover's semantic passability remains on the same side of its depth threshold.

Current production MoveTo searches run synchronously to a terminal search result without advancing simulation time between expansion chunks. They therefore read current Water during the query and immediately hand the resulting route to Movement.

If path search later becomes genuinely resumable across authoritative world ticks, invalidation should be local/semantic: relevant threshold crossings should stale affected work rather than every raw Water quantity change everywhere.

## Runtime composition

`SimulationAssembly` creates one `WaterWadingConstraint` from object definitions, current Water and shared Geometry. The same instance is supplied to:

```text
MovementSystem
MovementActionProcessor
MoverTraversalQueryConstraintProvider -> MoveToSystem
```

Planning and execution therefore use one semantic rule without moving Water into Navigation or duplicating threshold logic.

## World bounds

Water traversal has no separate map-bound rule. If the runtime configures finite `WorldBounds`, the shared `WorldGeometryLookup` presents outside coordinates as closed `FullShape`, and Navigation/Movement/Water all observe that same boundary through their ordinary Geometry dependency.

An unbounded runtime remains valid when no bounds are configured. Generated/unloaded/streamed world containment is still a separate future problem.

## Deliberately absent

- shallow-Water movement cost/speed penalties;
- swimming locomotion;
- boat/water-surface Navigation;
- current force, slipping or knockback;
- drowning/breathing;
- mover body-volume collision with Water;
- global path-cache invalidation from raw Water amounts;
- temporal/space-time fluid traversal planning.

## Tests

Headless/integration coverage includes mover-specific depth thresholds, optional Water-neutral behavior, deeper-than-one-cell detection, escape from over-deep source Water, generic Shape depth conversion, definition validation, Movement start rejection, Movement commit revalidation after Water changes, MoveTo query-constraint composition, preservation of caller constraint semantics, rain-created deep Water being avoided by MoveTo when a dry detour exists, and Water arriving during a timed edge preventing commit.

# ADR-002: Shape contribution algebra

- Status: Accepted
- Scope: Geometry and structural traversal
- Decision: Shapes contribute local departure, arrival and block facts; Navigation resolves those facts generically instead of branching on concrete Shape types.

## Context

Navigation needs to support full cells, ramps and future terrain shapes without teaching `NavigationSystem` every concrete implementation or allowing one Shape to inspect and interpret its neighbor.

## Decision

Each Shape contributes independent local structural roles. Generic composition resolves compatible departure/arrival roles and then removes blocked directions. Conceptually:

```text
resolved = departures & arrivals & ~blocks
```

Source and destination Shapes own only their side of the relationship. Intrinsic Shape traversal factors follow the same departure/arrival role split.

## Why

The algebra keeps local geometry translation-independent, makes processing order irrelevant and gives new Shapes a data/behavior contract rather than a central type-switch extension point.

## Consequences

- Navigation does not switch on `FullShape`, `RampShape` or future concrete classes.
- A compatible new Shape normally adds only its implementation, presentation binding and tests.
- Source/destination geometry can evolve independently behind the same neutral facts.
- If a real future Shape cannot fit the current one-supported-position model, the neutral Shape contract must be revised deliberately rather than bypassed with an escape hatch.

## Alternatives considered

Concrete Shape branching inside Navigation was rejected because every new Shape would modify generic consumers. Letting a Shape query neighboring Shapes was rejected because ownership/order would become implicit.

## Current implementation

`Shape` exposes structural transition roles/factors plus independent physical geometry facts. Current full cells and cardinal ramps use the shared algebra; surface-boundary continuity supplies an additional neutral geometric check for current same-level joins. Generic Navigation and Transition Cost consume these contracts without concrete-class branches.

## Related documentation

- [Geometry and Shape](../systems/foundations/geometry.md)
- [Navigation](../systems/traversal/navigation.md)
- [Transition Cost](../systems/traversal/traversal-cost.md)
- [Architecture](../architecture.md)

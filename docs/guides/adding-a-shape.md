# Adding a Shape

Add a new Shape only for a real geometry consumer.

## Simulation

1. Implement `Shape` using local relative coordinates.
2. Express departure, arrival and block contributions without querying neighbors/world state.
3. Provide traversal factors only for roles owned by the Shape.
4. Add focused topology/traversal tests.
5. Verify the Shape fits the current supported-position model. If it does not, revise the generic contract, Navigation read envelope and TransitionCost support lookup together.

Do not add recognition branches to `NavigationSystem` or `TransitionCost`.

## Presentation

If the Shape needs a distinct current visual/debug representation, add a typed `ShapePresentation<YourShape>` binding and register it in the presentation composition root.

Do not add:

```java
if (shape instanceof YourShape) ...
```

to generic landscape, overlay or HUD code, and do not add rendering methods or visual string ids to simulation `Shape`.

## Documentation

Update `systems/geometry.md` only if the **Shape contract or current geometry semantics** changed. Merely adding another implementation that fits the existing contract does not require rewriting the geometry documentation.

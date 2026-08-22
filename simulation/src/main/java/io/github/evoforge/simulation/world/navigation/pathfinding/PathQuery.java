package io.github.evoforge.simulation.world.navigation.pathfinding;

/** One actor-independent spatial route request plus an optional advisory edge constraint. */
public record PathQuery(
        int fromX,
        int fromY,
        int fromZ,
        int toX,
        int toY,
        int toZ,
        PathTransitionConstraint constraint) {

    public PathQuery {
        if (constraint == null) {
            throw new IllegalArgumentException(
                    "constraint must not be null");
        }
    }

    public static PathQuery between(
            int fromX,
            int fromY,
            int fromZ,
            int toX,
            int toY,
            int toZ) {

        return new PathQuery(
                fromX,
                fromY,
                fromZ,
                toX,
                toY,
                toZ,
                PathTransitionConstraint.ALLOW_ALL);
    }

    public PathQuery withConstraint(
            PathTransitionConstraint newConstraint) {

        return new PathQuery(
                fromX,
                fromY,
                fromZ,
                toX,
                toY,
                toZ,
                newConstraint);
    }
}

package io.github.evoforge.simulation.world.mechanics.movement;

import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.pathfinding.PathRoute;

/** Immutable read-only snapshot of the route currently executed by MoveTo. */
public record MoveToProgress(
        MoveToActionId actionId,
        ObjectId objectId,
        int goalX,
        int goalY,
        int goalZ,
        PathRoute route,
        int nextStepIndex) {

    public MoveToProgress {
        if (actionId == null || objectId == null || route == null) {
            throw new IllegalArgumentException(
                    "MoveTo progress must not contain null");
        }
        if (nextStepIndex < 0 || nextStepIndex > route.size()) {
            throw new IllegalArgumentException(
                    "nextStepIndex is outside the route");
        }
    }

    public int remainingStepCount() {
        return route.size() - nextStepIndex;
    }
}

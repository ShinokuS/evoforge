package io.github.evoforge.simulation.world.mechanics.movement;

import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.pathfinding.PathTransitionConstraint;

/** Command-side boundary for starting long-range movement without exposing planner internals. */
public interface MoveToStarter {

    MoveToStartAttempt start(
            ObjectId objectId,
            int goalX,
            int goalY,
            int goalZ);

    MoveToStartAttempt start(
            ObjectId objectId,
            int goalX,
            int goalY,
            int goalZ,
            PathTransitionConstraint constraint);

    static MoveToStarter direct(
            MoveToSystem moveTo) {

        if (moveTo == null) {
            throw new IllegalArgumentException(
                    "moveTo must not be null");
        }

        return new MoveToStarter() {
            @Override
            public MoveToStartAttempt start(
                    ObjectId objectId,
                    int goalX,
                    int goalY,
                    int goalZ) {
                return moveTo.start(
                        objectId,
                        goalX,
                        goalY,
                        goalZ);
            }

            @Override
            public MoveToStartAttempt start(
                    ObjectId objectId,
                    int goalX,
                    int goalY,
                    int goalZ,
                    PathTransitionConstraint constraint) {
                return moveTo.start(
                        objectId,
                        goalX,
                        goalY,
                        goalZ,
                        constraint);
            }
        };
    }
}

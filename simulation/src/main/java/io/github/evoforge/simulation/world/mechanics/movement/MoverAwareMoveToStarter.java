package io.github.evoforge.simulation.world.mechanics.movement;

import io.github.evoforge.simulation.world.mechanics.traversal.MoverTraversalConstraint;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.pathfinding.PathTransitionConstraint;

/**
 * Binds mover-specific live traversal facts into MoveTo's existing query-local
 * constraint boundary without changing MoveToSystem or global traversal revisions.
 */
public final class MoverAwareMoveToStarter
        implements MoveToStarter {

    private final MoveToStarter delegate;
    private final MoverTraversalConstraint traversal;

    public MoverAwareMoveToStarter(
            MoveToStarter delegate,
            MoverTraversalConstraint traversal) {

        if (delegate == null || traversal == null) {
            throw new IllegalArgumentException(
                    "mover-aware MoveTo dependencies must not be null");
        }
        this.delegate = delegate;
        this.traversal = traversal;
    }

    @Override
    public MoveToStartAttempt start(
            ObjectId objectId,
            int goalX,
            int goalY,
            int goalZ) {

        return start(
                objectId,
                goalX,
                goalY,
                goalZ,
                PathTransitionConstraint.ALLOW_ALL);
    }

    @Override
    public MoveToStartAttempt start(
            ObjectId objectId,
            int goalX,
            int goalY,
            int goalZ,
            PathTransitionConstraint constraint) {

        if (objectId == null) {
            throw new IllegalArgumentException(
                    "objectId must not be null");
        }
        if (constraint == null) {
            throw new IllegalArgumentException(
                    "constraint must not be null");
        }

        return delegate.start(
                objectId,
                goalX,
                goalY,
                goalZ,
                bind(objectId, constraint));
    }

    private PathTransitionConstraint bind(
            ObjectId objectId,
            PathTransitionConstraint caller) {

        if (traversal == MoverTraversalConstraint.ALLOW_ALL) {
            return caller;
        }

        return new PathTransitionConstraint() {
            @Override
            public boolean allows(
                    int fromX,
                    int fromY,
                    int fromZ,
                    int toX,
                    int toY,
                    int toZ) {

                return caller.allows(
                                fromX,
                                fromY,
                                fromZ,
                                toX,
                                toY,
                                toZ)
                        && traversal.allows(
                                objectId,
                                fromX,
                                fromY,
                                fromZ,
                                toX,
                                toY,
                                toZ);
            }

            @Override
            public long revision() {
                // Raw Water/mover facts are intentionally not revisions. Preserve
                // only the caller's explicit semantic/query-local revision.
                return caller.revision();
            }
        };
    }
}

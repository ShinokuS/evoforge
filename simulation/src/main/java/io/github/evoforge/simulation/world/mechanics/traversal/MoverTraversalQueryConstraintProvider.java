package io.github.evoforge.simulation.world.mechanics.traversal;

import io.github.evoforge.simulation.world.mechanics.movement.MoveToQueryConstraintProvider;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.pathfinding.PathTransitionConstraint;

/** Adapts live mover traversal permission into MoveTo's advisory query boundary. */
public final class MoverTraversalQueryConstraintProvider
        implements MoveToQueryConstraintProvider {

    private final MoverTraversalConstraint traversal;

    public MoverTraversalQueryConstraintProvider(
            MoverTraversalConstraint traversal) {

        if (traversal == null) {
            throw new IllegalArgumentException(
                    "traversal must not be null");
        }
        this.traversal = traversal;
    }

    @Override
    public PathTransitionConstraint constraintFor(
            ObjectId objectId,
            PathTransitionConstraint callerConstraint) {

        if (objectId == null) {
            throw new IllegalArgumentException(
                    "objectId must not be null");
        }
        if (callerConstraint == null) {
            throw new IllegalArgumentException(
                    "callerConstraint must not be null");
        }
        if (traversal == MoverTraversalConstraint.ALLOW_ALL) {
            return callerConstraint;
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

                return callerConstraint.allows(
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
                // Mover/environment facts are intentionally live but non-revisioned.
                // Preserve only the caller's explicit semantic/query-local revision.
                return callerConstraint.revision();
            }
        };
    }
}

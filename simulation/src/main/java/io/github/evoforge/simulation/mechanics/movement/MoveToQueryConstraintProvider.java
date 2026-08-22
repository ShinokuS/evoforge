package io.github.evoforge.simulation.mechanics.movement;

import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.navigation.pathfinding.PathTransitionConstraint;

/**
 * Supplies a mover-specific advisory PathQuery constraint without coupling MoveTo
 * orchestration to the domain that owns the dynamic traversal fact.
 */
@FunctionalInterface
public interface MoveToQueryConstraintProvider {

    MoveToQueryConstraintProvider IDENTITY =
            (objectId, caller) -> caller;

    PathTransitionConstraint constraintFor(
            ObjectId objectId,
            PathTransitionConstraint callerConstraint);
}

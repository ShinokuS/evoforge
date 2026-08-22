package io.github.evoforge.simulation.mechanics.movement;

import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.navigation.pathfinding.PathSearch;

/** Read-only disposable planning using the same mover policy as authoritative MoveTo. */
public interface MoveToPlanner {

    /**
     * Starts a disposable search from the mover's current transform to the goal.
     * No movement claim, occupancy reservation or authoritative action is created.
     */
    PathSearch beginPreview(ObjectId objectId, int goalX, int goalY, int goalZ);
}

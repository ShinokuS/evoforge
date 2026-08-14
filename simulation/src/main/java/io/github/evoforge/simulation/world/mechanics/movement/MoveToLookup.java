package io.github.evoforge.simulation.world.mechanics.movement;

import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.pathfinding.PathRoute;

/** Read-only observation of route-level Movement state. */
public interface MoveToLookup {

    boolean isActive(ObjectId objectId);

    MoveToActionId activeActionId(ObjectId objectId);

    /** Current immutable planned route, or null when no MoveTo is active. */
    PathRoute activeRoute(ObjectId objectId);

    /** Returns only the latest terminal outcome retained for this object. */
    MoveToCompletion lastCompletion(ObjectId objectId);
}

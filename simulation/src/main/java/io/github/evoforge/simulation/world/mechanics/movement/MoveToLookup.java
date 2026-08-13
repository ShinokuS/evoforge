package io.github.evoforge.simulation.world.mechanics.movement;

import io.github.evoforge.simulation.world.object.ObjectId;

/** Read-only observation of route-level Movement state. */
public interface MoveToLookup {

    boolean isActive(ObjectId objectId);

    MoveToActionId activeActionId(ObjectId objectId);

    /** Returns only the latest terminal outcome retained for this object. */
    MoveToCompletion lastCompletion(ObjectId objectId);
}

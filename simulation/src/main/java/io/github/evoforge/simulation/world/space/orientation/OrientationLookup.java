package io.github.evoforge.simulation.world.space.orientation;

import io.github.evoforge.simulation.world.object.ObjectId;

/** Read-only authoritative horizontal orientation. */
public interface OrientationLookup {
    boolean has(ObjectId objectId);
    FacingDirection facing(ObjectId objectId);
}

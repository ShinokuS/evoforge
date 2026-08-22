package io.github.evoforge.simulation.world.space.orientation;

import io.github.evoforge.simulation.world.object.ObjectId;

/** Narrow cross-owner mutation capability for physical orientation. */
public interface OrientationMutations {

    void faceIfPresent(ObjectId objectId, int dx, int dy);

    static OrientationMutations none() {
        return (objectId, dx, dy) -> { };
    }
}

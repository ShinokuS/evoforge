package io.github.evoforge.simulation.world.space.position;

import io.github.evoforge.simulation.world.object.ObjectId;

/** Consumer-neutral authoritative position mutation capability. */
public interface PositionMutations {

    void place(ObjectId id, int x, int y, int z);

    void move(ObjectId id, int x, int y, int z);

    void remove(ObjectId id);
}

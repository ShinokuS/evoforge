package io.github.evoforge.simulation.world.space.position;

import io.github.evoforge.simulation.world.object.ObjectId;

public interface PositionLookup {

    boolean has(ObjectId id);

    int x(ObjectId id);

    int y(ObjectId id);

    int z(ObjectId id);
}
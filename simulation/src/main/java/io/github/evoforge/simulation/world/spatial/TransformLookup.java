package io.github.evoforge.simulation.world.spatial;

import io.github.evoforge.simulation.world.object.ObjectId;

public interface TransformLookup {

    boolean has(ObjectId id);

    int x(ObjectId id);

    int y(ObjectId id);

    int z(ObjectId id);
}
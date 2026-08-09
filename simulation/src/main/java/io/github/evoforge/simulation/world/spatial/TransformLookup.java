package io.github.evoforge.simulation.world.spatial;

import io.github.evoforge.simulation.world.object.ObjectId;

public interface TransformLookup {

    boolean has(ObjectId id);

    double x(ObjectId id);

    double y(ObjectId id);

    double z(ObjectId id);
}
package io.github.evoforge.simulation.world.spatial;

import io.github.evoforge.simulation.world.object.ObjectId;

public interface SpatialIndex {

    void add(
            ObjectId id,
            double x,
            double y,
            double z);

    void move(
            ObjectId id,
            double oldX,
            double oldY,
            double oldZ,
            double newX,
            double newY,
            double newZ);

    void remove(
            ObjectId id,
            double x,
            double y,
            double z);
}
package io.github.evoforge.simulation.world.space.position;

import io.github.evoforge.simulation.world.object.ObjectId;

public interface ObjectPositionIndex {

    void add(
            ObjectId id,
            int x,
            int y,
            int z);

    void move(
            ObjectId id,
            int oldX,
            int oldY,
            int oldZ,
            int newX,
            int newY,
            int newZ);

    void remove(
            ObjectId id,
            int x,
            int y,
            int z);
}
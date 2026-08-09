package io.github.evoforge.simulation.world.object;

public interface ObjectLookup {

    WorldObject get(ObjectId id);

    boolean isAlive(ObjectId id);

    int size();
}
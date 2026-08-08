package io.github.evoforge.simulation.world;

import io.github.evoforge.simulation.world.object.ObjectRepository;

public final class World {

    private final ObjectRepository objects;

    public World() {
        objects = new ObjectRepository();
    }

    public ObjectRepository objects() {
        return objects;
    }
}
package io.github.evoforge.simulation.world.object;

public abstract class WorldObject {

    private final ObjectId id;

    protected WorldObject(ObjectId id) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }

        this.id = id;
    }

    public final ObjectId id() {
        return id;
    }
}
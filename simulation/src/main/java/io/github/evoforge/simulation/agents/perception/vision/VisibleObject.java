package io.github.evoforge.simulation.agents.perception.vision;

import io.github.evoforge.simulation.world.object.ObjectId;

public record VisibleObject(ObjectId objectId, int x, int y, int z, int distance) {
    public VisibleObject {
        if (objectId == null) throw new IllegalArgumentException("objectId must not be null");
        if (distance < 0) throw new IllegalArgumentException("distance must be >= 0");
    }
}

package io.github.evoforge.simulation.agents.perception;

import io.github.evoforge.simulation.world.object.ObjectId;

/** Sensor-neutral perceived concrete object fact for current decision making. */
public record PerceivedObject(ObjectId objectId, int x, int y, int z, int distance) {
    public PerceivedObject {
        if (objectId == null) throw new IllegalArgumentException("objectId must not be null");
        if (distance < 0) throw new IllegalArgumentException("distance must be >= 0");
    }
}

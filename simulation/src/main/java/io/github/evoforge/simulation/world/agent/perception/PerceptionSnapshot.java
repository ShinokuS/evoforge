package io.github.evoforge.simulation.world.agent.perception;

import io.github.evoforge.simulation.world.object.ObjectId;
import java.util.List;

/** Immutable current sensory facts; it is not persistent knowledge or memory. */
public record PerceptionSnapshot(ObjectId observerId, List<PerceivedObject> objects) {
    public PerceptionSnapshot {
        if (observerId == null || objects == null) throw new IllegalArgumentException("perception values must not be null");
        objects = List.copyOf(objects);
    }

    public static PerceptionSnapshot empty(ObjectId observerId) {
        return new PerceptionSnapshot(observerId, List.of());
    }
}

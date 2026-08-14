package io.github.evoforge.simulation.world.agent.perception;

import io.github.evoforge.simulation.world.object.ObjectId;

/** Sensor-neutral current perception consumed by autonomous decision making. */
public interface PerceptionLookup {
    PerceptionSnapshot perceive(ObjectId observerId);
}

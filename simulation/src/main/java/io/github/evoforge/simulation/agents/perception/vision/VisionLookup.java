package io.github.evoforge.simulation.agents.perception.vision;

import io.github.evoforge.simulation.world.object.ObjectId;

/** Read-only visual perception capability. */
public interface VisionLookup {
    /** Returns null when the object has no visual sense. */
    VisionSnapshot snapshot(ObjectId observerId);
}

package io.github.evoforge.simulation.world.agent.need;

import io.github.evoforge.simulation.world.object.ObjectId;

/** Narrow mutation capability for mechanics that increase an existing need deficit. */
public interface NeedDeficitIncrease {
    /** Increases a deficit up to its configured maximum and returns the amount actually applied. */
    long increase(ObjectId objectId, NeedId needId, long requestedAmount);
}

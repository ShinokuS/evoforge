package io.github.evoforge.simulation.world.space.occupancy;

import io.github.evoforge.simulation.world.object.ObjectId;

/** Consumer-neutral query for whether an object may enter a discrete cell. */
public interface CellAdmission {

    OccupancyState admissionState(ObjectId objectId, int x, int y, int z);
}

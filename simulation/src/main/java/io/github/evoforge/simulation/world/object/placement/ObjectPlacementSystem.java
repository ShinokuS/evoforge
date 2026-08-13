package io.github.evoforge.simulation.world.object.placement;

import io.github.evoforge.simulation.world.mechanics.occupancy.OccupancyState;
import io.github.evoforge.simulation.world.mechanics.occupancy.OccupancySystem;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.ObjectLookup;
import io.github.evoforge.simulation.world.spatial.SpatialSystem;

/**
 * Semantic object-placement boundary coordinating exclusive occupancy with
 * authoritative Spatial mutation.
 */
public final class ObjectPlacementSystem {

    private final ObjectLookup objects;
    private final OccupancySystem occupancy;
    private final SpatialSystem spatial;

    public ObjectPlacementSystem(
            ObjectLookup objects,
            OccupancySystem occupancy,
            SpatialSystem spatial) {

        if (objects == null) {
            throw new IllegalArgumentException(
                    "objects must not be null");
        }
        if (occupancy == null) {
            throw new IllegalArgumentException(
                    "occupancy must not be null");
        }
        if (spatial == null) {
            throw new IllegalArgumentException(
                    "spatial must not be null");
        }

        this.objects = objects;
        this.occupancy = occupancy;
        this.spatial = spatial;
    }

    public ObjectPlacementResult place(
            ObjectId objectId,
            int x,
            int y,
            int z) {

        if (objectId == null) {
            throw new IllegalArgumentException(
                    "objectId must not be null");
        }
        if (!objects.isAlive(objectId)) {
            throw new IllegalArgumentException(
                    "unknown object: " + objectId);
        }

        OccupancyState state = occupancy.admissionState(
                objectId,
                x,
                y,
                z);
        if (state == OccupancyState.OCCUPIED) {
            return ObjectPlacementResult.DESTINATION_OCCUPIED;
        }
        if (state == OccupancyState.RESERVED) {
            return ObjectPlacementResult.DESTINATION_RESERVED;
        }

        spatial.place(objectId, x, y, z);
        return ObjectPlacementResult.PLACED;
    }
}

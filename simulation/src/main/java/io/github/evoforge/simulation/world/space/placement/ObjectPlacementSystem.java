package io.github.evoforge.simulation.world.space.placement;

import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.ObjectLookup;
import io.github.evoforge.simulation.world.space.occupancy.CellAdmission;
import io.github.evoforge.simulation.world.space.occupancy.OccupancyState;
import io.github.evoforge.simulation.world.space.position.PositionMutations;

/**
 * Consumer-neutral object placement capability coordinating admission with
 * authoritative position mutation.
 */
public final class ObjectPlacementSystem {

    private final ObjectLookup objects;
    private final CellAdmission admission;
    private final PositionMutations positions;

    public ObjectPlacementSystem(
            ObjectLookup objects,
            CellAdmission admission,
            PositionMutations positions) {

        if (objects == null) {
            throw new IllegalArgumentException("objects must not be null");
        }
        if (admission == null) {
            throw new IllegalArgumentException("admission must not be null");
        }
        if (positions == null) {
            throw new IllegalArgumentException("positions must not be null");
        }

        this.objects = objects;
        this.admission = admission;
        this.positions = positions;
    }

    public ObjectPlacementResult place(
            ObjectId objectId,
            int x,
            int y,
            int z) {

        if (objectId == null) {
            throw new IllegalArgumentException("objectId must not be null");
        }
        if (!objects.isAlive(objectId)) {
            throw new IllegalArgumentException("unknown object: " + objectId);
        }

        OccupancyState state = admission.admissionState(objectId, x, y, z);
        if (state == OccupancyState.OCCUPIED) {
            return ObjectPlacementResult.DESTINATION_OCCUPIED;
        }
        if (state == OccupancyState.RESERVED) {
            return ObjectPlacementResult.DESTINATION_RESERVED;
        }

        positions.place(objectId, x, y, z);
        return ObjectPlacementResult.PLACED;
    }
}

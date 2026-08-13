package io.github.evoforge.simulation.world.mechanics.occupancy;

import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.ObjectLookup;
import io.github.evoforge.simulation.world.object.WorldObject;
import io.github.evoforge.simulation.world.spatial.CellObjectLookup;

import java.util.HashMap;
import java.util.Map;

/**
 * Owns present-tense execution reservations and composes them with actual
 * Spatial presence to expose dynamic cell availability.
 *
 * <p>Actual object position is never duplicated here. {@code OCCUPIED} is
 * derived from the Spatial cell index plus immutable occupancy definitions;
 * only {@code RESERVED} state is stored authoritatively by this system.</p>
 */
public final class OccupancySystem implements OccupancyLookup {

    private final ObjectLookup objects;
    private final CellObjectLookup cells;
    private final OccupancyDefinitions definitions;

    private final Map<CellKey, Reservation> reservationsByCell =
            new HashMap<>();
    private final Map<OccupancyReservationId, CellKey> cellsByReservation =
            new HashMap<>();

    public OccupancySystem(
            ObjectLookup objects,
            CellObjectLookup cells,
            OccupancyDefinitions definitions) {

        if (objects == null) {
            throw new IllegalArgumentException(
                    "objects must not be null");
        }
        if (cells == null) {
            throw new IllegalArgumentException(
                    "cells must not be null");
        }
        if (definitions == null) {
            throw new IllegalArgumentException(
                    "definitions must not be null");
        }

        this.objects = objects;
        this.cells = cells;
        this.definitions = definitions;
    }

    @Override
    public OccupancyState state(
            int x,
            int y,
            int z) {

        int exclusiveOccupants = exclusiveOccupantCount(x, y, z);
        if (exclusiveOccupants > 1) {
            throw new IllegalStateException(
                    "multiple exclusive occupants at ("
                            + x + ", " + y + ", " + z + ")");
        }
        if (exclusiveOccupants == 1) {
            return OccupancyState.OCCUPIED;
        }

        return reservationsByCell.containsKey(new CellKey(x, y, z))
                ? OccupancyState.RESERVED
                : OccupancyState.FREE;
    }

    /**
     * Returns the state relevant to placing/moving the candidate object.
     * Non-exclusive objects may coexist with occupants and reservations.
     */
    public OccupancyState admissionState(
            ObjectId candidate,
            int x,
            int y,
            int z) {

        if (!requiresExclusiveCell(candidate)) {
            return OccupancyState.FREE;
        }

        return state(x, y, z);
    }

    public boolean requiresExclusiveCell(
            ObjectId objectId) {

        WorldObject object = requireObject(objectId);
        return definitions.requiresExclusiveCell(object.definitionId());
    }

    /** Claims one immediate destination for one execution process. */
    public OccupancyReservationResult tryReserve(
            OccupancyReservationId reservationId,
            ObjectId objectId,
            int x,
            int y,
            int z) {

        if (reservationId == null) {
            throw new IllegalArgumentException(
                    "reservationId must not be null");
        }

        if (!requiresExclusiveCell(objectId)) {
            return OccupancyReservationResult.NOT_REQUIRED;
        }

        if (cellsByReservation.containsKey(reservationId)) {
            throw new IllegalStateException(
                    "reservation id already owns a cell: " + reservationId);
        }

        OccupancyState current = state(x, y, z);
        if (current == OccupancyState.OCCUPIED) {
            return OccupancyReservationResult.OCCUPIED;
        }
        if (current == OccupancyState.RESERVED) {
            return OccupancyReservationResult.RESERVED;
        }

        CellKey cell = new CellKey(x, y, z);
        reservationsByCell.put(
                cell,
                new Reservation(reservationId, objectId));
        cellsByReservation.put(reservationId, cell);
        return OccupancyReservationResult.ACQUIRED;
    }

    public boolean ownsReservation(
            OccupancyReservationId reservationId,
            ObjectId objectId,
            int x,
            int y,
            int z) {

        if (reservationId == null || objectId == null) {
            return false;
        }

        Reservation reservation = reservationsByCell.get(
                new CellKey(x, y, z));
        return reservation != null
                && reservationId.equals(reservation.id())
                && objectId.equals(reservation.objectId());
    }

    /**
     * True only while the exact reservation still owns the cell and no
     * exclusive object has physically entered it.
     */
    public boolean canCommit(
            OccupancyReservationId reservationId,
            ObjectId objectId,
            int x,
            int y,
            int z) {

        return ownsReservation(
                reservationId,
                objectId,
                x,
                y,
                z)
                && exclusiveOccupantCount(x, y, z) == 0;
    }

    /** Releases only the exact claim supplied by its owner. */
    public boolean release(
            OccupancyReservationId reservationId,
            ObjectId objectId,
            int x,
            int y,
            int z) {

        if (reservationId == null || objectId == null) {
            return false;
        }

        CellKey expectedCell = new CellKey(x, y, z);
        CellKey ownedCell = cellsByReservation.get(reservationId);
        if (!expectedCell.equals(ownedCell)) {
            return false;
        }

        Reservation reservation = reservationsByCell.get(expectedCell);
        if (reservation == null
                || !reservationId.equals(reservation.id())
                || !objectId.equals(reservation.objectId())) {
            return false;
        }

        reservationsByCell.remove(expectedCell);
        cellsByReservation.remove(reservationId);
        return true;
    }

    public int reservationCount() {
        return reservationsByCell.size();
    }

    private int exclusiveOccupantCount(
            int x,
            int y,
            int z) {

        int count = cells.objectCount(x, y, z);
        int exclusive = 0;

        for (int index = 0; index < count; index++) {
            ObjectId objectId = cells.objectAt(x, y, z, index);
            WorldObject object = objects.get(objectId);
            if (object == null) {
                throw new IllegalStateException(
                        "spatial index references missing object: " + objectId);
            }

            if (definitions.requiresExclusiveCell(object.definitionId())) {
                exclusive++;
            }
        }

        return exclusive;
    }

    private WorldObject requireObject(
            ObjectId objectId) {

        if (objectId == null) {
            throw new IllegalArgumentException(
                    "objectId must not be null");
        }

        WorldObject object = objects.get(objectId);
        if (object == null) {
            throw new IllegalArgumentException(
                    "unknown object: " + objectId);
        }
        return object;
    }

    private record CellKey(
            int x,
            int y,
            int z) {
    }

    private record Reservation(
            OccupancyReservationId id,
            ObjectId objectId) {
    }
}

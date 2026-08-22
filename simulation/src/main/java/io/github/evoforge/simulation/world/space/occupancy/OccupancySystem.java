package io.github.evoforge.simulation.world.space.occupancy;

import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.ObjectLookup;
import io.github.evoforge.simulation.world.object.WorldObject;
import io.github.evoforge.simulation.world.space.position.CellObjectLookup;

import java.util.HashMap;
import java.util.Map;
import io.github.evoforge.simulation.world.space.occupancy.OccupancyLookup;

/**
 * Owns present-tense execution reservations and composes them with actual
 * Spatial presence to expose dynamic cell availability.
 *
 * <p>Actual object position is never duplicated here. {@code OCCUPIED} is
 * derived from the Spatial cell index plus immutable occupancy definitions;
 * only {@code RESERVED} state is stored authoritatively by this system.</p>
 */
public final class OccupancySystem implements OccupancyLookup, CellAdmission {

    private final ObjectLookup objects;
    private final CellObjectLookup cells;
    private final OccupancyDefinitions definitions;

    private final Map<CellKey, Reservation> reservationsByCell =
            new HashMap<>();
    private final Map<OccupancyReservationId, CellKey> cellsByReservation =
            new HashMap<>();
    private final CellProbe reservationLookupProbe = new CellProbe();

    private long nextReservationId;

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

        reservationLookupProbe.set(x, y, z);
        return reservationsByCell.containsKey(reservationLookupProbe)
                ? OccupancyState.RESERVED
                : OccupancyState.FREE;
    }

    /**
     * Returns the state relevant to placing/moving the candidate object.
     * Non-exclusive objects may coexist with occupants and reservations.
     */
    @Override
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
    public OccupancyReservationAttempt tryReserve(
            ObjectId objectId,
            int x,
            int y,
            int z) {

        if (!requiresExclusiveCell(objectId)) {
            return OccupancyReservationAttempt.notRequired();
        }

        OccupancyState current = state(x, y, z);
        if (current == OccupancyState.OCCUPIED) {
            return OccupancyReservationAttempt.occupied();
        }
        if (current == OccupancyState.RESERVED) {
            return OccupancyReservationAttempt.reserved();
        }

        if (nextReservationId == Long.MAX_VALUE) {
            throw new IllegalStateException(
                    "occupancy reservation id space exhausted");
        }

        OccupancyReservationId reservationId =
                OccupancyReservationId.of(nextReservationId++);
        CellKey cell = new CellKey(x, y, z);
        reservationsByCell.put(
                cell,
                new Reservation(reservationId, objectId));
        cellsByReservation.put(reservationId, cell);
        return OccupancyReservationAttempt.acquired(reservationId);
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

        reservationLookupProbe.set(x, y, z);
        Reservation reservation = reservationsByCell.get(reservationLookupProbe);
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

        CellKey ownedCell = cellsByReservation.get(reservationId);
        if (ownedCell == null || !ownedCell.matches(x, y, z)) {
            return false;
        }

        Reservation reservation = reservationsByCell.get(ownedCell);
        if (reservation == null
                || !reservationId.equals(reservation.id())
                || !objectId.equals(reservation.objectId())) {
            return false;
        }

        reservationsByCell.remove(ownedCell);
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

    private static int hash(
            int x,
            int y,
            int z) {

        int result = Integer.hashCode(x);
        result = 31 * result + Integer.hashCode(y);
        result = 31 * result + Integer.hashCode(z);
        return result;
    }

    private record CellKey(
            int x,
            int y,
            int z) {

        private boolean matches(
                int otherX,
                int otherY,
                int otherZ) {

            return x == otherX && y == otherY && z == otherZ;
        }

        @Override
        public int hashCode() {
            return hash(x, y, z);
        }
    }

    /** Reused only for map reads; stored reservation keys remain immutable. */
    private static final class CellProbe {
        private int x;
        private int y;
        private int z;

        private void set(
                int x,
                int y,
                int z) {

            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public int hashCode() {
            return hash(x, y, z);
        }

        @Override
        public boolean equals(
                Object other) {

            if (other instanceof CellKey cell) {
                return x == cell.x()
                        && y == cell.y()
                        && z == cell.z();
            }
            if (other instanceof CellProbe probe) {
                return x == probe.x
                        && y == probe.y
                        && z == probe.z;
            }
            return false;
        }
    }

    private record Reservation(
            OccupancyReservationId id,
            ObjectId objectId) {
    }
}

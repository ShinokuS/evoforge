package io.github.evoforge.simulation.world.mechanics.occupancy;

/** Result plus optional handle from one immediate execution-reservation attempt. */
public record OccupancyReservationAttempt(
        OccupancyReservationResult result,
        OccupancyReservationId reservationId) {

    public OccupancyReservationAttempt {
        if (result == null) {
            throw new IllegalArgumentException(
                    "result must not be null");
        }

        if (result == OccupancyReservationResult.ACQUIRED
                && reservationId == null) {
            throw new IllegalArgumentException(
                    "acquired reservation must have an id");
        }
        if (result != OccupancyReservationResult.ACQUIRED
                && reservationId != null) {
            throw new IllegalArgumentException(
                    "only an acquired reservation may have an id");
        }
    }

    public boolean accepted() {
        return result.accepted();
    }

    public static OccupancyReservationAttempt acquired(
            OccupancyReservationId reservationId) {
        return new OccupancyReservationAttempt(
                OccupancyReservationResult.ACQUIRED,
                reservationId);
    }

    public static OccupancyReservationAttempt notRequired() {
        return new OccupancyReservationAttempt(
                OccupancyReservationResult.NOT_REQUIRED,
                null);
    }

    public static OccupancyReservationAttempt occupied() {
        return new OccupancyReservationAttempt(
                OccupancyReservationResult.OCCUPIED,
                null);
    }

    public static OccupancyReservationAttempt reserved() {
        return new OccupancyReservationAttempt(
                OccupancyReservationResult.RESERVED,
                null);
    }
}

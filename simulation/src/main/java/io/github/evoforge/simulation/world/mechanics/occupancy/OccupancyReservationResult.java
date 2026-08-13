package io.github.evoforge.simulation.world.mechanics.occupancy;

/** Result of trying to claim one immediate execution destination. */
public enum OccupancyReservationResult {
    ACQUIRED(true),
    NOT_REQUIRED(true),
    OCCUPIED(false),
    RESERVED(false);

    private final boolean accepted;

    OccupancyReservationResult(
            boolean accepted) {
        this.accepted = accepted;
    }

    public boolean accepted() {
        return accepted;
    }
}

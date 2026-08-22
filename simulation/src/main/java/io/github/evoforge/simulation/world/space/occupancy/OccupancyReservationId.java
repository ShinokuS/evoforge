package io.github.evoforge.simulation.world.space.occupancy;

/**
 * Opaque identity of one execution-time cell reservation.
 *
 * <p>The owner mechanic may derive this value from its own process identity,
 * but Occupancy deliberately does not depend on that mechanic's type.</p>
 */
public final class OccupancyReservationId {

    private final long value;

    private OccupancyReservationId(
            long value) {
        this.value = value;
    }

    public static OccupancyReservationId of(
            long value) {

        if (value < 0) {
            throw new IllegalArgumentException(
                    "value must be >= 0");
        }

        return new OccupancyReservationId(value);
    }

    public long asLong() {
        return value;
    }

    @Override
    public boolean equals(
            Object object) {

        if (this == object) {
            return true;
        }
        if (!(object instanceof OccupancyReservationId other)) {
            return false;
        }
        return value == other.value;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(value);
    }

    @Override
    public String toString() {
        return "OccupancyReservationId[" + value + "]";
    }
}

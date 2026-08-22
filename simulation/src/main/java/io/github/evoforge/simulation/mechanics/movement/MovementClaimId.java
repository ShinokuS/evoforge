package io.github.evoforge.simulation.mechanics.movement;

/** Opaque ownership token for one long-lived locomotion controller. */
public final class MovementClaimId {

    private final long value;

    private MovementClaimId(long value) {
        this.value = value;
    }

    public static MovementClaimId of(
            long value) {

        if (value < 0) {
            throw new IllegalArgumentException(
                    "value must be >= 0");
        }

        return new MovementClaimId(value);
    }

    public long asLong() {
        return value;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof MovementClaimId other)) {
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
        return "MovementClaimId[" + value + "]";
    }
}

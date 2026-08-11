package io.github.evoforge.simulation.world.mechanics.movement;

public final class MovementActionId {

    private final long value;

    private MovementActionId(long value) {
        this.value = value;
    }

    public static MovementActionId of(
            long value) {

        if (value < 0) {
            throw new IllegalArgumentException(
                    "value must be >= 0");
        }

        return new MovementActionId(value);
    }

    public long asLong() {
        return value;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof MovementActionId other)) {
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
        return "MovementActionId[" + value + "]";
    }
}

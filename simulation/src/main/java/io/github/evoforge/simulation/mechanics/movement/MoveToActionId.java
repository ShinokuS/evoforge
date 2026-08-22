package io.github.evoforge.simulation.mechanics.movement;

/** Opaque identifier for one accepted long-range movement operation. */
public final class MoveToActionId {

    private final long value;

    private MoveToActionId(long value) {
        this.value = value;
    }

    public static MoveToActionId of(
            long value) {

        if (value < 0) {
            throw new IllegalArgumentException(
                    "value must be >= 0");
        }
        return new MoveToActionId(value);
    }

    public long asLong() {
        return value;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof MoveToActionId other)) {
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
        return "MoveToActionId[" + value + "]";
    }
}

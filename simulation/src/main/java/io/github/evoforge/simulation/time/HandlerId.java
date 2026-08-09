package io.github.evoforge.simulation.time;

public final class HandlerId {

    private final int value;

    private HandlerId(int value) {
        this.value = value;
    }

    public static HandlerId of(int value) {
        if (value < 0) {
            throw new IllegalArgumentException(
                    "value must be >= 0");
        }

        return new HandlerId(value);
    }

    public int asInt() {
        return value;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof HandlerId other)) {
            return false;
        }

        return value == other.value;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(value);
    }

    @Override
    public String toString() {
        return "HandlerId[" + value + "]";
    }
}
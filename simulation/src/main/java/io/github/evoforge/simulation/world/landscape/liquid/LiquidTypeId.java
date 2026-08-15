package io.github.evoforge.simulation.world.landscape.liquid;

/** Stable semantic identity of one liquid constituent. */
public final class LiquidTypeId implements Comparable<LiquidTypeId> {

    private final String value;

    private LiquidTypeId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("liquid type id must not be blank");
        }
        this.value = value;
    }

    public static LiquidTypeId of(String value) {
        return new LiquidTypeId(value);
    }

    public String value() {
        return value;
    }

    @Override
    public int compareTo(LiquidTypeId other) {
        if (other == null) {
            throw new IllegalArgumentException("other liquid type id must not be null");
        }
        return value.compareTo(other.value);
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof LiquidTypeId other
                && value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return "LiquidTypeId[" + value + "]";
    }
}

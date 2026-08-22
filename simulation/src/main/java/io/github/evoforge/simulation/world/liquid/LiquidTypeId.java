package io.github.evoforge.simulation.world.liquid;

/** Open semantic identity of one liquid constituent; intentionally not a central enum. */
public record LiquidTypeId(String value) implements Comparable<LiquidTypeId> {

    public LiquidTypeId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("liquid type id must not be blank");
        }
    }

    public static LiquidTypeId of(String value) {
        return new LiquidTypeId(value);
    }

    @Override
    public int compareTo(LiquidTypeId other) {
        if (other == null) {
            throw new IllegalArgumentException("other liquid type id must not be null");
        }
        return value.compareTo(other.value);
    }
}

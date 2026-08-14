package io.github.evoforge.simulation.world.agent;

/** Stable open identifier for one capability an autonomous object may possess. */
public record CapabilityId(String value) implements Comparable<CapabilityId> {

    public CapabilityId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("capability value must not be blank");
        }
    }

    public static CapabilityId of(String value) {
        return new CapabilityId(value);
    }

    @Override
    public int compareTo(CapabilityId other) {
        if (other == null) {
            throw new IllegalArgumentException("other must not be null");
        }
        return value.compareTo(other.value);
    }
}

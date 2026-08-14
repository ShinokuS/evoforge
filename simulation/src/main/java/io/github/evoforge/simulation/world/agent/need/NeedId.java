package io.github.evoforge.simulation.world.agent.need;

/** Open semantic identifier for one need; intentionally not a central enum. */
public record NeedId(String value) implements Comparable<NeedId> {

    public NeedId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("need value must not be blank");
        }
    }

    public static NeedId of(String value) {
        return new NeedId(value);
    }

    @Override
    public int compareTo(NeedId other) {
        if (other == null) {
            throw new IllegalArgumentException("other must not be null");
        }
        return value.compareTo(other.value);
    }
}

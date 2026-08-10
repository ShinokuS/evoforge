package io.github.evoforge.simulation.world.landscape.definition;

public final class LandscapeDefinitionId {

    private final int value;

    private LandscapeDefinitionId(int value) {
        if (value < 0) {
            throw new IllegalArgumentException(
                    "value must be >= 0");
        }

        this.value = value;
    }

    public static LandscapeDefinitionId of(int value) {
        return new LandscapeDefinitionId(value);
    }

    public int asInt() {
        return value;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof LandscapeDefinitionId other
                && value == other.value;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(value);
    }

    @Override
    public String toString() {
        return "LandscapeDefinitionId[" + value + "]";
    }
}

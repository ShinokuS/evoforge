package io.github.evoforge.simulation.definition;

public final class DefinitionId {

    private final int value;

    private DefinitionId(int value) {
        this.value = value;
    }

    public static DefinitionId of(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("value must be >= 0");
        }

        return new DefinitionId(value);
    }

    public int asInt() {
        return value;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof DefinitionId other)) {
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
        return "DefinitionId[" + value + "]";
    }
}

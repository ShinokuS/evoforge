package io.github.evoforge.simulation.world.object.definition;

/** Object-domain view of a definition identifier. */
public final class ObjectDefinitionId {

    private final int value;

    private ObjectDefinitionId(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("value must be >= 0");
        }
        this.value = value;
    }

    public static ObjectDefinitionId of(int value) {
        return new ObjectDefinitionId(value);
    }

    public int asInt() {
        return value;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ObjectDefinitionId other
                && value == other.value;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(value);
    }

    @Override
    public String toString() {
        return "ObjectDefinitionId[" + value + "]";
    }
}

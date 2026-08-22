package io.github.evoforge.simulation.world.material;

public final class MaterialDefinitionId {

    private final int value;

    private MaterialDefinitionId(int value) {
        if (value < 0) {
            throw new IllegalArgumentException(
                    "value must be >= 0");
        }

        this.value = value;
    }

    public static MaterialDefinitionId of(int value) {
        return new MaterialDefinitionId(value);
    }

    public int asInt() {
        return value;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof MaterialDefinitionId other
                && value == other.value;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(value);
    }

    @Override
    public String toString() {
        return "MaterialDefinitionId[" + value + "]";
    }
}

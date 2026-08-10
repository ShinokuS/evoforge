package io.github.evoforge.simulation.world.object.definition;

import io.github.evoforge.simulation.definition.DefinitionId;

import java.util.Objects;

/** Object-domain view of a definition identifier. */
public final class ObjectDefinitionId {

    private final DefinitionId value;

    private ObjectDefinitionId(DefinitionId value) {
        this.value = Objects.requireNonNull(value, "value must not be null");
    }

    public static ObjectDefinitionId of(DefinitionId value) {
        return new ObjectDefinitionId(value);
    }

    public DefinitionId value() {
        return value;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ObjectDefinitionId other
                && value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return "ObjectDefinitionId[" + value.asInt() + "]";
    }
}

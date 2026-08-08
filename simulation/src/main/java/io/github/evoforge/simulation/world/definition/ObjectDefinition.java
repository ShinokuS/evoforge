package io.github.evoforge.simulation.world.definition;

public abstract class ObjectDefinition {

    private final String key;

    protected ObjectDefinition(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }

        if (key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }

        this.key = key;
    }

    public final String key() {
        return key;
    }
}
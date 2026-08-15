package io.github.evoforge.simulation.world.landscape.soil;

import java.util.Arrays;

import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;

public final class SoilPropertiesDefinitions {

    private static final int DEFAULT_CAPACITY = 16;

    private SoilProperties[] properties = new SoilProperties[DEFAULT_CAPACITY];
    private boolean frozen;

    public void put(LandscapeDefinitionId id, SoilProperties value) {
        if (frozen) {
            throw new IllegalStateException("soil property definitions are frozen");
        }
        if (id == null || value == null) {
            throw new IllegalArgumentException("soil property definition must not contain null");
        }
        int index = id.asInt();
        ensureCapacity(index + 1);
        if (properties[index] != null) {
            throw new IllegalStateException(
                    "soil property definition already exists: " + id);
        }
        properties[index] = value;
    }

    public boolean has(LandscapeDefinitionId id) {
        if (id == null) return false;
        int index = id.asInt();
        return index < properties.length && properties[index] != null;
    }

    public SoilProperties get(LandscapeDefinitionId id) {
        if (!has(id)) {
            throw new IllegalArgumentException(
                    "soil property definition not found: " + id);
        }
        return properties[id.asInt()];
    }

    public void freeze() {
        frozen = true;
    }

    public boolean isFrozen() {
        return frozen;
    }

    private void ensureCapacity(int requiredCapacity) {
        if (requiredCapacity <= properties.length) return;
        properties = Arrays.copyOf(
                properties,
                Math.max(requiredCapacity, properties.length * 2));
    }
}

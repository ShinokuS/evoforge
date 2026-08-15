package io.github.evoforge.simulation.world.landscape.soil;

import java.util.Arrays;

import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;

public final class SoilPropertiesVariationDefinitions {

    private static final int DEFAULT_CAPACITY = 16;

    private SoilPropertiesVariation[] variations =
            new SoilPropertiesVariation[DEFAULT_CAPACITY];
    private boolean frozen;

    public void put(
            LandscapeDefinitionId id,
            SoilPropertiesVariation value) {
        if (frozen) {
            throw new IllegalStateException("soil property variation definitions are frozen");
        }
        if (id == null || value == null) {
            throw new IllegalArgumentException("soil property variation must not contain null");
        }
        int index = id.asInt();
        ensureCapacity(index + 1);
        if (variations[index] != null) {
            throw new IllegalStateException(
                    "soil property variation already exists: " + id);
        }
        variations[index] = value;
    }

    public SoilPropertiesVariation find(LandscapeDefinitionId id) {
        if (id == null) return null;
        int index = id.asInt();
        return index < variations.length ? variations[index] : null;
    }

    public void freeze() {
        frozen = true;
    }

    public boolean isFrozen() {
        return frozen;
    }

    private void ensureCapacity(int requiredCapacity) {
        if (requiredCapacity <= variations.length) return;
        variations = Arrays.copyOf(
                variations,
                Math.max(requiredCapacity, variations.length * 2));
    }
}

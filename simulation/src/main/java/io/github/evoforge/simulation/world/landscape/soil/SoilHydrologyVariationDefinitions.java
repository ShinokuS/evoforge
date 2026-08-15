package io.github.evoforge.simulation.world.landscape.soil;

import java.util.Arrays;

import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;

/** Optional deterministic per-cell variation declared by a landscape definition. */
public final class SoilHydrologyVariationDefinitions {

    private static final int DEFAULT_CAPACITY = 16;

    private SoilHydrologyVariation[] variations =
            new SoilHydrologyVariation[DEFAULT_CAPACITY];
    private boolean frozen;

    public void put(
            LandscapeDefinitionId id,
            SoilHydrologyVariation variation) {

        if (frozen) {
            throw new IllegalStateException(
                    "soil hydrology variation definitions are frozen");
        }
        if (id == null || variation == null) {
            throw new IllegalArgumentException(
                    "soil hydrology variation arguments must not be null");
        }

        int index = id.asInt();
        ensureCapacity(index + 1);
        if (variations[index] != null) {
            throw new IllegalStateException(
                    "soil hydrology variation already exists: " + id);
        }
        variations[index] = variation;
    }

    public SoilHydrologyVariation find(
            LandscapeDefinitionId id) {

        if (id == null) {
            return null;
        }
        int index = id.asInt();
        return index < variations.length
                ? variations[index]
                : null;
    }

    public void freeze() {
        frozen = true;
    }

    public boolean isFrozen() {
        return frozen;
    }

    private void ensureCapacity(
            int requiredCapacity) {

        if (requiredCapacity <= variations.length) {
            return;
        }
        int next = Math.max(
                requiredCapacity,
                variations.length * 2);
        variations = Arrays.copyOf(variations, next);
    }
}

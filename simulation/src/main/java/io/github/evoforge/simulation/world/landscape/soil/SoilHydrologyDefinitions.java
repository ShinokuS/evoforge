package io.github.evoforge.simulation.world.landscape.soil;

import java.util.Arrays;

import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;

public final class SoilHydrologyDefinitions {

    private static final int DEFAULT_CAPACITY = 16;

    private SoilHydrology[] hydrology =
            new SoilHydrology[DEFAULT_CAPACITY];
    private boolean frozen;

    public void put(
            LandscapeDefinitionId id,
            SoilHydrology value) {

        if (frozen) {
            throw new IllegalStateException(
                    "soil hydrology definitions are frozen");
        }
        if (id == null) {
            throw new IllegalArgumentException(
                    "id must not be null");
        }
        if (value == null) {
            throw new IllegalArgumentException(
                    "value must not be null");
        }

        int index = id.asInt();
        ensureCapacity(index + 1);

        if (hydrology[index] != null) {
            throw new IllegalStateException(
                    "soil hydrology definition already exists: " + id);
        }

        hydrology[index] = value;
    }

    public boolean has(
            LandscapeDefinitionId id) {

        if (id == null) {
            return false;
        }

        int index = id.asInt();
        return index < hydrology.length
                && hydrology[index] != null;
    }

    public SoilHydrology get(
            LandscapeDefinitionId id) {

        if (!has(id)) {
            throw new IllegalArgumentException(
                    "soil hydrology definition not found: " + id);
        }

        return hydrology[id.asInt()];
    }

    public void freeze() {
        frozen = true;
    }

    public boolean isFrozen() {
        return frozen;
    }

    private void ensureCapacity(
            int requiredCapacity) {

        if (requiredCapacity <= hydrology.length) {
            return;
        }

        int newCapacity = Math.max(
                requiredCapacity,
                hydrology.length * 2);

        hydrology = Arrays.copyOf(
                hydrology,
                newCapacity);
    }
}

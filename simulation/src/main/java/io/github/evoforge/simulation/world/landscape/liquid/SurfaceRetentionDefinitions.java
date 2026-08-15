package io.github.evoforge.simulation.world.landscape.liquid;

import java.util.Arrays;

import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolume;

/** Material-owned microtopographic free-liquid retention capacities. */
public final class SurfaceRetentionDefinitions {

    private static final int DEFAULT_CAPACITY = 16;

    private int[] capacities = new int[DEFAULT_CAPACITY];
    private boolean[] present = new boolean[DEFAULT_CAPACITY];
    private boolean frozen;

    public void put(LandscapeDefinitionId id, int capacity) {
        if (frozen) {
            throw new IllegalStateException("surface retention definitions are frozen");
        }
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        int validated = CellVolume.requireValid(capacity);
        int index = id.asInt();
        ensureCapacity(index + 1);
        if (present[index]) {
            throw new IllegalStateException(
                    "surface retention definition already exists: " + id);
        }
        capacities[index] = validated;
        present[index] = true;
    }

    public int getOrZero(LandscapeDefinitionId id) {
        if (id == null) return CellVolume.EMPTY;
        int index = id.asInt();
        return index < present.length && present[index]
                ? capacities[index]
                : CellVolume.EMPTY;
    }

    public void freeze() {
        frozen = true;
    }

    public boolean isFrozen() {
        return frozen;
    }

    private void ensureCapacity(int requiredCapacity) {
        if (requiredCapacity <= capacities.length) return;
        int next = Math.max(requiredCapacity, capacities.length * 2);
        capacities = Arrays.copyOf(capacities, next);
        present = Arrays.copyOf(present, next);
    }
}

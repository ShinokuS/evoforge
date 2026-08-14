package io.github.evoforge.simulation.world.mechanics.traversal.water;

import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;

import java.util.Arrays;

/** Optional per-object-definition terrestrial water traversal capability. */
public final class WaterWadingDefinitions {

    private static final int DEFAULT_CAPACITY = 16;

    private WaterWadingProfile[] profiles =
            new WaterWadingProfile[DEFAULT_CAPACITY];
    private boolean frozen;

    public void put(
            ObjectDefinitionId id,
            WaterWadingProfile profile) {

        if (frozen) {
            throw new IllegalStateException(
                    "water wading definitions are frozen");
        }
        if (id == null) {
            throw new IllegalArgumentException(
                    "id must not be null");
        }
        if (profile == null) {
            throw new IllegalArgumentException(
                    "profile must not be null");
        }

        int index = id.asInt();
        ensureCapacity(index + 1);
        if (profiles[index] != null) {
            throw new IllegalStateException(
                    "water wading definition already exists: " + id);
        }
        profiles[index] = profile;
    }

    public boolean has(
            ObjectDefinitionId id) {

        if (id == null) {
            return false;
        }
        int index = id.asInt();
        return index < profiles.length
                && profiles[index] != null;
    }

    public WaterWadingProfile profile(
            ObjectDefinitionId id) {

        if (!has(id)) {
            throw new IllegalArgumentException(
                    "water wading definition not found: " + id);
        }
        return profiles[id.asInt()];
    }

    public void freeze() {
        frozen = true;
    }

    public boolean isFrozen() {
        return frozen;
    }

    private void ensureCapacity(
            int requiredCapacity) {

        if (requiredCapacity <= profiles.length) {
            return;
        }
        profiles = Arrays.copyOf(
                profiles,
                Math.max(requiredCapacity, profiles.length * 2));
    }
}

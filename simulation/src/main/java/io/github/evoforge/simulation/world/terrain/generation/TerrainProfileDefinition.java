package io.github.evoforge.simulation.world.terrain.generation;

import java.util.List;

/** Authored terrain profile: reusable process preset keys plus one semantic material-set key. */
public record TerrainProfileDefinition(
        String key,
        List<String> presetKeys,
        String materialSetKey) {

    public TerrainProfileDefinition {
        if (key == null || key.isBlank()
                || presetKeys == null
                || materialSetKey == null
                || materialSetKey.isBlank()) {
            throw new IllegalArgumentException("terrain profile fields must not be null or blank");
        }
        presetKeys = List.copyOf(presetKeys);
        for (String presetKey : presetKeys) {
            if (presetKey == null || presetKey.isBlank()) {
                throw new IllegalArgumentException("terrain preset key must not be null or blank");
            }
        }
    }
}

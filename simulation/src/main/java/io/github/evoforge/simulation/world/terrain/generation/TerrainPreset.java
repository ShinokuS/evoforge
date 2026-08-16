package io.github.evoforge.simulation.world.terrain.generation;

/** Resolved reusable terrain-generation behavior selected by an authored palette. */
public record TerrainPreset(
        String key,
        TerrainPresetCapability capability) {

    public TerrainPreset {
        if (key == null || key.isBlank() || capability == null) {
            throw new IllegalArgumentException(
                    "terrain preset key/capability must not be null or blank");
        }
    }
}

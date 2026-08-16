package io.github.evoforge.simulation.world.terrain.generation;

import java.util.List;

/** Small resolved authoring contract that selects reusable terrain processes and materials. */
public record TerrainPalette(
        String key,
        List<TerrainPreset> presets,
        TerrainPaletteMaterials materials) {

    public TerrainPalette {
        if (key == null || key.isBlank() || presets == null || materials == null) {
            throw new IllegalArgumentException(
                    "terrain palette fields must not be null or blank");
        }
        presets = List.copyOf(presets);
    }

    public boolean has(TerrainPresetCapability capability) {
        if (capability == null) {
            return false;
        }
        for (TerrainPreset preset : presets) {
            if (preset.capability() == capability) {
                return true;
            }
        }
        return false;
    }
}

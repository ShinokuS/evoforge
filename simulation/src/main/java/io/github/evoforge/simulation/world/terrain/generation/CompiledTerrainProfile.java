package io.github.evoforge.simulation.world.terrain.generation;

import java.util.List;

/** Runtime-ready terrain generation contract after authored references and roles are validated. */
public record CompiledTerrainProfile(
        String key,
        List<TerrainPreset> presets,
        TerrainMaterialSet materials) {

    public CompiledTerrainProfile {
        if (key == null || key.isBlank() || presets == null || materials == null) {
            throw new IllegalArgumentException("compiled terrain profile fields must not be null or blank");
        }
        presets = List.copyOf(presets);
    }

    public boolean has(TerrainPresetCapability capability) {
        if (capability == null) return false;
        for (TerrainPreset preset : presets) {
            if (preset.capability() == capability) return true;
        }
        return false;
    }
}

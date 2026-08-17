package io.github.evoforge.simulation.world.terrain.generation;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

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

    public Set<TerrainMaterialRole> requiredRoles() {
        EnumSet<TerrainMaterialRole> result = EnumSet.noneOf(TerrainMaterialRole.class);
        for (TerrainPreset preset : presets) result.addAll(preset.requiredRoles());
        return Set.copyOf(result);
    }
}

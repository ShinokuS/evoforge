package io.github.evoforge.simulation.world.terrain.generation;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Explicit catalog of reusable terrain-generation presets available to profile compilation. */
public final class TerrainPresetCatalog {
    public static final String NATURAL_GROUND = "core:natural_ground";
    public static final String DEPOSITIONAL_SAND = "core:depositional_sand";

    private final Map<String, TerrainPreset> presets;

    public TerrainPresetCatalog(Collection<TerrainPreset> presets) {
        if (presets == null) {
            throw new IllegalArgumentException("terrain presets must not be null");
        }
        Map<String, TerrainPreset> byKey = new LinkedHashMap<>();
        for (TerrainPreset preset : presets) {
            if (preset == null) {
                throw new IllegalArgumentException("terrain preset must not be null");
            }
            if (byKey.putIfAbsent(preset.key(), preset) != null) {
                throw new IllegalArgumentException(
                        "duplicate terrain preset: " + preset.key());
            }
        }
        this.presets = Map.copyOf(byKey);
    }

    public static TerrainPresetCatalog standard() {
        return new TerrainPresetCatalog(java.util.List.of(
                new TerrainPreset(
                        NATURAL_GROUND,
                        TerrainPresetCapability.GROUND_PROFILE,
                        TerrainMaterialRole.SURFACE,
                        TerrainMaterialRole.SUBSURFACE,
                        TerrainMaterialRole.BEDROCK),
                new TerrainPreset(
                        DEPOSITIONAL_SAND,
                        TerrainPresetCapability.SURFACE_DEPOSITION,
                        TerrainMaterialRole.SEDIMENT)));
    }

    public TerrainPreset resolve(String key) {
        return presets.get(key);
    }
}

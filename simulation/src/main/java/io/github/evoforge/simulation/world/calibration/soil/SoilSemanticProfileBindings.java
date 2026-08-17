package io.github.evoforge.simulation.world.calibration.soil;

import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialKey;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable authored soil semantics keyed by stable terrain material identity. */
public final class SoilSemanticProfileBindings {
    private final Map<TerrainMaterialKey, SoilSemanticProfile> profiles;

    private SoilSemanticProfileBindings(Map<TerrainMaterialKey, SoilSemanticProfile> profiles) {
        this.profiles = Map.copyOf(profiles);
    }

    public static SoilSemanticProfileBindings of(
            Map<TerrainMaterialKey, SoilSemanticProfile> profiles) {
        if (profiles == null) {
            throw new IllegalArgumentException("soil semantic bindings must not be null");
        }
        Map<TerrainMaterialKey, SoilSemanticProfile> copy = new LinkedHashMap<>();
        for (Map.Entry<TerrainMaterialKey, SoilSemanticProfile> entry : profiles.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new IllegalArgumentException("soil semantic bindings must not contain nulls");
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        return new SoilSemanticProfileBindings(copy);
    }

    public SoilSemanticProfile find(TerrainMaterialKey key) {
        return key == null ? null : profiles.get(key);
    }

    public SoilSemanticProfile require(TerrainMaterialKey key) {
        SoilSemanticProfile profile = find(key);
        if (profile == null) {
            throw new IllegalArgumentException("soil semantic profile is not bound: " + key);
        }
        return profile;
    }

    public Map<TerrainMaterialKey, SoilSemanticProfile> asMap() {
        return profiles;
    }
}

package io.github.evoforge.simulation.world.calibration.soil;

import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialKey;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable algorithm-independent hydraulic facts keyed by stable generated material identity. */
public final class SoilHydraulicProfileBindings {
    private final Map<TerrainMaterialKey, SoilHydraulicProfile> profiles;

    private SoilHydraulicProfileBindings(Map<TerrainMaterialKey, SoilHydraulicProfile> profiles) {
        this.profiles = Map.copyOf(profiles);
    }

    public static SoilHydraulicProfileBindings of(
            Map<TerrainMaterialKey, SoilHydraulicProfile> profiles) {
        if (profiles == null) {
            throw new IllegalArgumentException("soil hydraulic bindings must not be null");
        }
        Map<TerrainMaterialKey, SoilHydraulicProfile> copy = new LinkedHashMap<>();
        for (Map.Entry<TerrainMaterialKey, SoilHydraulicProfile> entry : profiles.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new IllegalArgumentException("soil hydraulic bindings must not contain nulls");
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        return new SoilHydraulicProfileBindings(copy);
    }

    public SoilHydraulicProfile find(TerrainMaterialKey key) {
        return key == null ? null : profiles.get(key);
    }

    public SoilHydraulicProfile require(TerrainMaterialKey key) {
        SoilHydraulicProfile profile = find(key);
        if (profile == null) {
            throw new IllegalArgumentException("soil hydraulic profile is not bound: " + key);
        }
        return profile;
    }

    public Map<TerrainMaterialKey, SoilHydraulicProfile> asMap() {
        return profiles;
    }
}

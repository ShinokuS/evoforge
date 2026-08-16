package io.github.evoforge.simulation.world.terrain.generation;

import java.util.Set;

/** Resolved reusable terrain-generation behavior and the semantic material roles it requires. */
public record TerrainPreset(
        String key,
        TerrainPresetCapability capability,
        Set<TerrainMaterialRole> requiredRoles) {

    public TerrainPreset {
        if (key == null || key.isBlank() || capability == null || requiredRoles == null) {
            throw new IllegalArgumentException(
                    "terrain preset fields must not be null or blank");
        }
        for (TerrainMaterialRole role : requiredRoles) {
            if (role == null) {
                throw new IllegalArgumentException("terrain preset material role must not be null");
            }
        }
        requiredRoles = Set.copyOf(requiredRoles);
    }

    public TerrainPreset(
            String key,
            TerrainPresetCapability capability,
            TerrainMaterialRole... requiredRoles) {
        this(key, capability, Set.of(requiredRoles));
    }
}

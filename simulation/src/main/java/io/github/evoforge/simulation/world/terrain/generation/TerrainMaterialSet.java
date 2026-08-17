package io.github.evoforge.simulation.world.terrain.generation;

import java.util.EnumMap;
import java.util.Map;

/** Compiled immutable material identities assigned to semantic terrain roles. */
public final class TerrainMaterialSet {
    private final String key;
    private final Map<TerrainMaterialRole, TerrainMaterialKey> bindings;

    TerrainMaterialSet(
            String key,
            Map<TerrainMaterialRole, TerrainMaterialKey> bindings) {
        if (key == null || key.isBlank() || bindings == null) {
            throw new IllegalArgumentException("terrain material set fields must not be null or blank");
        }
        this.key = key;
        EnumMap<TerrainMaterialRole, TerrainMaterialKey> copy =
                new EnumMap<>(TerrainMaterialRole.class);
        copy.putAll(bindings);
        this.bindings = Map.copyOf(copy);
    }

    public String key() {
        return key;
    }

    public TerrainMaterialKey get(TerrainMaterialRole role) {
        return role == null ? null : bindings.get(role);
    }

    public TerrainMaterialKey require(TerrainMaterialRole role) {
        TerrainMaterialKey material = get(role);
        if (material == null) {
            throw new IllegalStateException("compiled terrain material role is missing: " + role);
        }
        return material;
    }

    public Map<TerrainMaterialRole, TerrainMaterialKey> asMap() {
        return bindings;
    }
}

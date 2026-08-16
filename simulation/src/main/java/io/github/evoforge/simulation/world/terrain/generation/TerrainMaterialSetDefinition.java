package io.github.evoforge.simulation.world.terrain.generation;

import java.util.EnumMap;
import java.util.Map;

/** Authored semantic terrain-material bindings before preset compilation. */
public record TerrainMaterialSetDefinition(
        String key,
        Map<TerrainMaterialRole, TerrainMaterialKey> bindings) {

    public TerrainMaterialSetDefinition {
        if (key == null || key.isBlank() || bindings == null) {
            throw new IllegalArgumentException("terrain material-set fields must not be null or blank");
        }
        EnumMap<TerrainMaterialRole, TerrainMaterialKey> copy =
                new EnumMap<>(TerrainMaterialRole.class);
        for (Map.Entry<TerrainMaterialRole, TerrainMaterialKey> entry : bindings.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new IllegalArgumentException("terrain material-set binding must not be null");
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        bindings = Map.copyOf(copy);
    }
}

package io.github.evoforge.simulation.world.materialization;

import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.terrain.generation.CompiledTerrainProfile;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialKey;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialRole;
import java.util.LinkedHashMap;
import java.util.Map;

/** Explicit content-composition bridge from generated material keys to runtime landscape ids. */
public final class TerrainMaterialBindings {
    private final Map<TerrainMaterialKey, LandscapeDefinitionId> ids;

    private TerrainMaterialBindings(Map<TerrainMaterialKey, LandscapeDefinitionId> ids) {
        this.ids = Map.copyOf(ids);
    }

    /**
     * Binds every material present in one compiled material set through its semantic role.
     * Extra role ids are harmless; every authored material binding must have one runtime id.
     */
    public static TerrainMaterialBindings forProfile(
            CompiledTerrainProfile profile,
            Map<TerrainMaterialRole, LandscapeDefinitionId> idsByRole) {
        if (profile == null || idsByRole == null) {
            throw new IllegalArgumentException("terrain material bindings must not be null");
        }
        Map<TerrainMaterialKey, LandscapeDefinitionId> ids = new LinkedHashMap<>();
        for (Map.Entry<TerrainMaterialRole, TerrainMaterialKey> entry
                : profile.materials().asMap().entrySet()) {
            LandscapeDefinitionId id = idsByRole.get(entry.getKey());
            if (id == null) {
                throw new IllegalArgumentException(
                        "missing runtime landscape id for terrain material role: "
                                + entry.getKey().authoredName());
            }
            bind(ids, entry.getValue(), id);
        }
        return new TerrainMaterialBindings(ids);
    }

    public LandscapeDefinitionId resolve(TerrainMaterialKey key) {
        return ids.get(key);
    }

    public Map<TerrainMaterialKey, LandscapeDefinitionId> asMap() {
        return ids;
    }

    private static void bind(
            Map<TerrainMaterialKey, LandscapeDefinitionId> ids,
            TerrainMaterialKey key,
            LandscapeDefinitionId id) {
        LandscapeDefinitionId previous = ids.putIfAbsent(key, id);
        if (previous != null && !previous.equals(id)) {
            throw new IllegalArgumentException(
                    "terrain material key is bound to multiple runtime ids: " + key);
        }
    }
}

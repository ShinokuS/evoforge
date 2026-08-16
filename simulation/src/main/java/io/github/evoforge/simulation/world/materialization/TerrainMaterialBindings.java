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

    /** Binds exactly the semantic material roles that this compiled profile can generate. */
    public static TerrainMaterialBindings forProfile(
            CompiledTerrainProfile profile,
            Map<TerrainMaterialRole, LandscapeDefinitionId> idsByRole) {
        if (profile == null || idsByRole == null) {
            throw new IllegalArgumentException("terrain material bindings must not be null");
        }
        Map<TerrainMaterialKey, LandscapeDefinitionId> ids = new LinkedHashMap<>();
        for (TerrainMaterialRole role : profile.requiredRoles()) {
            LandscapeDefinitionId id = idsByRole.get(role);
            if (id == null) {
                throw new IllegalArgumentException(
                        "missing runtime landscape id for terrain material role: "
                                + role.authoredName());
            }
            bind(ids, profile.materials().require(role), id);
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

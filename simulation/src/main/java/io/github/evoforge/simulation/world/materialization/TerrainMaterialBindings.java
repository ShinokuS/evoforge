package io.github.evoforge.simulation.world.materialization;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialKey;
import io.github.evoforge.simulation.world.terrain.generation.TerrainPalette;
import io.github.evoforge.simulation.world.terrain.generation.TerrainPaletteMaterials;

/** Explicit content-composition bridge from semantic generated material keys to runtime ids. */
public final class TerrainMaterialBindings {

    private final Map<TerrainMaterialKey, LandscapeDefinitionId> ids;

    private TerrainMaterialBindings(
            Map<TerrainMaterialKey, LandscapeDefinitionId> ids) {
        this.ids = Map.copyOf(ids);
    }

    public static TerrainMaterialBindings forPalette(
            TerrainPalette palette,
            LandscapeDefinitionId topsoil,
            LandscapeDefinitionId soil,
            LandscapeDefinitionId sand,
            LandscapeDefinitionId rock) {
        if (palette == null
                || topsoil == null
                || soil == null
                || sand == null
                || rock == null) {
            throw new IllegalArgumentException(
                    "terrain material bindings must not be null");
        }
        TerrainPaletteMaterials materials = palette.materials();
        Map<TerrainMaterialKey, LandscapeDefinitionId> ids =
                new LinkedHashMap<>();
        bind(ids, materials.topsoil(), topsoil);
        bind(ids, materials.soil(), soil);
        bind(ids, materials.sand(), sand);
        bind(ids, materials.rock(), rock);
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

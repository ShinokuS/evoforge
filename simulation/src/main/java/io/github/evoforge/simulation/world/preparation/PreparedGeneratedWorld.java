package io.github.evoforge.simulation.world.preparation;

import io.github.evoforge.simulation.world.atlas.WorldAtlas;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialField;

/** Immutable generated preparation output consumed later by runtime materialization. */
public record PreparedGeneratedWorld(
        WorldAtlas atlas,
        TerrainMaterialField terrainMaterials,
        GeneratedLandscapeProperties landscapeProperties) {

    public PreparedGeneratedWorld(WorldAtlas atlas, TerrainMaterialField terrainMaterials) {
        this(atlas, terrainMaterials, emptyPropertiesFor(atlas));
    }

    public PreparedGeneratedWorld {
        if (atlas == null || terrainMaterials == null || landscapeProperties == null) {
            throw new IllegalArgumentException("prepared generated world components must not be null");
        }
        var bounds = atlas.genesis().spec().bounds();
        if (!bounds.equals(terrainMaterials.bounds())) {
            throw new IllegalArgumentException("prepared terrain materials must match atlas bounds");
        }
        if (!bounds.equals(landscapeProperties.bounds())) {
            throw new IllegalArgumentException("generated landscape properties must match atlas bounds");
        }
    }

    public PreparedGeneratedWorld withLandscapeProperties(GeneratedLandscapeProperties properties) {
        return new PreparedGeneratedWorld(atlas, terrainMaterials, properties);
    }

    private static GeneratedLandscapeProperties emptyPropertiesFor(WorldAtlas atlas) {
        if (atlas == null) {
            throw new IllegalArgumentException("prepared generated world components must not be null");
        }
        return GeneratedLandscapeProperties.empty(atlas.genesis().spec().bounds());
    }
}

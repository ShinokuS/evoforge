package io.github.evoforge.simulation.world.preparation;

import io.github.evoforge.simulation.world.atlas.WorldAtlas;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialField;
import io.github.evoforge.simulation.world.terrain.shape.TerrainShapeField;

/** Immutable generated preparation output consumed later by runtime materialization. */
public record PreparedGeneratedWorld(
        WorldAtlas atlas,
        TerrainMaterialField terrainMaterials,
        TerrainShapeField terrainShapes,
        GeneratedLandscapeProperties landscapeProperties) {

    public PreparedGeneratedWorld(WorldAtlas atlas, TerrainMaterialField terrainMaterials) {
        this(
                atlas,
                terrainMaterials,
                baselineShapesFor(atlas),
                emptyPropertiesFor(atlas));
    }

    public PreparedGeneratedWorld(
            WorldAtlas atlas,
            TerrainMaterialField terrainMaterials,
            GeneratedLandscapeProperties landscapeProperties) {
        this(atlas, terrainMaterials, baselineShapesFor(atlas), landscapeProperties);
    }

    public PreparedGeneratedWorld {
        if (atlas == null
                || terrainMaterials == null
                || terrainShapes == null
                || landscapeProperties == null) {
            throw new IllegalArgumentException("prepared generated world components must not be null");
        }
        var bounds = atlas.genesis().spec().bounds();
        if (!bounds.equals(terrainMaterials.bounds())) {
            throw new IllegalArgumentException("prepared terrain materials must match atlas bounds");
        }
        if (!bounds.equals(terrainShapes.bounds())) {
            throw new IllegalArgumentException("prepared terrain shapes must match atlas bounds");
        }
        if (!bounds.equals(landscapeProperties.bounds())) {
            throw new IllegalArgumentException("generated landscape properties must match atlas bounds");
        }
    }

    public PreparedGeneratedWorld withLandscapeProperties(GeneratedLandscapeProperties properties) {
        return new PreparedGeneratedWorld(atlas, terrainMaterials, terrainShapes, properties);
    }

    private static TerrainShapeField baselineShapesFor(WorldAtlas atlas) {
        if (atlas == null) {
            throw new IllegalArgumentException("prepared generated world components must not be null");
        }
        return TerrainShapeField.baseline(atlas.genesis().spec().bounds());
    }

    private static GeneratedLandscapeProperties emptyPropertiesFor(WorldAtlas atlas) {
        if (atlas == null) {
            throw new IllegalArgumentException("prepared generated world components must not be null");
        }
        return GeneratedLandscapeProperties.empty(atlas.genesis().spec().bounds());
    }
}

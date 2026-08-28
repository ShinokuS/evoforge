package io.github.evoforge.simulation.world.terrain.shape;

import io.github.evoforge.simulation.world.geometry.Shape;
import java.util.Optional;

/** One geometry candidate and its optional runtime Shape representation. */
public record TerrainShapeTemplate(
        TerrainSurfacePatch surface,
        Optional<Shape> shapeOverride) {

    public TerrainShapeTemplate {
        if (surface == null || shapeOverride == null) {
            throw new IllegalArgumentException("terrain shape template values must not be null");
        }
    }

    public static TerrainShapeTemplate baseline(TerrainSurfacePatch surface) {
        return new TerrainShapeTemplate(surface, Optional.empty());
    }

    public static TerrainShapeTemplate shaped(TerrainSurfacePatch surface, Shape shape) {
        if (shape == null) throw new IllegalArgumentException("runtime Shape must not be null");
        return new TerrainShapeTemplate(surface, Optional.of(shape));
    }
}

package io.github.evoforge.simulation.world.terrain.shape;

import io.github.evoforge.simulation.world.mechanics.geometry.RampShape;
import java.util.List;

/** Ordered geometry vocabulary available to generated Terrain. The first template is the fallback. */
public final class TerrainShapePalette {
    private final List<TerrainShapeTemplate> templates;

    public TerrainShapePalette(List<TerrainShapeTemplate> templates) {
        if (templates == null || templates.isEmpty()) {
            throw new IllegalArgumentException("terrain shape palette must not be empty");
        }
        List<TerrainShapeTemplate> copy = List.copyOf(templates);
        if (copy.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException("terrain shape palette must not contain null");
        }
        if (copy.get(0).shapeOverride().isPresent()) {
            throw new IllegalArgumentException("first terrain shape template must be the implicit baseline");
        }
        if (copy.size() > 256) {
            throw new IllegalArgumentException("current compact terrain shape field supports at most 256 templates");
        }
        this.templates = copy;
    }

    public List<TerrainShapeTemplate> templates() {
        return templates;
    }

    /** Current vocabulary. Adding another Shape only adds another template here. */
    public static TerrainShapePalette standard() {
        return new TerrainShapePalette(List.of(
                TerrainShapeTemplate.baseline(TerrainSurfacePatch.flatTop()),
                TerrainShapeTemplate.shaped(
                        TerrainSurfacePatch.cardinalRamp(1, 0), RampShape.POSITIVE_X),
                TerrainShapeTemplate.shaped(
                        TerrainSurfacePatch.cardinalRamp(-1, 0), RampShape.NEGATIVE_X),
                TerrainShapeTemplate.shaped(
                        TerrainSurfacePatch.cardinalRamp(0, 1), RampShape.POSITIVE_Y),
                TerrainShapeTemplate.shaped(
                        TerrainSurfacePatch.cardinalRamp(0, -1), RampShape.NEGATIVE_Y)));
    }
}

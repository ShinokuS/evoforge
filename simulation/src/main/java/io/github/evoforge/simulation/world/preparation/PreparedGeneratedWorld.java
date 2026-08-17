package io.github.evoforge.simulation.world.preparation;

import io.github.evoforge.simulation.world.atlas.WorldAtlas;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialField;

/**
 * Immutable generated preparation output consumed later by runtime materialization.
 *
 * <p>This object contains only durable generated facts and stable material identities. It owns no
 * runtime stores, schedulers, mutable WeatherState, or simulation process.</p>
 */
public record PreparedGeneratedWorld(
        WorldAtlas atlas,
        TerrainMaterialField terrainMaterials) {

    public PreparedGeneratedWorld {
        if (atlas == null || terrainMaterials == null) {
            throw new IllegalArgumentException("prepared generated world components must not be null");
        }
        if (!atlas.genesis().spec().bounds().equals(terrainMaterials.bounds())) {
            throw new IllegalArgumentException("prepared terrain materials must match atlas bounds");
        }
    }
}

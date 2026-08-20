package io.github.evoforge.simulation.world.atlas.hydrology;

import io.github.evoforge.simulation.world.atlas.ElevationField;

/**
 * Converts already-derived terrain depression facts into standing inland water without modifying
 * terrain elevation.
 */
@FunctionalInterface
public interface InlandLakeFormationAlgorithm {

    InlandLakeTopology generate(
            ElevationField elevation,
            DrainageBasinTopology basins,
            InlandLakeFormationRecipe recipe);

    static InlandLakeFormationAlgorithm standard() {
        return new SpillLevelInlandLakeFormationAlgorithm();
    }
}

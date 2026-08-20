package io.github.evoforge.simulation.world.atlas.hydrology;

import io.github.evoforge.simulation.world.atlas.ElevationField;

/**
 * Policy for deciding which terrain depression basins are significant enough to become standing
 * inland water.
 */
public record InlandLakeFormationRecipe(
        long minimumCells,
        long minimumMaximumDepthSubunits,
        boolean requireTwoByTwoInterior) {

    public InlandLakeFormationRecipe {
        if (minimumCells <= 0L) {
            throw new IllegalArgumentException("minimum lake cells must be positive");
        }
        if (minimumMaximumDepthSubunits <= 0L) {
            throw new IllegalArgumentException("minimum lake depth must be positive");
        }
    }

    public static InlandLakeFormationRecipe balanced() {
        return new InlandLakeFormationRecipe(
                4L,
                ElevationField.SUBUNITS_PER_CELL / 4L,
                true);
    }
}

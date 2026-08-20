package io.github.evoforge.simulation.world.atlas.hydrology;

import io.github.evoforge.simulation.world.atlas.ElevationField;

/** Policy for broad closed depressions that may later host inland standing water. */
public record LacustrineBasinMorphologyRecipe(
        long targetLandCellsPerBasin,
        int minimumRadiusCells,
        int maximumRadiusCells,
        long minimumDepthSubunits,
        long maximumDepthSubunits,
        int maximumBasinCount) {

    public LacustrineBasinMorphologyRecipe {
        if (targetLandCellsPerBasin <= 0L) {
            throw new IllegalArgumentException("target land cells per basin must be positive");
        }
        if (minimumRadiusCells < 2 || maximumRadiusCells < minimumRadiusCells) {
            throw new IllegalArgumentException("lacustrine basin radius bounds are invalid");
        }
        if (minimumDepthSubunits <= 0L || maximumDepthSubunits < minimumDepthSubunits) {
            throw new IllegalArgumentException("lacustrine basin depth bounds are invalid");
        }
        if (maximumBasinCount <= 0) {
            throw new IllegalArgumentException("maximum basin count must be positive");
        }
    }

    public static LacustrineBasinMorphologyRecipe balanced() {
        return new LacustrineBasinMorphologyRecipe(
                4_500L,
                2,
                5,
                ElevationField.SUBUNITS_PER_CELL / 2L,
                ElevationField.SUBUNITS_PER_CELL * 2L,
                8);
    }
}

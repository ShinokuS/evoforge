package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.genesis.WorldGenesis;

/**
 * Replaceable spatial algorithm that applies one calibrated mountain model to an existing elevation
 * fact.
 *
 * <p>The algorithm consumes already-calibrated cell-space parameters. It does not own semantic
 * intent compilation, base-terrain generation or later Shape fitting.</p>
 */
@FunctionalInterface
public interface MountainElevationAlgorithm {
    ElevationField generate(
            WorldGenesis genesis,
            ElevationField base,
            MountainCalibration calibration,
            MountainRecipe recipe);
}

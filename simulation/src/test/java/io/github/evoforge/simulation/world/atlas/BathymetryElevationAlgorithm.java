package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.genesis.WorldGenesis;

/** Replaceable spatial synthesis contract for standing-water bathymetry. */
@FunctionalInterface
public interface BathymetryElevationAlgorithm {

    ElevationField generate(
            WorldGenesis genesis,
            ElevationField base,
            BathymetryCalibration calibration,
            BathymetryRecipe recipe);
}

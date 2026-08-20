package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.genesis.WorldGenesis;

/** Replaceable owner of broad dry continental lowlands and closed-depression morphology. */
@FunctionalInterface
public interface InlandBasinMorphologyAlgorithm {
    ElevationField generate(
            WorldGenesis genesis,
            ElevationField baseElevation,
            InlandBasinMorphologyCalibration calibration,
            InlandBasinMorphologyRecipe recipe);

    static InlandBasinMorphologyAlgorithm standard() {
        return MultiLobeInlandBasinMorphologyAlgorithm.INSTANCE;
    }
}

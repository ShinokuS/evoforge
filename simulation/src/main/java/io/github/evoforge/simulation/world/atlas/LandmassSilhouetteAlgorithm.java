package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.genesis.WorldGenesis;

/** Replaceable owner of V14 continent/island silhouette geometry. */
@FunctionalInterface
public interface LandmassSilhouetteAlgorithm {
    LandmassSilhouette generate(
            WorldGenesis genesis,
            LandmassBoundaryCalibration boundary,
            LandmassSilhouetteCalibration calibration,
            LandmassSilhouetteRecipe recipe);

    static LandmassSilhouetteAlgorithm standard() {
        return HarmonicLandmassSilhouetteAlgorithm.INSTANCE;
    }
}

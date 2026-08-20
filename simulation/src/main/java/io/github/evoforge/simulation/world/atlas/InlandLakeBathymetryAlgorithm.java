package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.genesis.WorldGenesis;

/** Refines depth of existing inland Z=0 water while preserving its membership and all dry terrain. */
@FunctionalInterface
public interface InlandLakeBathymetryAlgorithm {
    ElevationField generate(
            WorldGenesis genesis,
            ElevationField bathymetricTerrain,
            InlandLakeBathymetryRecipe recipe);

    static InlandLakeBathymetryAlgorithm standard() {
        return DistanceProfileInlandLakeBathymetryAlgorithm.INSTANCE;
    }
}

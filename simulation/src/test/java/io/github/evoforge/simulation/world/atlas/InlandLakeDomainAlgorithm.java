package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.genesis.WorldGenesis;

/** Selects inland-water membership from existing continental lowland geometry without changing elevation. */
@FunctionalInterface
public interface InlandLakeDomainAlgorithm {
    InlandLakeDomain generate(
            WorldGenesis genesis,
            ElevationField continentalBase,
            InlandLakeDomainCalibration calibration,
            InlandLakeDomainRecipe recipe);

    static InlandLakeDomainAlgorithm standard() {
        return TerrainLowlandInlandLakeDomainAlgorithm.INSTANCE;
    }
}

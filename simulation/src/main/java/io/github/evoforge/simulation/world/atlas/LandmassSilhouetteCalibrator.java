package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;

/** Resolves semantic continent scale and fragmentation into compact-region operating values. */
@FunctionalInterface
public interface LandmassSilhouetteCalibrator {
    LandmassSilhouetteCalibration calibrate(
            WorldGenesis genesis,
            V12LandformCalibration terrain,
            LandmassSilhouetteRecipe recipe);

    static LandmassSilhouetteCalibrator standard() {
        return StandardLandmassSilhouetteCalibrator.INSTANCE;
    }
}

final class StandardLandmassSilhouetteCalibrator implements LandmassSilhouetteCalibrator {
    static final StandardLandmassSilhouetteCalibrator INSTANCE = new StandardLandmassSilhouetteCalibrator();
    private static final int PPM = NormalizedValue.SCALE;

    private StandardLandmassSilhouetteCalibrator() {
    }

    @Override
    public LandmassSilhouetteCalibration calibrate(
            WorldGenesis genesis,
            V12LandformCalibration terrain,
            LandmassSilhouetteRecipe recipe) {
        if (genesis == null || terrain == null || recipe == null) {
            throw new IllegalArgumentException("landmass silhouette calibration inputs must not be null");
        }

        int limitingSpan = Math.min(terrain.width(), terrain.height());
        int scalePpm = genesis.generationIntent().landmassScale().partsPerMillion();
        int fragmentationPpm = terrain.fragmentationPpm();
        LandmassSilhouetteRecipe.ScaffoldPolicy scaffold = recipe.scaffold();

        long spacingWorldPpm = scaffold.minimumSpacingWorldPpm()
                + (long) (scaffold.maximumSpacingWorldPpm() - scaffold.minimumSpacingWorldPpm())
                        * scalePpm / PPM;
        long compressionPpm = (long) fragmentationPpm
                * scaffold.fragmentationSpacingCompressionPpm() / PPM;
        spacingWorldPpm = spacingWorldPpm * (PPM - compressionPpm) / PPM;
        int spacing = Math.max(
                scaffold.minimumSpacingCells(),
                Math.toIntExact((long) limitingSpan * spacingWorldPpm / PPM));

        LandmassSilhouetteRecipe.GrowthPolicy growth = recipe.growth();
        int requestedClusters = 1 + Math.toIntExact(
                ((long) fragmentationPpm * (growth.maximumClusterCount() - 1) + PPM / 2L) / PPM);
        int approximateColumns = Math.max(1, Math.floorDiv(terrain.width() + spacing - 1, spacing));
        int approximateRows = Math.max(1, Math.floorDiv(terrain.height() + spacing - 1, spacing));
        int structuralCapacity = Math.max(1, Math.multiplyExact(approximateColumns, approximateRows) / 3);
        int clusters = Math.max(1, Math.min(requestedClusters, structuralCapacity));

        return new LandmassSilhouetteCalibration(
                spacing,
                clusters,
                fragmentationPpm,
                recipe.blend().silhouetteInfluencePpm());
    }
}

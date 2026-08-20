package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;

/** Resolves semantic continent scale and fragmentation into plate-scaffold operating values. */
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
        LandmassSilhouetteRecipe.PlatePolicy plates = recipe.plates();

        long spacingWorldPpm = plates.minimumSpacingWorldPpm()
                + (long) (plates.maximumSpacingWorldPpm() - plates.minimumSpacingWorldPpm())
                        * scalePpm / PPM;
        long compressionPpm = (long) fragmentationPpm
                * plates.fragmentationSpacingCompressionPpm() / PPM;
        spacingWorldPpm = spacingWorldPpm * (PPM - compressionPpm) / PPM;
        int spacing = Math.max(
                plates.minimumSpacingCells(),
                Math.toIntExact((long) limitingSpan * spacingWorldPpm / PPM));

        long cohesionPpm = (long) scalePpm * (PPM - fragmentationPpm) / PPM;
        int correlationPasses = Math.toIntExact(
                (cohesionPpm * plates.maximumCorrelationPasses() + PPM / 2L) / PPM);

        return new LandmassSilhouetteCalibration(
                spacing,
                correlationPasses,
                fragmentationPpm,
                recipe.blend().silhouetteInfluencePpm());
    }
}

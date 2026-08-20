package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;

/** Resolves semantic continent scale and fragmentation into exact silhouette operating values. */
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
        LandmassSilhouetteRecipe.BodyPolicy bodies = recipe.bodies();

        int extraBodies = Math.toIntExact(
                ((long) (PPM - scalePpm) * bodies.additionalBodiesAtMinimumScale() + PPM / 2L) / PPM);
        int primaryBodies = bodies.minimumBodyCount() + extraBodies;
        if (fragmentationPpm >= 700_000 && primaryBodies < bodies.minimumBodyCount() + bodies.additionalBodiesAtMinimumScale() + 1) {
            primaryBodies++;
        }

        int satelliteBodies = Math.toIntExact(
                ((long) fragmentationPpm * bodies.maximumSatelliteBodies() + PPM / 2L) / PPM);

        int radiusWorldPpm;
        if (primaryBodies == 1) {
            radiusWorldPpm = bodies.singleBodyRadiusWorldPpm();
        } else if (primaryBodies == 2) {
            radiusWorldPpm = bodies.twoBodyRadiusWorldPpm();
        } else {
            radiusWorldPpm = bodies.manyBodyRadiusWorldPpm();
        }
        int primaryRadius = Math.max(4, Math.toIntExact((long) limitingSpan * radiusWorldPpm / PPM));

        LandmassSilhouetteRecipe.CoastPolicy coast = recipe.coast();
        int irregularity = coast.minimumIrregularityPpm()
                + Math.toIntExact((long) fragmentationPpm
                        * coast.fragmentationIrregularityRangePpm() / PPM);

        return new LandmassSilhouetteCalibration(
                primaryBodies,
                satelliteBodies,
                primaryRadius,
                irregularity,
                recipe.blend().silhouetteInfluencePpm());
    }
}

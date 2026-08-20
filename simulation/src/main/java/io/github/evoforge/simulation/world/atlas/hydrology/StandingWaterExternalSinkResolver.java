package io.github.evoforge.simulation.world.atlas.hydrology;

/** Classifies which hydrologic standing-water bodies act as external drainage sinks. */
@FunctionalInterface
public interface StandingWaterExternalSinkResolver {
    StandingWaterExternalSinkTopology resolve(
            StandingWaterTopology standingWater,
            StandingWaterMorphologyTopology morphology,
            StandingWaterExternalSinkCalibration calibration);

    static StandingWaterExternalSinkResolver standard() {
        return ScaleAwareStandingWaterExternalSinkResolver.INSTANCE;
    }
}

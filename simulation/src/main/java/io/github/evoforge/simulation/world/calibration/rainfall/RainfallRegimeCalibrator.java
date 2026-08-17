package io.github.evoforge.simulation.world.calibration.rainfall;

import io.github.evoforge.simulation.world.climate.ClimateNormalsField;

/** Replaceable preparation-time calibration from climate facts to rainfall-regime statistics. */
@FunctionalInterface
public interface RainfallRegimeCalibrator {
    RainfallRegimeField calibrate(
            ClimateNormalsField climate,
            RainfallOccurrenceField occurrence);
}

package io.github.evoforge.simulation.world.calibration.rainfall;

import io.github.evoforge.simulation.world.mechanics.measurement.WaterDepthRate;

/** Immutable calibrated rainfall statistics at one world column. */
public record RainfallRegime(
        WaterDepthRate longTermMeanPrecipitation,
        RainfallOccurrenceNormal occurrence) {

    public RainfallRegime {
        if (longTermMeanPrecipitation == null || occurrence == null) {
            throw new IllegalArgumentException("rainfall regime components must not be null");
        }
    }
}

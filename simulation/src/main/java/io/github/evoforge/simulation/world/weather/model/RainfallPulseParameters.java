package io.github.evoforge.simulation.world.weather.model;

import io.github.evoforge.simulation.world.mechanics.measurement.WaterDepthRate;
import java.time.Duration;

/**
 * Compiled physical parameters for an alternating dry/wet rectangular-pulse rainfall process.
 *
 * <p>This is deliberately a runtime/calibration product, not authored world intent. A higher-level
 * climate calibrator is expected to derive these values from spatial climate statistics.</p>
 */
public record RainfallPulseParameters(
        Duration meanDrySpellDuration,
        Duration meanWetSpellDuration,
        WaterDepthRate meanWetIntensity) {

    public RainfallPulseParameters {
        if (meanDrySpellDuration == null
                || meanWetSpellDuration == null
                || meanWetIntensity == null) {
            throw new IllegalArgumentException("rainfall pulse parameters must not be null");
        }
        if (meanDrySpellDuration.isZero() || meanDrySpellDuration.isNegative()) {
            throw new IllegalArgumentException("mean dry-spell duration must be positive");
        }
        if (meanWetSpellDuration.isZero() || meanWetSpellDuration.isNegative()) {
            throw new IllegalArgumentException("mean wet-spell duration must be positive");
        }
        if (meanWetIntensity.depthNanometersNumerator().signum() <= 0) {
            throw new IllegalArgumentException("mean wet intensity must be positive");
        }
    }
}

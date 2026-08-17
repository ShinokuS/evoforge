package io.github.evoforge.simulation.world.weather.model;

import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.evoforge.simulation.world.mechanics.measurement.WaterDepthRate;
import java.time.Duration;
import org.junit.jupiter.api.Test;

final class RainfallPulseParametersTest {

    @Test
    void rejectsMissingNonPositiveOrZeroIntensityParameters() {
        WaterDepthRate positive = WaterDepthRate.ofMillimeters(1L, Duration.ofHours(1L));

        assertThrows(
                IllegalArgumentException.class,
                () -> new RainfallPulseParameters(null, Duration.ofHours(1L), positive));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RainfallPulseParameters(Duration.ZERO, Duration.ofHours(1L), positive));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RainfallPulseParameters(Duration.ofHours(1L), Duration.ZERO, positive));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RainfallPulseParameters(
                        Duration.ofHours(1L),
                        Duration.ofHours(1L),
                        WaterDepthRate.ZERO));
    }
}

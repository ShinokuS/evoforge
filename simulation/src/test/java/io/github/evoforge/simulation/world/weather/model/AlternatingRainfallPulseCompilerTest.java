package io.github.evoforge.simulation.world.weather.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.evoforge.simulation.world.calibration.rainfall.RainfallOccurrenceNormal;
import io.github.evoforge.simulation.world.calibration.rainfall.RainfallRegime;
import io.github.evoforge.simulation.world.mechanics.measurement.WaterDepthRate;
import java.time.Duration;
import org.junit.jupiter.api.Test;

final class AlternatingRainfallPulseCompilerTest {

    @Test
    void preservesLongTermMeanByScalingWetIntensityWithWetTimeFraction() {
        RainfallRegime regime = new RainfallRegime(
                WaterDepthRate.ofMillimeters(800L, Duration.ofDays(365L)),
                new RainfallOccurrenceNormal(Duration.ofHours(18), Duration.ofHours(6)));

        RainfallPulseParameters compiled = AlternatingRainfallPulseCompiler.compile(regime);

        assertEquals(Duration.ofHours(18), compiled.meanDrySpellDuration());
        assertEquals(Duration.ofHours(6), compiled.meanWetSpellDuration());
        assertEquals(
                WaterDepthRate.ofMillimeters(3_200L, Duration.ofDays(365L)),
                compiled.meanWetIntensity());
    }

    @Test
    void rejectsZeroPrecipitationInsteadOfInventingRainEvents() {
        RainfallRegime dry = new RainfallRegime(
                WaterDepthRate.ZERO,
                new RainfallOccurrenceNormal(Duration.ofHours(18), Duration.ofHours(6)));

        assertThrows(
                IllegalArgumentException.class,
                () -> AlternatingRainfallPulseCompiler.compile(dry));
    }
}

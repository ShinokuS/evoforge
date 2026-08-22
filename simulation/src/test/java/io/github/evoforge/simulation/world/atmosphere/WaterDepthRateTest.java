package io.github.evoforge.simulation.world.atmosphere;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.time.Duration;
import org.junit.jupiter.api.Test;

final class WaterDepthRateTest {

    @Test
    void storesPhysicalDepthPerTimeAsCanonicalExactRatio() {
        WaterDepthRate oneMillimeterPerSecond = WaterDepthRate.ofMillimeters(
                1L,
                Duration.ofSeconds(1L));

        assertEquals(BigInteger.ONE, oneMillimeterPerSecond.depthNanometersNumerator());
        assertEquals(BigInteger.valueOf(1_000L), oneMillimeterPerSecond.durationNanosecondsDenominator());
        assertEquals(
                WaterDepthRate.ofMicrometers(1_000L, Duration.ofSeconds(1L)),
                oneMillimeterPerSecond);
    }

    @Test
    void zeroHasOneCanonicalRepresentationAndInvalidRatesAreRejected() {
        assertEquals(WaterDepthRate.ZERO, WaterDepthRate.ofMillimeters(0L, Duration.ofDays(365L)));
        assertThrows(IllegalArgumentException.class,
                () -> WaterDepthRate.ofMillimeters(-1L, Duration.ofSeconds(1L)));
        assertThrows(IllegalArgumentException.class,
                () -> WaterDepthRate.ofMillimeters(1L, Duration.ZERO));
    }
}

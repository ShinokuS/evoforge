package io.github.evoforge.simulation.kernel.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.time.Duration;
import org.junit.jupiter.api.Test;

final class SimulationTimeScaleTest {

    @Test
    void convertsTicksToExactPhysicalNanoseconds() {
        SimulationTimeScale scale = SimulationTimeScale.of(Duration.ofMillis(250L));

        assertEquals(BigInteger.valueOf(250_000_000L), scale.nanosecondsPerTick());
        assertEquals(BigInteger.ZERO, scale.elapsedNanoseconds(0L));
        assertEquals(BigInteger.valueOf(1_000_000_000L), scale.elapsedNanoseconds(4L));
    }

    @Test
    void quantizesPositivePhysicalDurationsByCeiling() {
        SimulationTimeScale scale = SimulationTimeScale.of(Duration.ofMillis(250L));

        assertEquals(1L, scale.ticksForCeiling(BigInteger.ONE));
        assertEquals(1L, scale.ticksForCeiling(BigInteger.valueOf(250_000_000L)));
        assertEquals(2L, scale.ticksForCeiling(BigInteger.valueOf(250_000_001L)));
        assertEquals(4L, scale.ticksForCeiling(BigInteger.valueOf(1_000_000_000L)));
    }

    @Test
    void exactConversionDoesNotOverflowForLargeTickCounts() {
        SimulationTimeScale scale = SimulationTimeScale.of(Duration.ofSeconds(Long.MAX_VALUE));

        assertEquals(
                BigInteger.valueOf(Long.MAX_VALUE)
                        .multiply(BigInteger.valueOf(1_000_000_000L))
                        .multiply(BigInteger.valueOf(Long.MAX_VALUE)),
                scale.elapsedNanoseconds(Long.MAX_VALUE));
    }

    @Test
    void rejectsMissingNonPositiveDurationAndNegativeTicks() {
        assertThrows(IllegalArgumentException.class, () -> new SimulationTimeScale(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> SimulationTimeScale.of(Duration.ZERO));
        assertThrows(
                IllegalArgumentException.class,
                () -> SimulationTimeScale.of(Duration.ofNanos(-1L)));
        SimulationTimeScale scale = SimulationTimeScale.of(Duration.ofNanos(1L));
        assertThrows(IllegalArgumentException.class, () -> scale.elapsedNanoseconds(-1L));
        assertThrows(IllegalArgumentException.class, () -> scale.ticksForCeiling(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> scale.ticksForCeiling(BigInteger.ZERO));
    }
}

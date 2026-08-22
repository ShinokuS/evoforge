package io.github.evoforge.simulation.kernel.time;

import java.math.BigInteger;
import java.time.Duration;

/**
 * Exact physical duration represented by one deterministic simulation tick.
 *
 * <p>The scheduler remains tick-based; this value is a conversion contract for systems that need
 * physical time. It therefore does not change ordering, cadence, or determinism merely by existing.
 * No engine-wide default duration is implied.</p>
 */
public record SimulationTimeScale(Duration tickDuration) {

    private static final BigInteger NANOS_PER_SECOND = BigInteger.valueOf(1_000_000_000L);

    public SimulationTimeScale {
        if (tickDuration == null) {
            throw new IllegalArgumentException("tick duration must not be null");
        }
        if (tickDuration.isZero() || tickDuration.isNegative()) {
            throw new IllegalArgumentException("tick duration must be positive");
        }
    }

    public static SimulationTimeScale of(Duration tickDuration) {
        return new SimulationTimeScale(tickDuration);
    }

    /** Exact duration of one tick in nanoseconds without {@code long} overflow. */
    public BigInteger nanosecondsPerTick() {
        return BigInteger.valueOf(tickDuration.getSeconds())
                .multiply(NANOS_PER_SECOND)
                .add(BigInteger.valueOf(tickDuration.getNano()));
    }

    /** Exact elapsed physical nanoseconds represented by a non-negative tick count. */
    public BigInteger elapsedNanoseconds(long ticks) {
        if (ticks < 0L) {
            throw new IllegalArgumentException("tick count must be non-negative");
        }
        return nanosecondsPerTick().multiply(BigInteger.valueOf(ticks));
    }

    /**
     * Smallest positive number of whole simulation ticks that can represent the supplied physical
     * duration. This is the canonical quantization boundary for event-driven physical processes.
     */
    public long ticksForCeiling(BigInteger durationNanoseconds) {
        if (durationNanoseconds == null || durationNanoseconds.signum() <= 0) {
            throw new IllegalArgumentException("physical duration must be positive");
        }
        BigInteger[] division = durationNanoseconds.divideAndRemainder(nanosecondsPerTick());
        BigInteger ticks = division[0];
        if (division[1].signum() != 0) {
            ticks = ticks.add(BigInteger.ONE);
        }
        if (ticks.signum() <= 0) {
            ticks = BigInteger.ONE;
        }
        if (ticks.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
            throw new IllegalStateException("physical duration exceeds simulation tick range");
        }
        return ticks.longValueExact();
    }
}

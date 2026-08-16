package io.github.evoforge.simulation.world.mechanics.geometry;

/**
 * Exact non-negative rational rate measured in {@link CellVolume} units per simulation tick.
 *
 * <p>The rate preserves simulation dimensions without assigning a real-world duration to one tick.
 * Numerator and denominator are always stored in reduced form.
 */
public record CellVolumeRate(
        long volumeUnitsNumerator,
        long tickDenominator) {

    public static final CellVolumeRate ZERO = new CellVolumeRate(0L, 1L);

    public CellVolumeRate {
        if (volumeUnitsNumerator < 0L) {
            throw new IllegalArgumentException(
                    "volumeUnitsNumerator must be non-negative");
        }
        if (tickDenominator <= 0L) {
            throw new IllegalArgumentException(
                    "tickDenominator must be positive");
        }

        long divisor = greatestCommonDivisor(
                volumeUnitsNumerator,
                tickDenominator);
        volumeUnitsNumerator /= divisor;
        tickDenominator /= divisor;
    }

    public static CellVolumeRate of(
            long volumeUnits,
            long ticks) {
        return new CellVolumeRate(volumeUnits, ticks);
    }

    /**
     * Creates {@code volumeUnitsPerEvent * eventCount / ticks} while reducing factors before
     * multiplication so large cycle counts do not overflow merely because the unreduced total does.
     */
    public static CellVolumeRate ofEvents(
            long volumeUnitsPerEvent,
            long eventCount,
            long ticks) {
        if (volumeUnitsPerEvent < 0L) {
            throw new IllegalArgumentException(
                    "volumeUnitsPerEvent must be non-negative");
        }
        if (eventCount < 0L) {
            throw new IllegalArgumentException(
                    "eventCount must be non-negative");
        }
        if (ticks <= 0L) {
            throw new IllegalArgumentException("ticks must be positive");
        }
        if (volumeUnitsPerEvent == 0L || eventCount == 0L) {
            return ZERO;
        }

        long reducedEvents = eventCount;
        long reducedUnits = volumeUnitsPerEvent;
        long reducedTicks = ticks;

        long eventDivisor = greatestCommonDivisor(reducedEvents, reducedTicks);
        reducedEvents /= eventDivisor;
        reducedTicks /= eventDivisor;

        long unitDivisor = greatestCommonDivisor(reducedUnits, reducedTicks);
        reducedUnits /= unitDivisor;
        reducedTicks /= unitDivisor;

        return new CellVolumeRate(
                Math.multiplyExact(reducedUnits, reducedEvents),
                reducedTicks);
    }

    private static long greatestCommonDivisor(long first, long second) {
        long a = first;
        long b = second;
        while (b != 0L) {
            long remainder = a % b;
            a = b;
            b = remainder;
        }
        return a == 0L ? 1L : a;
    }
}

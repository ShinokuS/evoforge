package io.github.evoforge.simulation.time;

/**
 * Effectively unbounded non-negative simulation time represented as an era plus a tick inside it.
 *
 * <p>This avoids floating-point time drift and avoids making one signed {@code long} the lifetime of
 * the world. The maximum representable age is roughly 2^123 ticks, far beyond any practical
 * simulation horizon, while comparisons and small advances remain allocation-light.</p>
 */
public record SimulationInstant(long era, long tickWithinEra) implements Comparable<SimulationInstant> {

    public static final long TICKS_PER_ERA = 1L << 60;
    public static final SimulationInstant ZERO = new SimulationInstant(0L, 0L);

    public SimulationInstant {
        if (era < 0L) {
            throw new IllegalArgumentException("era must be >= 0");
        }
        if (tickWithinEra < 0L || tickWithinEra >= TICKS_PER_ERA) {
            throw new IllegalArgumentException("tickWithinEra must be in [0, TICKS_PER_ERA)");
        }
    }

    public static SimulationInstant fromTicks(long ticks) {
        if (ticks < 0L) {
            throw new IllegalArgumentException("ticks must be >= 0");
        }
        return new SimulationInstant(ticks / TICKS_PER_ERA, ticks % TICKS_PER_ERA);
    }

    public SimulationInstant plusTicks(long deltaTicks) {
        if (deltaTicks < 0L) {
            throw new IllegalArgumentException("deltaTicks must be >= 0");
        }

        long addedEras = deltaTicks / TICKS_PER_ERA;
        long remainder = deltaTicks % TICKS_PER_ERA;
        long nextTick = tickWithinEra + remainder;
        long carry = 0L;
        if (nextTick >= TICKS_PER_ERA) {
            nextTick -= TICKS_PER_ERA;
            carry = 1L;
        }
        long nextEra = Math.addExact(era, Math.addExact(addedEras, carry));
        return new SimulationInstant(nextEra, nextTick);
    }

    /**
     * Returns the interval when it fits into a signed long. Large callers can work era-by-era instead.
     */
    public long ticksUntilExact(SimulationInstant later) {
        if (later == null) {
            throw new IllegalArgumentException("later must not be null");
        }
        if (later.compareTo(this) < 0) {
            throw new IllegalArgumentException("later must not be before this instant");
        }
        long eraDelta = Math.subtractExact(later.era, era);
        long eraTicks = Math.multiplyExact(eraDelta, TICKS_PER_ERA);
        return Math.addExact(eraTicks, later.tickWithinEra - tickWithinEra);
    }

    @Override
    public int compareTo(SimulationInstant other) {
        int eraOrder = Long.compare(era, other.era);
        return eraOrder != 0 ? eraOrder : Long.compare(tickWithinEra, other.tickWithinEra);
    }

    @Override
    public String toString() {
        return "SimulationInstant[era=" + era + ", tick=" + tickWithinEra + "]";
    }
}

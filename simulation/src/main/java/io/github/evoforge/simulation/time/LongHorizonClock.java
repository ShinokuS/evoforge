package io.github.evoforge.simulation.time;

/** Mutable clock for long-lived processes that use {@link SimulationInstant}. */
public final class LongHorizonClock {

    private SimulationInstant now;

    public LongHorizonClock() {
        this(SimulationInstant.ZERO);
    }

    public LongHorizonClock(SimulationInstant initial) {
        if (initial == null) {
            throw new IllegalArgumentException("initial must not be null");
        }
        this.now = initial;
    }

    public SimulationInstant now() {
        return now;
    }

    public void advance() {
        now = now.plusTicks(1L);
    }

    public void advanceBy(long ticks) {
        now = now.plusTicks(ticks);
    }

    public void jumpTo(SimulationInstant target) {
        if (target == null) {
            throw new IllegalArgumentException("target must not be null");
        }
        if (target.compareTo(now) < 0) {
            throw new IllegalArgumentException("time cannot move backwards");
        }
        now = target;
    }
}

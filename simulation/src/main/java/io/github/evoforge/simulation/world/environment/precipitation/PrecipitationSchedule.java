package io.github.evoforge.simulation.world.environment.precipitation;

import io.github.evoforge.simulation.world.mechanics.geometry.CellVolume;

/** Deterministic uniform precipitation cadence configured at runtime composition. */
public record PrecipitationSchedule(
        int amountPerColumn,
        long intervalTicks,
        long activeTicks,
        long cycleTicks) {

    /** Historical endless periodic source: one precipitation pulse every interval. */
    public PrecipitationSchedule(
            int amountPerColumn,
            long intervalTicks) {
        this(amountPerColumn, intervalTicks, 0L, 0L);
    }

    public PrecipitationSchedule {
        CellVolume.requireValid(amountPerColumn);
        if (amountPerColumn == CellVolume.EMPTY) {
            throw new IllegalArgumentException(
                    "precipitation amountPerColumn must be positive");
        }
        if (intervalTicks <= 0L) {
            throw new IllegalArgumentException(
                    "precipitation intervalTicks must be positive");
        }

        boolean endless = activeTicks == 0L && cycleTicks == 0L;
        if (!endless) {
            if (activeTicks <= 0L) {
                throw new IllegalArgumentException(
                        "cyclic precipitation activeTicks must be positive");
            }
            if (cycleTicks <= activeTicks) {
                throw new IllegalArgumentException(
                        "cyclic precipitation cycleTicks must be greater than activeTicks");
            }
            if (intervalTicks > activeTicks) {
                throw new IllegalArgumentException(
                        "cyclic precipitation intervalTicks must fit inside activeTicks");
            }
        }
    }

    /**
     * Creates a repeating rain window. Pulses occur every {@code intervalTicks}
     * from the start of each cycle while the active window is open; the dry part of
     * the cycle schedules no precipitation work.
     */
    public static PrecipitationSchedule cyclic(
            int amountPerColumn,
            long intervalTicks,
            long activeTicks,
            long cycleTicks) {
        return new PrecipitationSchedule(
                amountPerColumn,
                intervalTicks,
                activeTicks,
                cycleTicks);
    }

    public boolean cyclic() {
        return cycleTicks > 0L;
    }

    /** Atmospheric forcing window, independent of individual solver pulse ticks. */
    public boolean activeAt(long tick) {
        if (!cyclic()) {
            return tick > 0L
                    && Math.floorMod(tick, intervalTicks) == 0L;
        }
        long phase = Math.floorMod(tick, cycleTicks);
        return phase > 0L && phase <= activeTicks;
    }

    /** Positive delay from {@code currentTick} to the next actual precipitation pulse. */
    public long delayToNextEvent(long currentTick) {
        if (!cyclic()) {
            return intervalTicks;
        }

        long phase = Math.floorMod(currentTick, cycleTicks);
        if (phase < activeTicks) {
            long candidate = Math.addExact(phase, intervalTicks);
            if (candidate <= activeTicks) {
                return intervalTicks;
            }
        }

        return Math.addExact(
                cycleTicks - phase,
                intervalTicks);
    }
}

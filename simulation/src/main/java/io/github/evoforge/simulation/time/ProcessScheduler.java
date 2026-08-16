package io.github.evoforge.simulation.time;

@FunctionalInterface
public interface ProcessScheduler {

    void scheduleAfter(
            long delayTicks,
            long processId);

    /**
     * Schedules against an absolute simulation tick when the bound scheduler
     * exposes that capability. Relative-only test doubles may keep the default.
     */
    default void scheduleAt(
            long tick,
            long processId) {
        throw new UnsupportedOperationException(
                "absolute process scheduling is not supported");
    }
}

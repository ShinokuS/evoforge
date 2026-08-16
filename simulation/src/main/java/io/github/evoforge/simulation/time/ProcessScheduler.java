package io.github.evoforge.simulation.time;

public interface ProcessScheduler {

    void scheduleAt(
            long tick,
            long processId);

    void scheduleAfter(
            long delayTicks,
            long processId);
}

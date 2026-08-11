package io.github.evoforge.simulation.time;

public interface ProcessScheduler {

    void scheduleAfter(
            long delayTicks,
            long processId);
}

package io.github.evoforge.simulation.time;

public final class SimulationStepper {

    private final SimulationClock clock;
    private final Scheduler scheduler;

    public SimulationStepper(
            SimulationClock clock,
            Scheduler scheduler) {

        if (clock == null) {
            throw new IllegalArgumentException(
                    "clock must not be null");
        }
        if (scheduler == null) {
            throw new IllegalArgumentException(
                    "scheduler must not be null");
        }

        this.clock = clock;
        this.scheduler = scheduler;
    }

    public void advance() {
        clock.advance();
        scheduler.dispatchDue(
                clock.tick());
    }
}

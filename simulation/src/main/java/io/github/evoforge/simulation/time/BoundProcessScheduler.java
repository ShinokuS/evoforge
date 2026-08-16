package io.github.evoforge.simulation.time;

public final class BoundProcessScheduler
        implements ProcessScheduler {

    private final SimulationTime time;
    private final Scheduler scheduler;
    private final HandlerId handlerId;

    public BoundProcessScheduler(
            SimulationTime time,
            Scheduler scheduler,
            HandlerId handlerId) {

        if (time == null) {
            throw new IllegalArgumentException(
                    "time must not be null");
        }
        if (scheduler == null) {
            throw new IllegalArgumentException(
                    "scheduler must not be null");
        }
        if (handlerId == null) {
            throw new IllegalArgumentException(
                    "handlerId must not be null");
        }

        this.time = time;
        this.scheduler = scheduler;
        this.handlerId = handlerId;
    }

    @Override
    public void scheduleAt(
            long tick,
            long processId) {

        if (tick < time.tick()) {
            throw new IllegalArgumentException(
                    "tick must not be before current simulation time");
        }

        scheduler.schedule(
                tick,
                handlerId,
                processId);
    }

    @Override
    public void scheduleAfter(
            long delayTicks,
            long processId) {

        if (delayTicks < 0) {
            throw new IllegalArgumentException(
                    "delayTicks must be >= 0");
        }

        long when;

        try {
            when = Math.addExact(
                    time.tick(),
                    delayTicks);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException(
                    "scheduled time overflow",
                    exception);
        }

        scheduleAt(
                when,
                processId);
    }
}

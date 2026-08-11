package io.github.evoforge.simulation.time;

public final class SimulationClock
        implements SimulationTime {

    private long tick;

    @Override
    public long tick() {
        return tick;
    }

    public void advance() {
        if (tick == Long.MAX_VALUE) {
            throw new IllegalStateException(
                    "simulation clock overflow");
        }

        tick++;
    }
}

package io.github.evoforge.simulation.world.mechanics.geometry;

public final class TransitionComposition {

    private TransitionComposition() {
    }

    public static int resolve(
            long ports,
            int blocks) {

        TransitionMask.requireValid(blocks);

        return TransitionPorts.departures(ports)
                & TransitionPorts.arrivals(ports)
                & ~blocks
                & TransitionMask.ALL;
    }
}

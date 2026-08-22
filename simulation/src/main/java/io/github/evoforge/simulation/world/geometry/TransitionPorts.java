package io.github.evoforge.simulation.world.geometry;

public final class TransitionPorts {

    public static final long NONE = 0L;

    private static final int SHIFT = 27;
    private static final long MASK =
            (1L << SHIFT) - 1L;

    private TransitionPorts() {
    }

    public static long of(
            int departures,
            int arrivals) {

        TransitionMask.requireValid(departures);
        TransitionMask.requireValid(arrivals);

        return Integer.toUnsignedLong(departures)
                | Integer.toUnsignedLong(arrivals) << SHIFT;
    }

    public static long departuresOnly(
            int departures) {

        return of(
                departures,
                TransitionMask.NONE);
    }

    public static long arrivalsOnly(
            int arrivals) {

        return of(
                TransitionMask.NONE,
                arrivals);
    }

    public static int departures(
            long ports) {

        return (int) (ports & MASK);
    }

    public static int arrivals(
            long ports) {

        return (int) (ports >>> SHIFT & MASK);
    }
}

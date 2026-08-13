package io.github.evoforge.simulation.world.mechanics.geometry;

public interface Shape {

    long transitionPorts(
            int relativeX,
            int relativeY,
            int relativeZ);

    default int transitionBlocks(
            int relativeX,
            int relativeY,
            int relativeZ) {

        return TransitionMask.NONE;
    }

    /**
     * Guaranteed lower bound for every positive traversal factor this Shape may return.
     * Implementations with factors below NEUTRAL must override this conservatively.
     */
    default int minimumTraversalFactor() {
        return 1;
    }

    default int departureTraversalFactor(
            int relativeX,
            int relativeY,
            int relativeZ,
            int directionX,
            int directionY,
            int directionZ) {

        int direction = TransitionMask.of(
                directionX,
                directionY,
                directionZ);

        int departures = TransitionPorts.departures(
                transitionPorts(
                        relativeX,
                        relativeY,
                        relativeZ));

        return (departures & direction) != 0
                ? ShapeTraversalFactor.NEUTRAL
                : ShapeTraversalFactor.NONE;
    }

    default int arrivalTraversalFactor(
            int relativeX,
            int relativeY,
            int relativeZ,
            int directionX,
            int directionY,
            int directionZ) {

        int direction = TransitionMask.of(
                directionX,
                directionY,
                directionZ);

        int arrivals = TransitionPorts.arrivals(
                transitionPorts(
                        relativeX,
                        relativeY,
                        relativeZ));

        return (arrivals & direction) != 0
                ? ShapeTraversalFactor.NEUTRAL
                : ShapeTraversalFactor.NONE;
    }
}

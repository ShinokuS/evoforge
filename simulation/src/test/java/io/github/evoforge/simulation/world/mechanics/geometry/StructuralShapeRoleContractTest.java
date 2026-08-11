package io.github.evoforge.simulation.world.mechanics.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class StructuralShapeRoleContractTest {

    private static final Shape[] PRODUCTION_SHAPES = {
        FullShape.INSTANCE,
        RampShape.POSITIVE_X,
        RampShape.NEGATIVE_X,
        RampShape.POSITIVE_Y,
        RampShape.NEGATIVE_Y
    };

    @Test
    void productionShapesKeepTransitionRolesLocalToTheirSupportedPosition() {
        for (Shape shape : PRODUCTION_SHAPES) {
            assertRoleContract(shape);
        }
    }

    @Test
    void productionTraversalFactorsFollowTheSameRoleOwnership() {
        for (Shape shape : PRODUCTION_SHAPES) {
            assertTraversalFactorContract(shape);
        }
    }

    private static void assertRoleContract(Shape shape) {
        for (int relativeZ = -2; relativeZ <= 2; relativeZ++) {
            for (int relativeY = -2; relativeY <= 2; relativeY++) {
                for (int relativeX = -2; relativeX <= 2; relativeX++) {
                    long ports =
                            shape.transitionPorts(
                                    relativeX,
                                    relativeY,
                                    relativeZ);

                    int departures =
                            TransitionPorts.departures(ports);
                    int arrivals =
                            TransitionPorts.arrivals(ports);

                    if (departures != TransitionMask.NONE) {
                        assertEquals(
                                0,
                                relativeX,
                                shape + " exposes departures outside its supported position");
                        assertEquals(
                                0,
                                relativeY,
                                shape + " exposes departures outside its supported position");
                        assertEquals(
                                1,
                                relativeZ,
                                shape + " exposes departures outside its supported position");
                    }

                    if (arrivals != TransitionMask.NONE) {
                        int directionX = -relativeX;
                        int directionY = -relativeY;
                        int directionZ = 1 - relativeZ;

                        assertTrue(
                                Math.abs(directionX) <= 1
                                        && Math.abs(directionY) <= 1
                                        && Math.abs(directionZ) <= 1
                                        && (directionX != 0
                                                || directionY != 0
                                                || directionZ != 0),
                                shape + " exposes an arrival that does not end at its supported position");

                        assertEquals(
                                TransitionMask.of(
                                        directionX,
                                        directionY,
                                        directionZ),
                                arrivals,
                                shape + " exposes arrival bits for another position");
                    }
                }
            }
        }
    }

    private static void assertTraversalFactorContract(
            Shape shape) {

        for (int relativeZ = -2; relativeZ <= 2; relativeZ++) {
            for (int relativeY = -2; relativeY <= 2; relativeY++) {
                for (int relativeX = -2; relativeX <= 2; relativeX++) {
                    long ports = shape.transitionPorts(
                            relativeX,
                            relativeY,
                            relativeZ);
                    int departures =
                            TransitionPorts.departures(ports);
                    int arrivals =
                            TransitionPorts.arrivals(ports);

                    for (int dz = -1; dz <= 1; dz++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            for (int dx = -1; dx <= 1; dx++) {
                                if (dx == 0 && dy == 0 && dz == 0) {
                                    continue;
                                }

                                int direction = TransitionMask.of(
                                        dx,
                                        dy,
                                        dz);

                                int expectedDeparture =
                                        (departures & direction) != 0
                                                ? ShapeTraversalFactor.NEUTRAL
                                                : ShapeTraversalFactor.NONE;
                                int expectedArrival =
                                        (arrivals & direction) != 0
                                                ? ShapeTraversalFactor.NEUTRAL
                                                : ShapeTraversalFactor.NONE;

                                assertEquals(
                                        expectedDeparture,
                                        shape.departureTraversalFactor(
                                                relativeX,
                                                relativeY,
                                                relativeZ,
                                                dx,
                                                dy,
                                                dz),
                                        shape + " departure traversal factor diverges from its port role");
                                assertEquals(
                                        expectedArrival,
                                        shape.arrivalTraversalFactor(
                                                relativeX,
                                                relativeY,
                                                relativeZ,
                                                dx,
                                                dy,
                                                dz),
                                        shape + " arrival traversal factor diverges from its port role");
                            }
                        }
                    }
                }
            }
        }
    }
}

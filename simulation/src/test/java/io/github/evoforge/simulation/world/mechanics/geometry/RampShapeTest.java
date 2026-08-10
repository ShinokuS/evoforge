package io.github.evoforge.simulation.world.mechanics.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class RampShapeTest {

    @Test
    void positiveYFormsLinearBidirectionalPassage() {
        assertOrientation(
                RampShape.POSITIVE_Y,
                0,
                1);
    }

    @Test
    void allOrientationsUseTheSameRotatedTopology() {
        assertOrientation(
                RampShape.POSITIVE_X,
                1,
                0);
        assertOrientation(
                RampShape.NEGATIVE_X,
                -1,
                0);
        assertOrientation(
                RampShape.POSITIVE_Y,
                0,
                1);
        assertOrientation(
                RampShape.NEGATIVE_Y,
                0,
                -1);
    }

    @Test
    void exposesNoSideOrDiagonalEntries() {
        assertEquals(
                TransitionPorts.NONE,
                RampShape.POSITIVE_Y.transitionPorts(
                        1,
                        0,
                        1));

        assertEquals(
                TransitionPorts.NONE,
                RampShape.POSITIVE_Y.transitionPorts(
                        1,
                        -1,
                        0));

        assertEquals(
                TransitionPorts.NONE,
                RampShape.POSITIVE_Y.transitionPorts(
                        -1,
                        1,
                        1));
    }

    private static void assertOrientation(
            RampShape shape,
            int riseX,
            int riseY) {

        int lowerToRamp =
                TransitionMask.of(
                        riseX,
                        riseY,
                        1);

        assertPorts(
                shape.transitionPorts(
                        -riseX,
                        -riseY,
                        0),
                lowerToRamp);

        int rampToLower =
                TransitionMask.of(
                        -riseX,
                        -riseY,
                        -1);

        int rampToUpper =
                TransitionMask.of(
                        riseX,
                        riseY,
                        0);

        assertPorts(
                shape.transitionPorts(
                        0,
                        0,
                        1),
                rampToLower | rampToUpper);

        int upperToRamp =
                TransitionMask.of(
                        -riseX,
                        -riseY,
                        0);

        assertPorts(
                shape.transitionPorts(
                        riseX,
                        riseY,
                        1),
                upperToRamp);
    }

    private static void assertPorts(
            long ports,
            int expected) {

        assertEquals(
                expected,
                TransitionPorts.departures(ports));

        assertEquals(
                expected,
                TransitionPorts.arrivals(ports));

        assertTrue(
                Integer.bitCount(expected) > 0);
    }
}

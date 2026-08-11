package io.github.evoforge.simulation.world.mechanics.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void blocksItsSolidTerrainVolume() {
        int sideBlocks =
                RampShape.POSITIVE_Y.transitionBlocks(
                        1,
                        0,
                        0);

        assertTrue(
                TransitionMask.contains(
                        sideBlocks,
                        -1,
                        0,
                        0));

        int insideBlocks =
                RampShape.POSITIVE_Y.transitionBlocks(
                        0,
                        0,
                        0);

        assertEquals(
                8,
                Integer.bitCount(insideBlocks));

        assertFalse(
                TransitionMask.contains(
                        insideBlocks,
                        0,
                        0,
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
                lowerToRamp,
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
                rampToLower | rampToUpper,
                rampToLower);

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
                TransitionMask.NONE,
                upperToRamp);
    }

    private static void assertPorts(
            long ports,
            int expectedDepartures,
            int expectedArrivals) {

        assertEquals(
                expectedDepartures,
                TransitionPorts.departures(ports));

        assertEquals(
                expectedArrivals,
                TransitionPorts.arrivals(ports));
    }
}

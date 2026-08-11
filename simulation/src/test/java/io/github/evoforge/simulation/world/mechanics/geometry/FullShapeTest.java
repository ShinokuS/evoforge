package io.github.evoforge.simulation.world.mechanics.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class FullShapeTest {

    @Test
    void exposesHorizontalAndCardinalUpDeparturesFromTop() {
        long ports =
                FullShape.INSTANCE.transitionPorts(
                        0,
                        0,
                        1);

        int departures =
                TransitionPorts.departures(ports);

        assertEquals(
                12,
                Integer.bitCount(departures));

        assertEquals(
                TransitionMask.NONE,
                TransitionPorts.arrivals(ports));

        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }

                assertTrue(
                        TransitionMask.contains(
                                departures,
                                dx,
                                dy,
                                0));
            }
        }

        assertTrue(
                TransitionMask.contains(
                        departures,
                        -1,
                        0,
                        1));
        assertTrue(
                TransitionMask.contains(
                        departures,
                        1,
                        0,
                        1));
        assertTrue(
                TransitionMask.contains(
                        departures,
                        0,
                        -1,
                        1));
        assertTrue(
                TransitionMask.contains(
                        departures,
                        0,
                        1,
                        1));
    }

    @Test
    void exposesArrivalIntoTopFromNeighbor() {
        long ports =
                FullShape.INSTANCE.transitionPorts(
                        -1,
                        0,
                        1);

        assertEquals(
                TransitionMask.NONE,
                TransitionPorts.departures(ports));

        int arrivals =
                TransitionPorts.arrivals(ports);

        assertEquals(
                1,
                Integer.bitCount(arrivals));

        assertTrue(
                TransitionMask.contains(
                        arrivals,
                        1,
                        0,
                        0));
    }

    @Test
    void exposesDiagonalArrivalIntoTopFromNeighbor() {
        int arrivals =
                TransitionPorts.arrivals(
                        FullShape.INSTANCE.transitionPorts(
                                -1,
                                -1,
                                1));

        assertEquals(
                1,
                Integer.bitCount(arrivals));

        assertTrue(
                TransitionMask.contains(
                        arrivals,
                        1,
                        1,
                        0));
    }

    @Test
    void returnsNoPortsOutsideLocalTopNeighborhood() {
        assertEquals(
                TransitionPorts.NONE,
                FullShape.INSTANCE.transitionPorts(
                        2,
                        0,
                        1));

        assertEquals(
                TransitionPorts.NONE,
                FullShape.INSTANCE.transitionPorts(
                        0,
                        0,
                        0));

        assertEquals(
                TransitionPorts.NONE,
                FullShape.INSTANCE.transitionPorts(
                        0,
                        0,
                        2));
    }

    @Test
    void blocksTransitionsTowardItsOccupiedSide() {
        int blocks =
                FullShape.INSTANCE.transitionBlocks(
                        -1,
                        0,
                        0);

        assertEquals(
                3,
                Integer.bitCount(blocks));

        assertTrue(
                TransitionMask.contains(
                        blocks,
                        1,
                        -1,
                        0));

        assertTrue(
                TransitionMask.contains(
                        blocks,
                        1,
                        0,
                        0));

        assertTrue(
                TransitionMask.contains(
                        blocks,
                        1,
                        1,
                        0));

        assertFalse(
                TransitionMask.contains(
                        blocks,
                        0,
                        1,
                        0));
    }

    @Test
    void blocksOnlyDirectDiagonalWhenOccupyingDiagonalCell() {
        int blocks =
                FullShape.INSTANCE.transitionBlocks(
                        -1,
                        -1,
                        0);

        assertEquals(
                1,
                Integer.bitCount(blocks));

        assertTrue(
                TransitionMask.contains(
                        blocks,
                        1,
                        1,
                        0));
    }

    @Test
    void blocksDirectVerticalEntryIntoItsCell() {
        int fromBelow =
                FullShape.INSTANCE.transitionBlocks(
                        0,
                        0,
                        -1);

        assertEquals(
                1,
                Integer.bitCount(fromBelow));

        assertTrue(
                TransitionMask.contains(
                        fromBelow,
                        0,
                        0,
                        1));

        int fromAbove =
                FullShape.INSTANCE.transitionBlocks(
                        0,
                        0,
                        1);

        assertEquals(
                1,
                Integer.bitCount(fromAbove));

        assertTrue(
                TransitionMask.contains(
                        fromAbove,
                        0,
                        0,
                        -1));
    }

    @Test
    void blocksDirectDiagonalVerticalEntryIntoItsCell() {
        int blocks =
                FullShape.INSTANCE.transitionBlocks(
                        0,
                        -1,
                        -1);

        assertEquals(
                1,
                Integer.bitCount(blocks));

        assertTrue(
                TransitionMask.contains(
                        blocks,
                        0,
                        1,
                        1));
    }

    @Test
    void returnsNoBlocksOutsideLocalNeighborhood() {
        assertEquals(
                TransitionMask.NONE,
                FullShape.INSTANCE.transitionBlocks(
                        2,
                        0,
                        0));

        assertEquals(
                TransitionMask.NONE,
                FullShape.INSTANCE.transitionBlocks(
                        0,
                        0,
                        2));
    }
}

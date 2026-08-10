package io.github.evoforge.simulation.world.mechanics.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class TransitionCompositionTest {

    @Test
    void resolvesTransitionOnlyWhenDepartureAndArrivalMatch() {
        Accumulator topology =
                new Accumulator();

        topology.add(
                FullShape.INSTANCE,
                0,
                0,
                1);

        assertEquals(
                TransitionMask.NONE,
                topology.resolve());

        topology.add(
                FullShape.INSTANCE,
                -1,
                0,
                1);

        int resolved =
                topology.resolve();

        assertEquals(
                1,
                Integer.bitCount(resolved));

        assertTrue(
                TransitionMask.contains(
                        resolved,
                        1,
                        0,
                        0));
    }

    @Test
    void resolvesEightDirectionsAcrossFlatFullNeighborhood() {
        Accumulator topology =
                flatNeighborhood(
                        Integer.MIN_VALUE,
                        Integer.MIN_VALUE);

        int resolved =
                topology.resolve();

        assertEquals(
                8,
                Integer.bitCount(resolved));

        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }

                assertTrue(
                        TransitionMask.contains(
                                resolved,
                                dx,
                                dy,
                                0));
            }
        }
    }

    @Test
    void missingDestinationSupportRemovesOnlyThatTransition() {
        Accumulator topology =
                flatNeighborhood(
                        1,
                        0);

        int resolved =
                topology.resolve();

        assertEquals(
                7,
                Integer.bitCount(resolved));

        assertFalse(
                TransitionMask.contains(
                        resolved,
                        1,
                        0,
                        0));

        assertTrue(
                TransitionMask.contains(
                        resolved,
                        0,
                        1,
                        0));
    }

    @Test
    void occupiedSideBlocksDirectAndCornerCrossingTransitions() {
        Accumulator topology =
                flatNeighborhood(
                        Integer.MIN_VALUE,
                        Integer.MIN_VALUE);

        topology.add(
                FullShape.INSTANCE,
                -1,
                0,
                0);

        int resolved =
                topology.resolve();

        assertEquals(
                5,
                Integer.bitCount(resolved));

        assertFalse(
                TransitionMask.contains(
                        resolved,
                        1,
                        -1,
                        0));

        assertFalse(
                TransitionMask.contains(
                        resolved,
                        1,
                        0,
                        0));

        assertFalse(
                TransitionMask.contains(
                        resolved,
                        1,
                        1,
                        0));
    }

    @Test
    void compositionIsIndependentOfShapeOrder() {
        Accumulator first =
                new Accumulator();

        first.add(
                FullShape.INSTANCE,
                0,
                0,
                1);
        first.add(
                FullShape.INSTANCE,
                -1,
                0,
                1);
        first.add(
                FullShape.INSTANCE,
                -1,
                0,
                0);

        Accumulator second =
                new Accumulator();

        second.add(
                FullShape.INSTANCE,
                -1,
                0,
                0);
        second.add(
                FullShape.INSTANCE,
                -1,
                0,
                1);
        second.add(
                FullShape.INSTANCE,
                0,
                0,
                1);

        assertEquals(
                first.resolve(),
                second.resolve());
    }

    @Test
    void unrelatedShapeDoesNotChangeComposition() {
        Accumulator topology =
                new Accumulator();

        topology.add(
                FullShape.INSTANCE,
                0,
                0,
                1);
        topology.add(
                FullShape.INSTANCE,
                -1,
                0,
                1);

        int before =
                topology.resolve();

        topology.add(
                FullShape.INSTANCE,
                2,
                2,
                2);

        assertEquals(
                before,
                topology.resolve());
    }

    @Test
    void masksInvalidCenterBitFromRawPorts() {
        long allBits =
                (1L << 27) - 1L;

        long malformedPorts =
                allBits | allBits << 27;

        int resolved =
                TransitionComposition.resolve(
                        malformedPorts,
                        TransitionMask.NONE);

        assertEquals(
                TransitionMask.ALL,
                resolved);

        assertEquals(
                26,
                Integer.bitCount(resolved));
    }

    private static Accumulator flatNeighborhood(
            int missingX,
            int missingY) {

        Accumulator topology =
                new Accumulator();

        for (int y = -1; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                if (x == missingX && y == missingY) {
                    continue;
                }

                topology.add(
                        FullShape.INSTANCE,
                        -x,
                        -y,
                        1);
            }
        }

        return topology;
    }

    private static final class Accumulator {

        private long ports =
                TransitionPorts.NONE;
        private int blocks =
                TransitionMask.NONE;

        void add(
                Shape shape,
                int relativeX,
                int relativeY,
                int relativeZ) {

            ports |= shape.transitionPorts(
                    relativeX,
                    relativeY,
                    relativeZ);

            blocks |= shape.transitionBlocks(
                    relativeX,
                    relativeY,
                    relativeZ);
        }

        int resolve() {
            return TransitionComposition.resolve(
                    ports,
                    blocks);
        }
    }
}

package io.github.evoforge.simulation.world.mechanics.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class FullShapeTest {

    @Test
    void exposesEightHorizontalTransitionsFromTop() {
        int mask =
                FullShape.INSTANCE.transitionMask(
                        0,
                        0,
                        1);

        assertEquals(
                8,
                Integer.bitCount(mask));

        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }

                assertTrue(
                        TransitionMask.contains(
                                mask,
                                dx,
                                dy,
                                0));
            }
        }
    }

    @Test
    void doesNotExposeVerticalTransitionFromTop() {
        int mask =
                FullShape.INSTANCE.transitionMask(
                        0,
                        0,
                        1);

        assertFalse(
                TransitionMask.contains(
                        mask,
                        0,
                        0,
                        1));

        assertFalse(
                TransitionMask.contains(
                        mask,
                        0,
                        0,
                        -1));
    }

    @Test
    void exposesTransitionIntoTopFromNeighbor() {
        int mask =
                FullShape.INSTANCE.transitionMask(
                        -1,
                        0,
                        1);

        assertEquals(
                1,
                Integer.bitCount(mask));

        assertTrue(
                TransitionMask.contains(
                        mask,
                        1,
                        0,
                        0));
    }

    @Test
    void transitionMasksIntersectForAdjacentFullShapes() {
        int fromSource =
                FullShape.INSTANCE.transitionMask(
                        0,
                        0,
                        1);

        int fromDestination =
                FullShape.INSTANCE.transitionMask(
                        -1,
                        0,
                        1);

        int common =
                fromSource & fromDestination;

        assertEquals(
                1,
                Integer.bitCount(common));

        assertTrue(
                TransitionMask.contains(
                        common,
                        1,
                        0,
                        0));
    }

    @Test
    void transitionMasksIntersectForDiagonalFullShapes() {
        int fromSource =
                FullShape.INSTANCE.transitionMask(
                        0,
                        0,
                        1);

        int fromDestination =
                FullShape.INSTANCE.transitionMask(
                        -1,
                        -1,
                        1);

        int common =
                fromSource & fromDestination;

        assertEquals(
                1,
                Integer.bitCount(common));

        assertTrue(
                TransitionMask.contains(
                        common,
                        1,
                        1,
                        0));
    }

    @Test
    void returnsNoTransitionsOutsideLocalTopNeighborhood() {
        assertEquals(
                TransitionMask.NONE,
                FullShape.INSTANCE.transitionMask(
                        2,
                        0,
                        1));

        assertEquals(
                TransitionMask.NONE,
                FullShape.INSTANCE.transitionMask(
                        0,
                        0,
                        0));

        assertEquals(
                TransitionMask.NONE,
                FullShape.INSTANCE.transitionMask(
                        0,
                        0,
                        2));
    }
}

package io.github.evoforge.simulation.world.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class TransitionMaskTest {

    @Test
    void encodesEveryNeighborAsUniqueBit() {
        int mask = TransitionMask.NONE;

        for (int dz = -1; dz <= 1; dz++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }

                    mask |= TransitionMask.of(dx, dy, dz);
                }
            }
        }

        assertEquals(
                TransitionMask.ALL,
                mask);

        assertEquals(
                26,
                Integer.bitCount(mask));
    }

    @Test
    void containsEncodedTransition() {
        int mask =
                TransitionMask.of(1, -1, 0)
                        | TransitionMask.of(0, 0, 1);

        assertTrue(
                TransitionMask.contains(
                        mask,
                        1,
                        -1,
                        0));

        assertTrue(
                TransitionMask.contains(
                        mask,
                        0,
                        0,
                        1));

        assertFalse(
                TransitionMask.contains(
                        mask,
                        -1,
                        0,
                        0));
    }

    @Test
    void rejectsZeroTransition() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TransitionMask.of(
                        0,
                        0,
                        0));
    }

    @Test
    void rejectsTransitionOutsideLocalNeighborhood() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TransitionMask.of(
                        2,
                        0,
                        0));
    }

    @Test
    void rejectsMaskWithCenterBit() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TransitionMask.requireValid(
                        1 << 13));
    }

    @Test
    void acceptsCompleteNeighborMask() {
        TransitionMask.requireValid(
                TransitionMask.ALL);
    }
}

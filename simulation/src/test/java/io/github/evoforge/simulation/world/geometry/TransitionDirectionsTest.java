package io.github.evoforge.simulation.world.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class TransitionDirectionsTest {

    @Test
    void enumeratesEveryImmediateDirectionExactlyOnce() {
        int combined = TransitionMask.NONE;

        for (int index = 0; index < TransitionDirections.COUNT; index++) {
            int bit = TransitionDirections.mask(index);
            assertEquals(
                    bit,
                    TransitionMask.of(
                            TransitionDirections.dx(index),
                            TransitionDirections.dy(index),
                            TransitionDirections.dz(index)));
            assertTrue((combined & bit) == 0);
            combined |= bit;
        }

        assertEquals(TransitionMask.ALL, combined);
    }
}

package io.github.evoforge.simulation.world.mechanics.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class GridTransitionLengthTest {

    @Test
    void distinguishesCardinalAndDiagonalLengths() {
        assertEquals(
                GridTransitionLength.CARDINAL,
                GridTransitionLength.units(1, 0, 0));

        assertEquals(
                GridTransitionLength.DOUBLE_DIAGONAL,
                GridTransitionLength.units(1, -1, 0));

        assertEquals(
                GridTransitionLength.TRIPLE_DIAGONAL,
                GridTransitionLength.units(1, -1, 1));
    }

    @Test
    void rejectsNonNeighborAndCenterOffsets() {
        assertThrows(
                IllegalArgumentException.class,
                () -> GridTransitionLength.units(0, 0, 0));

        assertThrows(
                IllegalArgumentException.class,
                () -> GridTransitionLength.units(2, 0, 0));
    }
}

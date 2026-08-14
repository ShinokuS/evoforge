package io.github.evoforge.simulation.world.mechanics.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ShapeVolumeContractTest {

    private static final Shape[] PRODUCTION_SHAPES = {
        FullShape.INSTANCE,
        RampShape.POSITIVE_X,
        RampShape.NEGATIVE_X,
        RampShape.POSITIVE_Y,
        RampShape.NEGATIVE_Y
    };

    @Test
    void cellVolumeUsesBoundedDeterministicFixedPointScale() {
        assertEquals(0, CellVolume.EMPTY);
        assertEquals(1_000_000, CellVolume.FULL);

        assertEquals(
                CellVolume.EMPTY,
                CellVolume.requireValid(CellVolume.EMPTY));
        assertEquals(
                CellVolume.FULL,
                CellVolume.requireValid(CellVolume.FULL));

        assertThrows(
                IllegalArgumentException.class,
                () -> CellVolume.requireValid(CellVolume.EMPTY - 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> CellVolume.requireValid(CellVolume.FULL + 1));
    }

    @Test
    void productionShapesExposeValidSolidVolume() {
        for (Shape shape : PRODUCTION_SHAPES) {
            int volume = shape.solidVolume();

            assertEquals(
                    volume,
                    CellVolume.requireValid(volume));
            assertTrue(
                    volume > CellVolume.EMPTY,
                    shape + " must occupy positive terrain volume");
        }
    }

    @Test
    void fullShapeOccupiesWholeAnchorCell() {
        assertEquals(
                CellVolume.FULL,
                FullShape.INSTANCE.solidVolume());
    }

    @Test
    void everyCardinalRampOccupiesHalfAnchorCell() {
        int expected = CellVolume.FULL / 2;

        assertEquals(expected, RampShape.POSITIVE_X.solidVolume());
        assertEquals(expected, RampShape.NEGATIVE_X.solidVolume());
        assertEquals(expected, RampShape.POSITIVE_Y.solidVolume());
        assertEquals(expected, RampShape.NEGATIVE_Y.solidVolume());
    }
}

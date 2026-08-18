package io.github.evoforge.simulation.world.mechanics.geometry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class SurfaceBoundaryContinuityTest {

    @Test
    void sameDirectionRampsJoinLaterallyAtTheSameWorldHeight() {
        assertSameHeightSideJoin(RampShape.POSITIVE_X);
        assertSameHeightSideJoin(RampShape.NEGATIVE_X);
        assertSameHeightSideJoin(RampShape.POSITIVE_Y);
        assertSameHeightSideJoin(RampShape.NEGATIVE_Y);
    }

    @Test
    void sameDirectionRampsDoNotJoinLaterallyWhenTheirWorldHeightsDiffer() {
        assertDifferentHeightSideBreak(RampShape.POSITIVE_X);
        assertDifferentHeightSideBreak(RampShape.NEGATIVE_X);
        assertDifferentHeightSideBreak(RampShape.POSITIVE_Y);
        assertDifferentHeightSideBreak(RampShape.NEGATIVE_Y);
    }

    private static void assertSameHeightSideJoin(RampShape shape) {
        CellFace side = lateralFace(shape);
        assertTrue(SurfaceBoundaryContinuity.aligns(shape, 7, side, shape, 7));
        assertTrue(SurfaceBoundaryContinuity.aligns(shape, 7, side.opposite(), shape, 7));
    }

    private static void assertDifferentHeightSideBreak(RampShape shape) {
        CellFace side = lateralFace(shape);
        assertFalse(SurfaceBoundaryContinuity.aligns(shape, 7, side, shape, 8));
        assertFalse(SurfaceBoundaryContinuity.aligns(shape, 7, side.opposite(), shape, 8));
    }

    private static CellFace lateralFace(RampShape shape) {
        return shape.riseX() != 0 ? CellFace.POSITIVE_Y : CellFace.POSITIVE_X;
    }
}

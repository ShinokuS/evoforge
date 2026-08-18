package io.github.evoforge.simulation.world.mechanics.geometry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class SurfaceBoundaryProfileTest {

    @Test
    void parallelRampSidesShareTheSameWorldSpaceProfile() {
        assertTrue(SurfaceBoundaryContinuity.aligns(
                RampShape.POSITIVE_Y,
                0,
                CellFace.POSITIVE_X,
                RampShape.POSITIVE_Y,
                0));
        assertTrue(SurfaceBoundaryContinuity.aligns(
                RampShape.NEGATIVE_X,
                3,
                CellFace.POSITIVE_Y,
                RampShape.NEGATIVE_X,
                3));
    }

    @Test
    void slopedSideDoesNotPretendToBeFlatTerrain() {
        assertFalse(SurfaceBoundaryContinuity.aligns(
                RampShape.POSITIVE_Y,
                0,
                CellFace.POSITIVE_X,
                FullShape.INSTANCE,
                0));
    }

    @Test
    void rampEndsAlignWithTheirNaturalLowAndHighPlatforms() {
        assertTrue(SurfaceBoundaryContinuity.aligns(
                RampShape.POSITIVE_X,
                0,
                CellFace.POSITIVE_X,
                FullShape.INSTANCE,
                0));
        assertTrue(SurfaceBoundaryContinuity.aligns(
                RampShape.POSITIVE_X,
                0,
                CellFace.NEGATIVE_X,
                FullShape.INSTANCE,
                -1));
    }

    @Test
    void oppositeRampSlopesDoNotJoinLaterally() {
        assertFalse(SurfaceBoundaryContinuity.aligns(
                RampShape.POSITIVE_Y,
                0,
                CellFace.POSITIVE_X,
                RampShape.NEGATIVE_Y,
                0));
    }
}

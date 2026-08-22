package io.github.evoforge.simulation.world.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import io.github.evoforge.simulation.world.space.measurement.CellVolume;

final class ShapeSpaceContractTest {

    @Test
    void emptyCoordinateHasFullLinearFreeSpaceAndOpenPhysicalFaces() {
        assertEquals(
                CellVolume.FULL,
                CellSpace.capacity(null));
        assertEquals(
                CellVolume.FULL / 2,
                CellSpace.freeVolumeBelow(
                        null,
                        CellSpace.FULL_HEIGHT / 2));
        assertEquals(
                CellSpace.EMPTY_HEIGHT,
                CellSpace.boundaryOpeningFloor(
                        null,
                        CellFace.POSITIVE_X));
        assertEquals(
                CellSpace.EMPTY_HEIGHT,
                CellSpace.boundaryOpeningFloor(
                        null,
                        CellFace.NEGATIVE_Z));
        assertEquals(
                CellSpace.FULL_HEIGHT,
                CellSpace.boundaryOpeningFloor(
                        null,
                        CellFace.POSITIVE_Z));
    }

    @Test
    void fullShapeHasNoFreeSpaceOrPhysicalBoundaryOpening() {
        assertEquals(
                CellVolume.EMPTY,
                CellSpace.capacity(FullShape.INSTANCE));

        for (CellFace face : CellFace.values()) {
            assertEquals(
                    CellSpace.CLOSED,
                    CellSpace.boundaryOpeningFloor(
                            FullShape.INSTANCE,
                            face));
        }
    }

    @Test
    void defaultPartialShapePreservesSolidVolumeThroughCoarseProfile() {
        Shape quarterSolid = new Shape() {
            @Override
            public int solidVolume() {
                return CellVolume.FULL / 4;
            }

            @Override
            public long transitionPorts(
                    int relativeX,
                    int relativeY,
                    int relativeZ) {

                return TransitionPorts.NONE;
            }
        };

        assertEquals(
                3 * CellVolume.FULL / 4,
                CellSpace.capacity(quarterSolid));
        assertEquals(
                3 * CellVolume.FULL / 8,
                CellSpace.freeVolumeBelow(
                        quarterSolid,
                        CellSpace.FULL_HEIGHT / 2));
        assertEquals(
                CellSpace.CLOSED,
                CellSpace.boundaryOpeningFloor(
                        quarterSolid,
                        CellFace.POSITIVE_X));
    }

    @Test
    void productionFreeSpaceProfilesAreMonotonicAndMatchSolidVolume() {
        Shape[] shapes = {
            FullShape.INSTANCE,
            RampShape.POSITIVE_X,
            RampShape.NEGATIVE_X,
            RampShape.POSITIVE_Y,
            RampShape.NEGATIVE_Y
        };

        for (Shape shape : shapes) {
            int previous = CellVolume.EMPTY;
            for (int height = CellSpace.EMPTY_HEIGHT;
                    height <= CellSpace.FULL_HEIGHT;
                    height += 100_000) {

                int current = CellSpace.freeVolumeBelow(
                        shape,
                        height);
                assertTrue(
                        current >= previous,
                        shape + " free-space profile must be monotonic");
                previous = current;
            }

            assertEquals(
                    CellVolume.FULL - shape.solidVolume(),
                    CellSpace.capacity(shape));
        }
    }

    @Test
    void rampFreeWedgeUsesHeightDependentVolumeProfile() {
        int halfHeight = CellSpace.FULL_HEIGHT / 2;

        for (RampShape ramp : ramps()) {
            assertEquals(
                    CellVolume.FULL / 2,
                    CellSpace.capacity(ramp));
            assertEquals(
                    CellVolume.FULL / 8,
                    CellSpace.freeVolumeBelow(
                            ramp,
                            halfHeight));
            assertEquals(
                    halfHeight,
                    CellSpace.surfaceHeight(
                            ramp,
                            CellVolume.FULL / 8));
        }
    }

    @Test
    void rampBoundaryOpeningFollowsItsPhysicalRise() {
        for (RampShape ramp : ramps()) {
            CellFace highFace = CellFace.fromDelta(
                    ramp.riseX(),
                    ramp.riseY(),
                    0);
            CellFace lowFace = highFace.opposite();

            assertEquals(
                    CellSpace.CLOSED,
                    CellSpace.boundaryOpeningFloor(
                            ramp,
                            highFace));
            assertEquals(
                    CellSpace.EMPTY_HEIGHT,
                    CellSpace.boundaryOpeningFloor(
                            ramp,
                            lowFace));
            assertEquals(
                    CellSpace.FULL_HEIGHT,
                    CellSpace.boundaryOpeningFloor(
                            ramp,
                            CellFace.POSITIVE_Z));
            assertEquals(
                    CellSpace.CLOSED,
                    CellSpace.boundaryOpeningFloor(
                            ramp,
                            CellFace.NEGATIVE_Z));
        }
    }

    @Test
    void surfaceHeightRejectsAmountThatDoesNotFitCurrentSpace() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CellSpace.surfaceHeight(
                        RampShape.POSITIVE_X,
                        CellVolume.FULL));
    }

    private static RampShape[] ramps() {
        return new RampShape[] {
            RampShape.POSITIVE_X,
            RampShape.NEGATIVE_X,
            RampShape.POSITIVE_Y,
            RampShape.NEGATIVE_Y
        };
    }
}

package io.github.evoforge.simulation.world.landscape.water;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.world.landscape.water.storage.SparseWaterStorage;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolume;
import io.github.evoforge.simulation.world.mechanics.geometry.FullShape;
import io.github.evoforge.simulation.world.mechanics.geometry.GeometryLookup;
import io.github.evoforge.simulation.world.mechanics.geometry.RampShape;
import io.github.evoforge.simulation.world.mechanics.geometry.Shape;
import io.github.evoforge.simulation.world.mechanics.geometry.TransitionPorts;

final class WaterSystemTest {

    private static final GeometryLookup OPEN_GEOMETRY =
            (x, y, z) -> null;

    @Test
    void emptyCellStartsDry() {
        WaterSystem water = water(OPEN_GEOMETRY);

        assertEquals(
                CellVolume.EMPTY,
                water.lookup().amount(4, 5, 6));
    }

    @Test
    void addAtMostStoresFiniteVolumeAndSaturatesAtCellCapacity() {
        WaterSystem water = water(OPEN_GEOMETRY);

        assertEquals(
                700_000,
                water.addAtMost(1, 2, 3, 700_000));
        assertEquals(
                300_000,
                water.addAtMost(1, 2, 3, 700_000));
        assertEquals(
                CellVolume.FULL,
                water.lookup().amount(1, 2, 3));
        assertEquals(
                CellVolume.EMPTY,
                water.addAtMost(1, 2, 3, 1));
    }

    @Test
    void removeAtMostReturnsActualRemovedVolumeAndNeverGoesNegative() {
        WaterSystem water = water(OPEN_GEOMETRY);
        water.addAtMost(1, 2, 3, 400_000);

        assertEquals(
                150_000,
                water.removeAtMost(1, 2, 3, 150_000));
        assertEquals(
                250_000,
                water.lookup().amount(1, 2, 3));

        assertEquals(
                250_000,
                water.removeAtMost(1, 2, 3, 900_000));
        assertEquals(
                CellVolume.EMPTY,
                water.lookup().amount(1, 2, 3));
        assertEquals(
                CellVolume.EMPTY,
                water.removeAtMost(1, 2, 3, 1));
    }

    @Test
    void fullShapeLeavesNoFreeVolumeAtItsAnchorCell() {
        WaterSystem water = water(
                (x, y, z) -> FullShape.INSTANCE);

        assertEquals(
                CellVolume.EMPTY,
                water.addAtMost(0, 0, 0, CellVolume.FULL));
        assertEquals(
                CellVolume.EMPTY,
                water.lookup().amount(0, 0, 0));
    }

    @Test
    void rampLeavesHalfCellForLiquidAtItsAnchorCell() {
        WaterSystem water = water(
                (x, y, z) -> RampShape.POSITIVE_X);

        assertEquals(
                CellVolume.FULL / 2,
                water.addAtMost(0, 0, 0, CellVolume.FULL));
        assertEquals(
                CellVolume.FULL / 2,
                water.lookup().amount(0, 0, 0));
    }

    @Test
    void arbitraryShapeParticipatesThroughSolidVolumeWithoutConcreteTypeKnowledge() {
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

        WaterSystem water = water(
                (x, y, z) -> quarterSolid);

        assertEquals(
                3 * CellVolume.FULL / 4,
                water.addAtMost(7, 8, 9, CellVolume.FULL));
    }

    @Test
    void geometryChangeNeverSilentlyDeletesExistingWater() {
        Shape[] currentShape = {null};
        WaterSystem water = water(
                (x, y, z) -> currentShape[0]);

        water.addAtMost(0, 0, 0, 600_000);
        currentShape[0] = FullShape.INSTANCE;

        assertEquals(
                600_000,
                water.lookup().amount(0, 0, 0));
        assertEquals(
                CellVolume.EMPTY,
                water.addAtMost(0, 0, 0, 1));
        assertEquals(
                600_000,
                water.removeAtMost(0, 0, 0, CellVolume.FULL));
        assertEquals(
                CellVolume.EMPTY,
                water.lookup().amount(0, 0, 0));
    }

    @Test
    void negativeTransferRequestsAreProgrammingErrors() {
        WaterSystem water = water(OPEN_GEOMETRY);

        assertThrows(
                IllegalArgumentException.class,
                () -> water.addAtMost(0, 0, 0, -1));
        assertThrows(
                IllegalArgumentException.class,
                () -> water.removeAtMost(0, 0, 0, -1));
    }

    private static WaterSystem water(
            GeometryLookup geometry) {

        return new WaterSystem(
                new SparseWaterStorage(),
                geometry);
    }
}

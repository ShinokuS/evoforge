package io.github.evoforge.simulation.world.landscape.soil;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.world.landscape.liquid.LiquidTypeId;
import io.github.evoforge.simulation.world.landscape.soil.storage.SparseSoilLiquidStorage;

final class SoilLiquidSystemTest {

    private static final LiquidTypeId WATER = LiquidTypeId.of("water");
    private static final LiquidTypeId BLOOD = LiquidTypeId.of("blood");

    @Test
    void severalRetainedLiquidsShareOneMaterialOwnedPoreCapacity() {
        SoilLiquidSystem soil = soil(10_000, 10_000);

        assertEquals(6_000, soil.infiltrateAtMost(WATER, 1, 2, 3, 6_000));
        assertEquals(4_000, soil.infiltrateAtMost(BLOOD, 1, 2, 3, 6_000));

        assertEquals(6_000, soil.lookup().amountOf(WATER, 1, 2, 3));
        assertEquals(4_000, soil.lookup().amountOf(BLOOD, 1, 2, 3));
        assertEquals(10_000, soil.lookup().totalAmount(1, 2, 3));
        assertEquals(1, soil.cells().occupiedCellCount());
        assertEquals(1, soil.cells().cellCount(WATER));
        assertEquals(1, soil.cells().cellCount(BLOOD));
    }

    @Test
    void liquidMaterialInteractionCanVaryUptakeWithoutChangingStorageModel() {
        SoilLiquidInteractionLookup interactions =
                (type, x, y, z, hydrology) -> BLOOD.equals(type)
                        ? 2_000
                        : hydrology.infiltrationLimit();
        SoilLiquidSystem soil = new SoilLiquidSystem(
                new SparseSoilLiquidStorage(),
                (x, y, z) -> new SoilHydrology(20_000, 8_000),
                interactions);

        assertEquals(2_000, soil.infiltrateAtMost(BLOOD, 0, 0, 0, 10_000));
        assertEquals(8_000, soil.infiltrateAtMost(WATER, 0, 0, 0, 10_000));
        assertEquals(10_000, soil.lookup().totalAmount(0, 0, 0));
    }

    @Test
    void removingOneConstituentPreservesOtherRetainedComposition() {
        SoilLiquidSystem soil = soil(20_000, 20_000);
        soil.infiltrateAtMost(WATER, 0, 0, 0, 7_000);
        soil.infiltrateAtMost(BLOOD, 0, 0, 0, 5_000);

        assertEquals(5_000, soil.removeAtMost(BLOOD, 0, 0, 0, 20_000));

        assertEquals(7_000, soil.lookup().amountOf(WATER, 0, 0, 0));
        assertEquals(0, soil.lookup().amountOf(BLOOD, 0, 0, 0));
        assertEquals(7_000, soil.lookup().totalAmount(0, 0, 0));
        assertEquals(1, soil.cells().occupiedCellCount());
        assertEquals(0, soil.cells().cellCount(BLOOD));
    }

    @Test
    void moistureFacadeProjectsOnlyItsConfiguredConstituent() {
        SoilLiquidSystem retained = soil(20_000, 20_000);
        SoilMoistureSystem moisture = new SoilMoistureSystem(retained, WATER);

        retained.infiltrateAtMost(BLOOD, 0, 0, 0, 4_000);
        retained.infiltrateAtMost(WATER, 0, 0, 0, 3_000);

        assertEquals(3_000, moisture.lookup().amount(0, 0, 0));
        assertEquals(1, moisture.cells().wetCellCount());
        assertEquals(7_000, retained.lookup().totalAmount(0, 0, 0));
    }

    private static SoilLiquidSystem soil(int capacity, int infiltrationLimit) {
        return new SoilLiquidSystem(
                new SparseSoilLiquidStorage(),
                (x, y, z) -> new SoilHydrology(capacity, infiltrationLimit));
    }
}
